# Android Afriex Live Mode Parity

Canonical partner UAT workbook lives in [`afriex/`](./afriex/README.md).

This note tracks Android ↔ iOS ↔ Functions parity for **live / sandbox Afriex Business API** usage under the temporary **transaction-only** wallet release.

## Rules

1. **Callable-only from apps** — no Afriex REST from Android or iOS.
2. **Transaction-only hub** — `WalletProductReleasePolicy.isTransactionOnlyRelease = true` → label **Transfers**; hide balance custody UI.
3. **Funding** — `EXTERNAL_CARD` | `EXTERNAL_BANK` | `EXTERNAL_MOBILE_MONEY` only for consumer sends.
4. **Webhooks** — verified on server (`x-webhook-signature`, RSA-SHA256); clients poll or listen to Firestore/receipt helpers.
5. **Docs reference** — https://docs.afriex.com · API v1 (1.0.7)
6. **Environments**
   - Sandbox: `https://sandbox.api.afriex.com`
   - Production: `https://api.afriex.com`

## Customer identity verification

- `ensureAfriexCustomer` creates or reuses the signed-in member's Afriex customer from the member's own name, email, E.164 phone, and ISO country. The client cannot submit another member's profile or a provider customer id.
- `verifyAfriexCustomer` is opt-in and currently supports only Afriex `BVN` verification for a Nigerian (`NG`) customer. It is not part of mobile-money route verification, recipient registration, or ordinary transfer submission.
- BVNs are sent only from the callable to `POST /api/v1/customer/{customerId}/verify`; they are never written to Firestore, logs, analytics, receipts, or Android state. The server stores only the result state, timestamps, `customerId`, provider `reference`, ISO country, and provider timestamps.
- The callable applies a per-member one-minute retry guard and maps Afriex `429` to a customer-safe rate-limit response. Afriex `503`/5xx responses are treated as temporary failures. Do not automatically retry or loop this operation.

## MCP and REST boundary

- Afriex MCP (`https://mcp.afriex.com/mcp`) is an AI-client integration only. It uses `x-afriex-api-key` and optional `x-afriex-environment`; neither is ever shipped in Android or used by Firebase Functions.
- Firebase Functions call the Business REST API only: production `https://api.afriex.com/api/v1`, sandbox `https://sandbox.api.afriex.com/api/v1`, with the server-only Business key in `x-api-key` and no key prefix. The configuration rejects an MCP host, MCP-only header, or prefixed key.
- Apps continue to use callable Functions only. They must not call MCP tools, list provider customers, create/delete customers, update KYC, or access provider balances directly.
- Hosted checkout, sandbox balance top-up, and test webhook actions remain owner-only sandbox UAT helpers. They are blocked in production and never create an in-app balance.

## API reference controls

- Functions may omit `x-api-version`, which Afriex resolves to its current supported version. If an override is configured, Functions accept only `2026-05-18` and fail before sending a request for any other value.
- Business API keys are permission-scoped in the Afriex dashboard. A `401` can indicate a missing endpoint permission, a malformed key, or a revoked key; customer-facing errors stay generic while Functions logs retain diagnostics for operators.
- `x-api-signature` is an optional request-signing feature that must be enabled and specified by Afriex for the business. Do not fabricate a signature or send an app-held signing secret. Enable it only after the signing algorithm and key-management contract are confirmed with Afriex.
- Production webhooks retain cryptographic signature/shared-secret verification. If an infrastructure firewall is configured, allowlist Afriex production `34.197.33.100` and sandbox `34.234.189.210` in addition to, never instead of, signature verification.

## Mobile-money safety status

- The server atomically consumes the quote and acquires one sender lock before direct mobile-money collection.
- `FUNDING_RECONCILIATION_REQUIRED` means external funding or collection may have completed while delivery needs support review. Treat it as terminal for automatic polling and do not expose Send Again.
- Functions reject mobile-money provider URLs and obvious test/live key mismatches that conflict with the configured Afriex environment.

## Send Money funding boundary

- `initiateTransfer` accepts only `EXTERNAL_CARD`, `EXTERNAL_BANK`, and `EXTERNAL_MOBILE_MONEY`. It requires a saved `fundingPaymentMethodId`, reloads that method from the signed-in member's Firestore subcollection, and checks the current eligibility again before an external charge or collection is created. It never accepts `WALLET`, `BALANCE`, `STORED_BALANCE`, `HOSTED_WALLET`, or `PROVIDER_HOSTED_WALLET`.
- A card must contain a current Stripe charge payment-method id and must not have `requiresRelinkForCharges = true`. Cards are funding only for Send Money; a card is never an Afriex recipient route.
- A mobile-money funding method must have `phoneOwnershipVerified = true`, `verificationStatus = VERIFIED`, and a live Afriex deposit/collection corridor. It can currently fund mobile-money delivery only. This is a deliberate server constraint until the collection-complete webhook can safely create and reconcile local-bank or SWIFT delivery records.
- The current `EXTERNAL_BANK` implementation is verified **US ACH via Stripe**. It requires the Stripe source/customer ids, a `verified` source status, US country, and no relink flag. Afriex local-bank and SWIFT payment methods are recipient payout routes; they are not exposed as funding sources.
- Do not label an Afriex local-bank payout route as a Send Money funding source until the provider `BANK_ACCOUNT` `DEPOSIT` payment-method and collection transaction flow is implemented end to end: provider collection creation, terminal webhook verification, idempotent payout creation, refund/reconciliation handling, production permission confirmation, and UAT for each enabled collection corridor. Treating a payout-only route as funded before that work would risk delivery before collection settles.

## Send Money FX and settlement contract

- **USD is the Send Money source amount and settlement ledger.** Android requests `getWalletTransferQuote` with `sourceCurrency=USD`; Functions reject a non-USD source quote instead of relabeling a SEK, GBP, or other entered amount as USD. Every lane shows the locked USD total, one customer-visible fee, and the exact beneficiary delivery amount/currency before confirmation.
- **Recipient currency follows the selected Afriex rail, not a guessed domestic currency.** For example, Afriex documents Sweden's local-bank rail in EUR, the United Kingdom's local-bank rail in GBP, and its international SWIFT rail in USD. The provider rate locks the destination amount in the quote. Do not substitute SEK for Sweden or another domestic currency unless Afriex adds that rail/currency. Reference: [Afriex Supported Currencies & Payment Rails](https://docs.afriex.com/api-reference/supported-currencies).
- **Mobile-money funding locks both sides.** A quote for `EXTERNAL_MOBILE_MONEY` includes `fundingCollectionAmount` and `fundingCollectionCurrency`, calculated from the quoted USD total (send amount plus customer fee). Android shows this as **Approve on your phone**. Submission reloads the funding method and requires the quote method, local currency, total, and collection amount to still match; it never recalculates a new local debit after customer review.
- **Card and ACH funding are not the Afriex prefund mechanism.** Current Stripe card and US ACH Send Money charges use USD. A card issuer may independently convert that USD amount to the cardholder's SEK, GBP, or another local currency and may add its own fee; that issuer amount is not an app-guaranteed FX quote. Stripe processing/settlement credits the Stripe balance and its configured Stripe payout destination, not the Afriex Business USD balance. Operations must keep the Afriex USD business balance independently prefunded and reconcile Stripe receipts, Afriex collections, payouts, refunds, and webhooks. This is consistent with Stripe's [currency model](https://docs.stripe.com/currencies) and Afriex's provider-rate quote.
- **To charge an exact non-USD card amount in the future**, implement Stripe multi-currency presentment as a separate product/compliance change: create the PaymentIntent in that local currency, display its independently locked card total, and reconcile Stripe's settlement conversion separately from the Afriex USD payout quote. Never achieve that by merely changing an Android currency label.

### Android Local Spend Reference

- Android may offer a first **Start in your local currency** field for the sender's profile country. It supports ISO country values as well as display names, so Sweden/`SE` resolves to `SEK` and Saudi Arabia/`SA` resolves to `SAR`.
- The field calls `getSecureExchangeRate` for a non-binding `referenceRate` from local currency to USD, debounced by 350 ms. Android automatically fills the separate USD **transfer amount** from that reference. A manual edit to USD clears the local reference and cancels any late response.
- `referenceRate` is returned separately from the legacy application rate. It is display-only and is never persisted as a payout instruction, included in `initiateTransfer`, or allowed to replace the protected `getWalletTransferQuote` quote id.
- The local-reference state is scoped to the active Send Money lane and cleared on recipient/lane change. Stale responses are ignored. The final recipient amount, fee, USD debit, mobile-money approval amount, quote expiry, and funding method are still server-validated at submit time.
- Copy must say **estimated** for card funding because the card issuer determines the statement FX and may add a fee. For US ACH, the debit remains USD. For mobile-money funding, Android must direct the sender to the exact local approval amount in the protected live quote instead of representing the profile-currency reference as a collection total.

## Cross-Platform Saved Recipients

- Android and iOS read the same server-owned collection: `users/{uid}/beneficiaries/{beneficiaryId}`.
- Local-bank and SWIFT destinations use `saveVerifiedBeneficiary`. Mobile-money registration uses `createBeneficiaryVerification`, followed by `applyApprovedBeneficiaryVerification`; the server creates the recipient document. A complete server-owned recipient marked verified by iOS or Android is reusable on both platforms without another provider lookup. New records include the immutable `recipientIdentityKey`; older verified iOS records without that later field remain valid through the server-owned legacy-proof migration. Only records with missing routing details or no server verification use `refreshSavedBeneficiaryVerification`.
- The canonical shared fields are `type`, `name`, `country`, `network`, `mobileNumber`, `accountNumber`, `institutionCode`, `bankName`, `swiftCode`, `routingCode`, `recipientEmail`, `recipientAddress`, `bankAddress`, `invoiceReference`, and the verification audit fields.
- Shared readers also accept legacy iOS aliases such as `providerVerifiedAt`, `recipientConfirmedAt`, `recipientConfirmationSource`, `providerInstitutionCode`, `recipientPhoneE164`, `beneficiaryVerificationStatus`, and `isProviderVerified`. A complete proof using those names is reusable and must not open Android's refresh dialog.
- Beneficiaries are server-write-only in Firestore. A legacy iOS record with a recognised verified provider status and complete provider route is therefore reusable even when it predates `isVerified`, an identity key, or newer audit fields. Android must not call `refreshSavedBeneficiaryVerification` for it; only new or materially edited routes require provider verification.
- Mobile-money numbers are stored as E.164. iOS converts a local entry using the selected country; Android constructs E.164 before calling Functions. Android loads recipient-registration countries through `getMobileMoneySupportedCountries` with `purpose=RECIPIENT_REGISTRATION`, then loads the provider picker only through `getAfriexInstitutions`; it must never fall back to a bundled provider list or accept a typed institution code. Registration countries require both an approved payout corridor and documented provider resolution, so Android never offers a route it cannot safely verify.
- A successful mobile-money route check without a returned name is still a verified route outside name-enquiry corridors. The sender must confirm the entered recipient name before saving; this is not a provider failure.
- `AFRIEX_MODE` and `AFRIEX_ENV` must resolve to the same environment. Production verification uses the production Business API host and an API key enabled for the Resolve Payment Method capability; Functions fail safely when those settings disagree instead of attempting a cross-environment request.
- A mobile-money lane must never submit a `BANK_ACCOUNT` or `SWIFT_BANK` record. The recipient can be refreshed from either platform, but delivery must use its matching rail and its server-issued proof.

## Bank Recipient Contract

- Keep **Local Bank** and **SWIFT Bank** as separate Bank Account routes. Android loads institutions from `getAfriexInstitutions` and saves only the returned institution or routing/BIC code.
- The current Local Bank corridor set is Nigeria, Ghana, Kenya, South Africa, Egypt, and USA (`BANK_ACCOUNT`). A saved recipient from a different country is not silently converted or re-verified as Local Bank; it must use a complete, verified `SWIFT_BANK` route when SWIFT is the supported delivery rail.
- A saved iOS or Android bank recipient with a matching server `recipientIdentityKey` is already verified. It must not open the refresh dialog merely because it predates a newer Android audit field. Reverification is required only after a material routing change.
- USA Local Bank requires an 8- or 9-digit ABA routing number in addition to the provider-listed bank. The local-bank route is then resolved through Functions before it can be saved. SWIFT requires a provider-listed 8/11-character BIC, recipient contact/address data, and bank mailing address. Android never accepts a typed institution code or an invoice URL.

### Required Fields And Gates

| Route | Required before save | Verification and persistence |
| --- | --- | --- |
| Local Bank | Country, provider-listed bank and code, recipient name (provider-returned where available), recipient phone, account number, confirmed account number | Functions resolves the account and selected institution. The normalized account entries must match; if a corridor returns no account-holder name, the sender supplies and confirms it before save. |
| USA Local Bank | All Local Bank fields plus an 8- or 9-digit ABA routing number; recipient address is optional | Uses `BANK_ACCOUNT`, checks the ABA format before provider route verification, persists the routing number and optional recipient address, and never falls back to SWIFT. |
| SWIFT Bank | Provider-supported SWIFT-only country, beneficiary name, recipient phone, provider-listed bank with an 8/11-character BIC, account/IBAN, confirmed account/IBAN, recipient email and mailing address, bank mailing address | Functions verifies the BIC/institution and records the sender's confirmation of the exact account or IBAN. Afriex does not provide SWIFT account-holder resolution, so the UI must not label that account-holder name as provider-verified. The transfer invoice is created after provider acceptance; customers may optionally add a payment reference but never paste an invoice URL. |

- Editing country, bank, institution/routing/BIC, account/IBAN, phone, email, or either address clears verification and requires a new backend verification.
- Android obtains institutions and verification results through Firebase callables only. It does not call Afriex directly, ship a static bank list, or treat an entered display name as proof of a payout route.
- USA and SWIFT address fields use `searchRecipientAddress`, an App Check-protected callable. It returns Google Places address suggestions only when the server has `GOOGLE_PLACES_API_KEY` (or the compatible `GOOGLE_MAPS_API_KEY`) configured; manual complete-address entry always remains available and is the fallback for an unavailable address service. The Android app never receives the mapping key.
- The customer-facing quote contains one total transfer fee. Provider charges, owner fees, and route FX margin remain backend/admin-only accounting fields.

### Android Send-Money Screen Order

1. Show a pending transfer for the currently selected lane before the route selector, then show **App User**, **Mobile Money**, and **Bank Account**.
2. Every lane ends with a fresh live quote, one customer-visible total fee, a verified funding method, optional note, review, submit, and a lane-specific progress/receipt state.
3. **Mobile Money:** search saved recipients by name, phone, network, or country; expose Browse, Add, and Send again. Registration order is full name, country, provider, phone, re-entered phone, backend verification, sender confirmation, then save.
4. **App User:** search by name, phone, or email; expose Browse all, suggested members, and Send again. Select only the member's already verified bank or mobile-money receive route; do not expose editable recipient payment details. Block the send with setup guidance when no route is ready.
5. **Local Bank:** display `Who -> Bank -> Verify`; collect full name, country, recipient phone, backend institution selection/code, account number and confirmation, provider route verification, and sender confirmation.
6. **USA Local Bank:** remains a `BANK_ACCOUNT` route. Collect full name, United States, recipient phone, an 8- or 9-digit ABA routing number, provider-listed bank, account number and confirmation, optional recipient address, provider verification, and sender confirmation. Never render a static U.S. list, branch address, or SWIFT/BIC field.
7. **SWIFT Bank:** display `Who -> Bank -> SWIFT -> Verify`; collect full name, SWIFT-supported country, recipient phone, provider-listed BIC/institution, account or IBAN and confirmation, recipient email/address, official bank mailing address, backend institution verification, and sender confirmation. Invoice creation and delivery remain server-side after transfer acceptance.
8. Preserve the original saved rail when editing. Change of country, bank, institution/routing/BIC, account/IBAN, phone, email, or either address removes the proof and requires backend verification again.

## Callables Android must keep aligned with iOS

| Callable | Role |
| --- | --- |
| `getWalletTransferQuote` | FX + fees + total debit before submit |
| `createBeneficiaryVerification` | MM name/route check before saving |
| `applyApprovedBeneficiaryVerification` | Server-side creation of an approved mobile-money recipient |
| `initiateTransfer` | Commit send |
| `getWalletTransferReceipt` | Receipt sheet |
| `sendWalletTransferReceipt` | Email/SMS copy |
| `pollAfriexTransactionStatus` | WH-08 fallback while pending |
| `getMobileMoneySupportedCountries` | Corridor list |
| `getRecipientPayoutMethods` / list recipient routes | App-user bank/MM receive |
| `ensureAfriexCustomer` | SET-01 server bootstrap |
| `verifyAfriexCustomer` | Optional self-service Nigerian BVN verification; never used for a payout recipient or a send operation |
| `getAfriexInstitutions` | SET-04/05 |
| `resolveAfriexAccount` | SET-07 |
| `searchRecipientAddress` | Optional USA/SWIFT recipient and bank-address assistance; does not verify a payout route |
| `saveVerifiedBeneficiary` | Revalidates and persists a local-bank or SWIFT route |
| `refreshSavedBeneficiaryVerification` | Reuses a matching server proof; only rechecks changed or legacy routes |
| `getAfriexRates` | SET-08 helper |

Owner/admin UAT-only (not main consumer path): create Afriex withdraw payment method / withdraw transaction helpers if deployed.

## Temporary rollout

See [AFRIEX_TEMPORARY_TRANSACTION_ONLY_ROLLOUT.md](./AFRIEX_TEMPORARY_TRANSACTION_ONLY_ROLLOUT.md).

## Supported currencies & rails

Deposit vs payout country coverage (live vs **Coming soon**) is mirrored from
[Afriex Supported Currencies](https://docs.afriex.com/guides/supported-currencies) in:

- [`afriex/05_SUPPORTED_CURRENCIES_AND_RAILS.md`](./afriex/05_SUPPORTED_CURRENCIES_AND_RAILS.md) — includes **local bank** (`BANK_ACCOUNT`) and **SWIFT 100-country** lists
- [`afriex/afriex_supported_currencies.json`](./afriex/afriex_supported_currencies.json) — `localBankPayoutLiveIso2`, `swiftPayoutLiveIso2`, `swiftPayoutByRegion`

## Android product parity (transaction-only)

Keep aligned with the iOS wallet handoff:

1. **Three send loops** — App User, Mobile Money, and Bank Account stay separate in Send Money.
2. **Send Again / Recent Activity** — reopen the same original loop (`WalletNav.pendingSendAgainTransaction` → `TransactScreen`).
3. **App-user receipts** — label delivery as app-user route even when payout rails are MM/bank.
4. **PDF / share / print** — customer-safe statement receipts (Volunteers App / Soft Solutions Tech); no Afriex branding or raw API/webhook payloads.
5. **Callable-only** — no Stripe secret APIs and no Afriex REST from Android.
6. **Map** — [`ANDROID_TRANSACTION_ONLY_WALLET.md`](./ANDROID_TRANSACTION_ONLY_WALLET.md).

## Workflow for engineers

1. Complete prerequisites in [`afriex/01_TEST_ENVIRONMENT_AND_PREREQUISITES.md`](./afriex/01_TEST_ENVIRONMENT_AND_PREREQUISITES.md) (Functions env, not the app).
2. Run relevant SET / PAY-MM / WH cases from [`afriex/`](./afriex/).
3. Validate Android Send Money loops against [`afriex/ANDROID_WALLET_WORKFLOW.md`](./afriex/ANDROID_WALLET_WORKFLOW.md).
4. Confirm hub Recent Activity + History open receipt PDF/print and Send Again into the correct lane.
5. Sign off in [`afriex/99_SIGN_OFF.md`](./afriex/99_SIGN_OFF.md) before flipping live keys.
