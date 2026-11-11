# Add Recipient audit (all routes, before App Review)

This audit covers mobile money, local bank (including US routing numbers) and SWIFT recipients, plus edits. No backend code was changed.

## What is already solid

- **Bank and SWIFT recipients come only from a server approval.** `createBankRecipientVerification` writes an approval that belongs to one sender and expires; `applyApprovedBankRecipientVerification` then creates the recipient from it. The client never writes a verified recipient.
- **Mobile money works the same way** (`createBeneficiaryVerification` → `applyApprovedBeneficiaryVerification`). For NG/GH it uses the provider's name, so there is no name loop.
- **Firestore rules only let clients write `verificationStatus: "UNVERIFIED"`.** Editing any delivery field (account number, phone, institution, channel, country, name, address) forces UNVERIFIED.
  - `recipientEmail`, `currency`, `isAppUser` and `transactionInvoice` can be edited without re-verifying. The send path doesn't use `currency` or `isAppUser` from the saved record. `recipientEmail` only changes the SWIFT contact email.
- **US routing numbers** are checked through the Afriex `routing_number` lookup, and the typed code is the code stored, so there is no mismatch loop.

## Fixed on iOS

- **NG/GH local-bank name loop.**
  - `verifyBeneficiaryForServerSave` (BANK_ACCOUNT) requires the typed name to exactly match the Afriex name, ignoring case. On a mismatch it throws "The confirmed recipient details no longer match Afriex" without returning the bank's name, so the sender can never fix it.
  - iOS now calls `resolveAfriexAccount` first for NG/GH, adopts the returned holder name, then verifies. The sender still confirms the provider-confirmed account before saving.
  - iOS also reads the top-level `resolveAfriexAccount` response; it previously only read a `resolved` sub-object.

## Server items

1. **NG/GH bank name mismatch (Android has the same loop).** When `accountNameVerified`, don't require the typed name to match. Store the provider name and return it, as mobile money does with `allowProviderNameOverride`, and let the client confirm it.

2. **Recipients can be saved on rails that production then refuses to quote.**
   - Saving runs only `assertTransferDestinationCorridor`. Quoting also runs `assertAfriexBankSwiftProductionScope`. Check the live `.env`:
   - **US local bank:**
     - The BANK branch rejects any country outside `AFRIEX_LOCAL_BANK_INSTITUTION_COUNTRIES` (`.env.example`: `NG,GH,KE,ZA,EG`, which has no `US`). US routes use the routing-number lookup, not the institution list, so every US quote fails with "This country is not enabled for configured Afriex local-bank institution resolution."
     - Fix: skip that check for `US`, or add `US`. Also confirm `US` is in `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES`.
   - **Local bank in production** needs `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED` or `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES`. Neither is in `.env.example`.
   - **SWIFT in production** needs `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true` (and optionally `AFRIEX_SWIFT_PAYOUT_PRODUCTION_COUNTRIES`). This isn't in `.env.example`, and without it every SWIFT recipient saves but never sends.
   - Bank/SWIFT execution also needs `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED`.
   - **Fix:** call `assertAfriexBankSwiftProductionScope(config, route, iso2)` in `createBankRecipientVerification` (after `verifyBeneficiaryForServerSave`), so an unsendable recipient is rejected at save time with a clear message.

3. **The new `getMobileMoneySupportedCountries` dropped the bank/SWIFT lists.**
   - The legacy version (`legacyDeployedFunctions` line 1177) returned `bankPayoutSupportedCountries`, `swiftPayoutSupportedCountries` and `bankInstitutionListCountries`. iOS uses these to build the Local Bank and SWIFT pickers.
   - The `index.ts` version that deploys next returns only mobile-money lists. iOS then falls back to cached or bundled lists (about 100 SWIFT countries).
   - **Fix:** return those three fields again, filtered by the production scope from item 2, so the apps only offer countries that can actually be sent to.

4. **No rate limit on name lookups.**
   - `resolveAfriexAccount` and `createBankRecipientVerification` return NG/GH account-holder names to any signed-in user with App Check, without a per-user limit.
   - **Fix:** add a per-sender limit (for example 20 lookups per hour) to prevent account-name enumeration.
