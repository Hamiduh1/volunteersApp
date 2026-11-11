# Send Money audit: server-side follow-ups

An end-to-end audit of iOS Send Money (mobile money, bank/SWIFT, App User,
card/ACH/mobile-money funding) against `my-firebase-functions/src/index.ts`.
iOS-side fixes are already in the iOS repo. The items below need a backend
change and a functions deploy. Line numbers refer to `otp-mobilemoney-clean`
at `3962fec`.

## Fixed on iOS (no server change needed, listed for context)

- Saved-recipient sends no longer send a top-level `recipientId` (see item 1).
- Receipts derive Delivered / Declined from `status` when `progressState` is missing (item 2).
- `type: "SWIFT_BANK"` recipients are quoted and sent on the SWIFT rail (item 3).
- Country names such as "Cote d'Ivoire", "Republic of the Congo", "Czech Republic" resolve to ISO codes; the SWIFT list now uses "Hong Kong SAR" and "United Arab Emirates".
- The Home screen recipient "Edit" now opens the verified Send Money editor (the old editors could never save).
- Payment Methods "Verify Now" for mobile money now runs the OTP step first.
- The verify step shows the provider-returned account name (top-level `recipientName` when `accountNameVerified`, `providerResolvedName` for banks).

## Blockers (items 1–3 are fixed in this commit; they need a functions deploy)

Deploy: `firebase deploy --only functions:initiateTransfer,functions:sendToAppUser,functions:getWalletTransferReceipt,functions:sendWalletTransferReceipt,functions:pollAfriexTransactionStatus,functions:createBankRecipientVerification,functions:applyApprovedBankRecipientVerification,functions:saveVerifiedBeneficiary`

1. **External funding branches reject any request with a top-level `recipientId`.**
   `if (requestData.recipientId) throw ...` at 21229 (mobile money funding), 21466 (card), 21930 (bank).
   Beneficiary sends carry the saved id in `recipientBeneficiary.id`; a top-level id is also accepted earlier (20289, 20310) and never cleared.
   Older iOS builds (since Oct 24) and any Android path that sets `recipientId` for a saved recipient fail at Send with "Card funding is not supported for app user transfers."
   Done: the three guards now read `if (requestData.recipientId && !requestData.recipientBeneficiary)` (same pattern as 20328).

2. **`getWalletTransferReceipt` (buildTransferReceiptPayload, ~30796) returns no `progressState`, `lane`, `fundingMethod` or `deliveryMethod`.**
   Clients that read `progressState` show every receipt as pending forever and keep polling.
   Done: the receipt now includes `progressState` (`DELIVERED` / `FAILED` / `IN_PROGRESS` / `PENDING`, mapped from `statusLabel`), `lane` (`APP_USER` / `BANK_ACCOUNT` / `MOBILE_MONEY`), and readable `fundingMethod` / `deliveryMethod`. Android can read these too.

3. **SWIFT recipients are saved without `providerChannel`.**
   `verifyBeneficiaryForServerSave` SWIFT branch (~14266) stores `type: "SWIFT_BANK"` only. The quote upgrades to SWIFT only when the request says so (`providerChannel`), so a client that relies on the saved channel quotes it as BANK and `loadVerifiedBankSwiftBeneficiary(..., "BANK")` rejects it. Re-adding hits the identity dedupe in `applyApprovedBankRecipientVerification` and returns the same document.
   Done: both bank branches store `providerChannel` / `afriexProviderChannel` (`"SWIFT"` or `"BANK_ACCOUNT"`), and re-adding an existing recipient backfills the field. Still open: derive the quote route from the stored `type` instead of trusting the request.

## High

4. **External funding reconciliation lock is never released.**
   `markExternalFundingReconciliationRequired` sets `external_funding_reconciliation_locks/{uid}.active = true`; `assertNoExternalFundingReconciliationLock` blocks every send for that user; nothing sets it back to false. One failed card-funded delivery locks the account permanently.
   Fix: release on refund / confirmed settlement (admin action or webhook), and scope the lock to the specific payout.

5. **Card / ACH funded delivery failures are not refunded.**
   `applyMobileMoneyProviderResult` moves them to `FUNDING_RECONCILIATION_REQUIRED`; the only automatic refund helper covers mobile-money funding. Receipt copy promises "released or refunded".
   Fix: `stripe.refunds.create` with the stored PaymentIntent / charge id, set `REFUNDED`, release locks.

6. **Post-charge errors drop the payment reference.**
   Plain `HttpsError("unavailable", "Funding was confirmed, but the transfer needs reconciliation. Do not retry.")` around 19511, 19528, 19658, 19676 has no `details`. Use `fundingReconciliationError(..., payoutRef.id)` so clients can show the reference.

7. **App User bank receive routes are never offered to senders.**
   iOS calls the live `listRecipientPayoutMethods` (legacy). Its bank summary has no destination id, and it does not filter on `appUserReceiveRouteVerified`, so senders either never see bank routes or can pick routes the quote then rejects.
   Fix: deploy `getRecipientPayoutMethods` (27924) as the source of App User receive routes; iOS will switch to it once it is live.

8. **Nigeria / Ghana local bank: name mismatch throws without the provider name** (~14082).
   The user can only retype names blindly. Return `{canProceed: false, reasonCode: "NAME_MISMATCH", providerResolvedName}` instead of throwing; iOS already shows a "Use provider name" button when that field is present.

9. **Nigeria / Ghana mobile money: a pending name lookup is approved at registration** (resolveAfriexMobileMoneyAccount ~13121), then `hasServerIssuedBeneficiaryProof` rejects the saved record (~13708). For `RECIPIENT_REGISTRATION` in name-enquiry countries, reject with "Name check is temporarily unavailable. Try again shortly." instead of saving.

10. **Bank/SWIFT payout reads the sender phone only from the root user doc, after the charge.** Read through `withUserPrivateProfile` (includes `phoneVerifiedNumber`) and validate before charging.

## Medium

- `resolveTransferCountry` ignores `countryCode` from the request; fall back to it, and add the aliases "Hong Kong", "UAE", "Türkiye".
- Saving a bank/SWIFT recipient runs only `assertTransferDestinationCorridor`; the production scope / `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED` checks run first at quote time, so a recipient can be saved and then never quoted.
- `sendWalletTransferReceipt` only writes `wallet_receipt_deliveries`; nothing consumes it, and it reads `channel`, not `channels[]`.
- Cards are charged `off_session`; cards that require 3-D Secure always fail. Return the PaymentIntent `client_secret` on `requires_action`.
- A mobile-money-funded App User send has no sender transaction until the collection settles, and the "already in progress" error has no payout id. Write a pending transaction with the collection id and include `{payoutRequestId}` in error `details`.

## Deploy

`1d6444c` (Financial Connections ACH fields) also needs deploying; without it, Financial Connections-linked US banks never pass the ACH funding checks on either app.
