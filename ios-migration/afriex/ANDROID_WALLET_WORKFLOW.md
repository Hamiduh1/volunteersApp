# Android wallet workflow ↔ Afriex UAT

Reference map for VolunteersApp Android while Afriex Partner UAT runs.
Source workbook: [README.md](./README.md).

## Architecture (non-negotiable)

```
Android / iOS app
    → Firebase Auth + App Check
    → HTTPS callables only
Cloud Functions
    → Afriex Business API (x-api-key, sandbox or prod base URL)
    → Firestore payout_requests / wallets/{uid}/transactions
    → Webhooks (RSA-SHA256 verify) + optional poll
```

**Never** call `sandbox.api.afriex.com` or `api.afriex.com` from the mobile app.

## Product mode

| Flag | Behavior |
| --- | --- |
| `WalletProductReleasePolicy.isTransactionOnlyRelease = true` | Hub = **Transfers**. Send with external funding. Balance / add / withdraw / agent = Coming soon. |
| `false` (later) | Full custodial wallet + agent portal. |

## Payment Methods Boundaries

Payment Methods must keep these three destinations visually and functionally separate:

1. **Pay for transfers** — saved cards, verified US ACH banks, and eligible mobile-money collection methods can fund a transfer. They are never app-wallet balance or recipient-delivery routes.
2. **Receive App User transfers** — a member's provider-verified local-bank or mobile-money route receives an App User transfer. It is not a Stripe Connect account, a Send Money beneficiary, or a sender funding method.
3. **Business services payouts: Stripe Connect** - setup requires selecting a named in-app commerce service: Marketplace, Garage Sales, Dating services, Sponsored Ads, Paid Events, Organizer Earnings, or Other in-app commerce. Functions persist that service tag and permit a Stripe destination only for its matching commerce settlement. Stripe Connect is never a Send Money, Afriex, local/SWIFT bank, mobile-money, App User, top-up, or cash-out route. Purchases use Stripe Checkout and do not require the payer to complete Connect onboarding.

## End-user send loops (three lanes)

Keep **App User**, **Mobile Money**, and **Bank Account** as separate Send Money loops.

1. Security gate (6-digit PIN, 10‑min session, expire on app background).
2. Transfer hub → Send Money → choose lane (App User / Mobile Money / Bank).
3. Pick recipient:
   - App User → directory / prior app-user recipient. Block self-send, then load only that member's server-verified local-bank or mobile-money receive routes. If none exists, show setup guidance and do not quote or send. Stripe Connect is not a remittance requirement.
   - Mobile Money / Bank → beneficiary (`users/{uid}/beneficiaries`)
   - Mobile Money registration -> re-enter the local number, select an Afriex-listed provider code, then call `createBeneficiaryVerification` with name, country, provider code, and normalized E.164 phone. Functions validate the selected provider from the live institution list, then call Afriex Resolve Payment Method using the documented mobile fields: `channel=MOBILE_MONEY`, E.164 phone as `accountNumber`, and `countryCode`; provider code is not sent to that resolve request. Continue only when `canProceed` is true, show the returned provider name when available, and require sender confirmation. Android generates only an opaque beneficiary document ID, then calls `applyApprovedBeneficiaryVerification` with that ID and the approval ID; Functions creates the verified `users/{uid}/beneficiaries/{beneficiaryId}` document. Android must not write recipient documents and must not call `resolveAfriexAccount` for mobile-recipient registration. All 19 UAT payout countries remain selectable and can be route-validated. Afriex returns a provider-confirmed account name only for `NG` and `GH`; every other corridor, including `KE`, must retain the customer-confirmed name after route validation. In corridors without a returned name, the sender confirms the intended recipient name before `VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED` is saved. The second state verifies the route, not the account-holder name. Changing phone, country, provider, or the manually entered name clears the approval and confirmation.
   - Shared recipient verification → iOS and Android read the same server-owned `users/{uid}/beneficiaries/{beneficiaryId}` document. A recipient with the server-issued verification status, confirmation, route identity, and verification timestamp is immediately reusable on either platform; Android must not call `refreshSavedBeneficiaryVerification` merely because it did not create the recipient. The refresh callable is reserved for genuinely incomplete legacy records and must never downgrade or overwrite an existing server-issued proof.
   - Bank compatibility → Android recognizes iOS aliases such as `BANK`, `bankCode`, `accountNo`/`iban`, and `swiftBic` before evaluating the shared verification proof. A valid iOS Local Bank or SWIFT recipient must proceed directly to the amount and funding steps, with re-verification required only after a material routing change.
   - Bank recipient registration → after the sender confirms the Local Bank or SWIFT details, Android calls `createBankRecipientVerification` with the full recipient/bank payload and continues only when `canProceed` is true. It then calls `applyApprovedBankRecipientVerification` with the opaque beneficiary ID and approval ID. Functions, not Firestore clients, create the verified `users/{uid}/beneficiaries/{beneficiaryId}` document. iOS reads that same document for the same Firebase UID. `users/{uid}/payment_methods` remains sender funding only and must never be used as a payout recipient source.
   - Bank FX → do not treat the optional conversion preview as a transfer decision. After the sender selects an eligible card or verified ACH funding source, `getWalletTransferQuote` supplies the locked rate, recipient amount, and one customer-visible fee. For SWIFT the target currency is USD; Local Bank uses the provider route's currency.
   - Phone normalization → the Mobile Money form accepts either a local number or an E.164 number. It stores and sends one normalized E.164 value, never a duplicated country code such as `+256256...`.
    - Local bank registration: Local Bank is limited to the confirmed production corridors (NG, GH, KE, ZA, and EG); Uganda and the United States must not appear in this chooser. A sender can search the live provider catalog or enter its current provider institution code directly, then resolve it before entering and verifying the account number. Afriex documents BANK_ACCOUNT account-name resolution for Nigeria and Ghana: the sender must confirm the returned account-holder name, account number, and bank before VERIFIED_AFRIEX_BANK_NAME_CONFIRMED can be saved. In any local-bank corridor without account-name support, Android labels the provider-confirmed bank route and requires sender confirmation; it must never describe the account-holder name as provider verified. Changing routing, institution, account, country, phone, or recipient name clears the account confirmation.
   - SWIFT registration → SWIFT is independently selectable and only lists SWIFT-only corridors, rather than being disabled because the current Local Bank country has a local rail. The sender may search the optional live catalog or enter an 8- or 11-character SWIFT/BIC directly. Android resolves the BIC through `GET /payment-method/institution/codes` server-side and fills the confirmed bank name before collecting the account/IBAN and delivery fields. The sender then confirms the recipient, account, and institution before `VERIFIED_AFRIEX_SWIFT_INSTITUTION_CONFIRMED` can be saved. Recipient and official-bank address fields use the App Check-protected `searchRecipientAddress` helper when Google Places is configured server-side, while full manual entry remains valid. This verifies the institution and user-entered delivery details; it does not represent provider account-holder name verification.
4. Pick **EXTERNAL_** funding method (card / verified US ACH bank / eligible mobile money) — no `WALLET` balance spend. Mobile-money funding is shown only for an App User's verified mobile-money receive route and only when the sender's collection corridor is live.
5. Pricing: `getAfriexRates` provides the clearly labelled preview; `getWalletTransferQuote` locks the customer rate, recipient amount, and one total customer fee after a funding method is selected. Provider, owner, and funding-fee components remain in protected server/admin records only.
   - Afriex production limits: Mobile Money and Local Bank payouts are capped at USD-equivalent $2,000 per transfer and $5,000 per Afriex business account per UTC day. Functions enforce both limits before collection; Android displays them but never acts as the authority.
   - Production scope: enable only the Mobile Money payout and collection country lists agreed with Afriex. Use `AFRIEX_MOBILE_MONEY_PAYOUTS_PRODUCTION_ENABLED` with `AFRIEX_MOBILE_MONEY_PAYOUT_PRODUCTION_COUNTRIES`, and `AFRIEX_MOBILE_MONEY_COLLECTIONS_PRODUCTION_ENABLED` with `AFRIEX_MOBILE_MONEY_COLLECTION_PRODUCTION_COUNTRIES`. Local-bank production still requires `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED` plus `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES`.
    - Fees: Schedule 1 fixes Local Bank defaults at NG $0.20, GH $1.40, KE $0.25, ZA $1.20, and EG $1.60, plus fixed Mobile Money provider fees for 18 supplied corridors, including Ghana and Kenya. The server uses those locked Schedule 1 rates and rejects a production Mobile Money quote when neither a Schedule 1 rate nor an explicit provider-fee override exists. Ethiopia is provider-supported but has no supplied dollar commercial, so `ET_MOBILE_MONEY` must have that override before it can be quoted. Mobile Money collection fees remain separately agreed and are never inferred from payout pricing. USD SWIFT is 0.25% of the transfer amount, with a real-time FX quote and an expected 3-5 working-day settlement window; intermediary banks can affect the received amount.
   - Mobile-money collection: the provider receives the sender's local mobile-money amount and currency as the deposit source, with the locked USD settlement amount as destination. A completed collection queues the separately quoted recipient payout; it never credits an app-held customer balance.
   - Business balance: Afriex business-balance funding is an operator-to-provider settlement process using the provider-designated USD, GBP, or EUR bank account, or an approved third-party-capable card processor. It is never a customer wallet top-up and must not create an app-held customer balance. End-customer onboarding uses the standard required profile information; do not impose extra KYC unless Afriex or the applicable corridor requires it.
   - Both are Firebase callables implemented in `my-firebase-functions/src/index.ts`; they call Afriex `GET /api/v1/org/rates` server-side. Deploy Functions after pull.
6. Review → `createBeneficiaryVerification` re-resolves Mobile Money with Afriex and rejects a changed phone, provider code, or account-holder name before funding/payout.
7. `sendToAppUser` (App User lane) or `initiateTransfer` (beneficiary lane). The App User callable rechecks self-send protection, the quote, funding eligibility, and the selected receive route, then submits the provider payout server-side.
8. Receipt sheet: `getWalletTransferReceipt` / local mapping — share, **PDF**, **print**.
9. Optional: `pollAfriexTransactionStatus` while pending (UAT WH-08).
10. Optional: `sendWalletTransferReceipt` (email/SMS).
11. History / hub Recent Activity: open same receipt actions; **Send Again** reopens the original lane.
12. App-user transfers keep **app-user receipt** labeling even if payout uses MM/bank rails.

## UAT case → Android / Functions ownership

| UAT ID | Owner | App surface |
| --- | --- | --- |
| SET-01…03 customers | Functions | Hidden; `user_private.afriexCustomerId` |
| SET-04…07 institutions / resolve | Functions | Networks/countries via metadata + callables |
| SET-08 rates | Functions | Quote / conversion preview on Send Money |
| SET-09…10 business balance / topup | Functions / ops only | **Not** shown on txn-only hub |
| PAY-MM-* | Functions via `initiateTransfer` | Send Money → Mobile Money |
| PAY-BK-* | Functions creates Afriex `BANK_ACCOUNT` `WITHDRAW` payment methods and transactions when the explicit environment gate is enabled | Bank Account loop uses approved local-bank corridors, a verified card or US ACH bank funding source, provider receipt states, and no app-wallet balance |
| PAY-SW-* | Functions creates Afriex `SWIFT` `WITHDRAW` payment methods and transactions when the explicit environment gate is enabled | Bank Account loop validates SWIFT beneficiary details and the [100-country catalog](https://docs.afriex.com/guides/supported-currencies); requires a verified card or US ACH bank funding source |
| PAY-UPI-* | Later | India UPI — documented, not consumer-shipped |
| WH-01…02 | Functions webhook | Receipt status → Delivered / Failed |
| WH-08 | Functions + `pollAfriexTransactionStatus` | Receipt Refresh |

## Android code anchors

| Area | Path |
| --- | --- |
| Release policy | `app/.../wallet/WalletProductReleasePolicy.kt` |
| Callables | `app/.../firebase/FirebaseContract.kt` (`CallableFunction`) |
| Transfer payloads / quote | `app/.../wallet/TransferPayload.kt` |
| Send VM | `app/.../wallet/TransactViewModel.kt` |
| Send UI | `app/.../wallet/TransactScreen.kt` |
| Hub + Recent Activity | `app/.../wallet/WalletScreen.kt` |
| History + Send Again | `app/.../wallet/TransactionHistoryScreen.kt` |
| Receipt PDF/share/print | `app/.../wallet/WalletReceiptDocuments.kt` |
| Send Again bridge | `app/.../wallet/WalletNav.kt` |
| Security | `app/.../wallet/WalletSecurityService.kt` |
| Country / MM networks / bank + SWIFT allowlists | `app/.../wallet/CountryMetadata.kt` · [`afriex_supported_currencies.json`](./afriex_supported_currencies.json) |

## Sandbox tips (from Afriex workbook)

- Auto-settle ~1–2 minutes.
- Force fail: put `fail` in `meta.reference` (server-side when creating Afriex txn).
- Production-only channels (Virtual Account, Pool Account, Crypto) need coordinated prod validation — not part of txn-only consumer path.
