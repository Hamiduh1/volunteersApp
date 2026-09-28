# Afriex Business API — Partner Integration UAT Template

User Acceptance Testing (UAT) workbook for businesses integrating with Afriex.

| Field | Value |
| --- | --- |
| Partner / Business name | SoftSolutions |
| Integration owner (name) | SoftSolutions Technical Integration Team *(replace with named owner before submission)* |
| Technical contact (email) | *(required before submission)* |
| Afriex environment | Sandbox (`https://sandbox.api.afriex.com`) |
| UAT start date | 2026-07-12 |
| UAT target completion date | 2026-07-27 |
| API version | v1 (1.0.7) |
| Documentation reference | https://docs.afriex.com |

## API contract notes

- Every API path in this workbook is relative to `https://sandbox.api.afriex.com/api/v1` for sandbox or `https://api.afriex.com/api/v1` for production.
- The current Business API uses `GET /org/balance`, `POST /org/balance/topup`, and `GET /org/rates`. The shorter `/balance` wording in older workbook copies is not the implementation URL.
- The workbook's `v1 (1.0.7)` is integration metadata, not an `x-api-version` header value. The current API documents that optional header as an ISO date; omit it to use the latest stable API unless Afriex supplies a date-based version to pin.
- Never record API keys, webhook signatures, full account numbers, or raw customer PII in this repository. Capture redacted request/response evidence and the provider transaction ID only.
- The confirmed production-account terms are maintained in [11_PRODUCTION_ACCOUNT_TERMS_2026-09-02.md](./11_PRODUCTION_ACCOUNT_TERMS_2026-09-02.md) and its machine-readable fee mirror. Mobile-money fees and production availability are agreement-specific; do not use provider-reference values as production commercials.

## How to use this workbook

1. Read [Test Environment & Prerequisites](./01_TEST_ENVIRONMENT_AND_PREREQUISITES.md) first and complete every prerequisite before testing.
2. Work through each test-case file. Files are grouped by flow: Setup & Reference, Collections (deposits), Payouts (withdrawals), SWAP/FX, Webhooks, and Error Handling.
3. For each test case, record the **Actual Result**, set the **Status** (`Pass` / `Fail` / `Blocked` / `N/A`), set **Severity** for any defect, and add your name, date, and any defect reference in **Notes**.
4. Only test channels and countries relevant to your integration. Mark out-of-scope rows as `N/A` so coverage is explicit.
5. In sandbox, transactions auto-settle in ~1–2 minutes. To force a **FAILED** outcome, include the word `fail` in `meta.reference`; any other reference settles as **SUCCESS**.
6. Endpoints marked **Production only** (Virtual Account, Pool Account, Crypto Wallet) cannot be exercised in sandbox — coordinate a controlled production validation with the Afriex team.
7. Complete the [sign-off block](./99_SIGN_OFF.md) once UAT is complete.
8. Raise blockers and defects on the integration support channel with the **Test ID** and request/response payloads.

## Execution rules

- Generate a UUID v4 idempotency key for each logical transaction attempt, persist it with the pending transfer, and reuse that same key only for safe network retries of that attempt. A new user-initiated transfer gets a new key.
- Treat redirect returns, polling responses, and webhook payloads as provider evidence. Do not create an app-held balance, mark a payout delivered, or issue an internal refund until the provider-backed reconciliation policy explicitly allows it.
- Complete provider API UAT from approved server-side tooling only. Android and iOS remain Firebase-callable clients and must never call Afriex directly.
- Mark a test `N/A` when it is outside the release scope. Mark it `Blocked` when the provider contract is in scope but the required server adapter has not been implemented.

## Status legend

| Status | Meaning |
| --- | --- |
| Not Started | Test has not yet been executed. |
| Pass | Actual result matches expected result. |
| Fail | Actual result does not match expected result — log a defect. |
| Blocked | Cannot execute due to an unmet dependency or environment issue. |
| N/A | Channel/service not in scope for this partner. |

## Files in this pack

| File | Scope |
| --- | --- |
| [01_TEST_ENVIRONMENT_AND_PREREQUISITES.md](./01_TEST_ENVIRONMENT_AND_PREREQUISITES.md) | Sandbox/prod URLs, keys, webhooks, idempotency |
| [02_SETUP_AND_REFERENCE.md](./02_SETUP_AND_REFERENCE.md) | SET-01 … SET-10 customers, institutions, rates, balance |
| [03_PAYOUTS_WITHDRAWALS.md](./03_PAYOUTS_WITHDRAWALS.md) | PAY-* provider bank / mobile money / SWIFT / UPI tests and VolunteersApp release gates |
| [04_WEBHOOKS_AND_NOTIFICATIONS.md](./04_WEBHOOKS_AND_NOTIFICATIONS.md) | WH-* events + status polling |
| [05_SUPPORTED_CURRENCIES_AND_RAILS.md](./05_SUPPORTED_CURRENCIES_AND_RAILS.md) | Deposit vs payout corridors; **local bank** vs **SWIFT (100 countries)** |
| [afriex_supported_currencies.json](./afriex_supported_currencies.json) | Machine-readable ISO2 + rail catalog (`swiftPayoutLiveIso2`, `localBankPayoutLiveIso2`, …) |
| [06_SCHEDULE1_TRANSFER_FEES.md](./06_SCHEDULE1_TRANSFER_FEES.md) | Confirmed bank commercials, mobile-money MSA policy, and SWIFT terms |
| [07_HOSTED_CHECKOUT_AND_SETTLEMENT.md](./07_HOSTED_CHECKOUT_AND_SETTLEMENT.md) | Hosted checkout contract, provider reconciliation, and no-custody guardrails |
| [08_COLLECTIONS_SWAP_FX_AND_ERRORS.md](./08_COLLECTIONS_SWAP_FX_AND_ERRORS.md) | Collections, swap/FX, and error-handling UAT scope / release gates |
| [afriex_schedule1_transfer_fees.json](./afriex_schedule1_transfer_fees.json) | Default corridor fee catalog for quotes / admin |
| [09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md](./09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md) | Submitted sandbox evidence summary, requested initial production scope, and exclusions |
| [10_RUNTIME_CONFIGURATION_AND_KEY_HYGIENE.md](./10_RUNTIME_CONFIGURATION_AND_KEY_HYGIENE.md) | Shared Android/iOS/Functions configuration boundary, webhook setup, and credential rotation procedure |
| [11_PRODUCTION_ACCOUNT_TERMS_2026-09-02.md](./11_PRODUCTION_ACCOUNT_TERMS_2026-09-02.md) | Confirmed production corridor, limit, fee, funding, and onboarding terms |
| [12_MOBILE_MONEY_RECIPIENT_VERIFICATION_UAT_2026-09-06.md](./12_MOBILE_MONEY_RECIPIENT_VERIFICATION_UAT_2026-09-06.md) | 19-corridor recipient-registration parity, provider-resolution behavior, and production evidence requirements |
| [13_SUPPORTED_COUNTRIES_REFRESH_2026-09-06.md](./13_SUPPORTED_COUNTRIES_REFRESH_2026-09-06.md) | Current provider mobile, local-bank, and SWIFT coverage with account-entitlement safeguards |
| [14_PAYMENT_METHOD_RESOLUTION_PRODUCTION_2026-09-07.md](./14_PAYMENT_METHOD_RESOLUTION_PRODUCTION_2026-09-07.md) | Afriex production recipient-resolution contract, diagnostics, and provider escalation evidence |
| [99_SIGN_OFF.md](./99_SIGN_OFF.md) | UAT acceptance sign-off |
| [ANDROID_WALLET_WORKFLOW.md](./ANDROID_WALLET_WORKFLOW.md) | How VolunteersApp Android maps to this UAT (callable-only) |

## Critical product rule (VolunteersApp)

**Do not call Afriex REST from the Android or iOS app.**  
All Afriex Business API traffic stays in Cloud Functions. Clients use Firebase callables only (`getWalletTransferQuote`, `initiateTransfer`, `pollAfriexTransactionStatus`, etc.).

Android handoff extras (transaction-only):

- Three send loops: App User, Mobile Money, Bank Account
- Recent Activity / Send Again reopen the same original loop
- App-user receipts stay app-user even if payout uses MM/bank
- Customer-safe PDF / share / print (no Afriex branding, no raw webhook/API payloads)

See also:

- [../ANDROID_AFRIEX_LIVE_MODE_PARITY.md](../ANDROID_AFRIEX_LIVE_MODE_PARITY.md)
- [../ANDROID_TRANSACTION_ONLY_WALLET.md](../ANDROID_TRANSACTION_ONLY_WALLET.md)
- [ANDROID_WALLET_WORKFLOW.md](./ANDROID_WALLET_WORKFLOW.md)
