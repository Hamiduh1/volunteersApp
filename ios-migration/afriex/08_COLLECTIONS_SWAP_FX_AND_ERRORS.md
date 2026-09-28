# Collections, Swap/FX, and Error Handling

This sheet completes the Afriex partner UAT workbook. It separates provider-level capability testing from the current VolunteersApp transaction-only release so that unsupported or production-only services are explicitly recorded as `N/A` or `Blocked`.

## Collections / deposits

| Test ID | Channel / Rail | Scenario | Expected provider result | VolunteersApp release status | Notes |
| --- | --- | --- | --- | --- | --- |
| COL-01 | Hosted checkout | Create a sandbox checkout session with a unique merchant reference and HTTPS return URL | 201 with `data.checkoutUrl` | In scope for owner sandbox UAT only | Use `createAfriexSandboxCheckoutSession`; a redirect or event never credits an app balance. See [07_HOSTED_CHECKOUT_AND_SETTLEMENT.md](./07_HOSTED_CHECKOUT_AND_SETTLEMENT.md). |
| COL-02 | Virtual Account | Create and validate a virtual account | Production-only endpoint; coordinate controlled provider validation | N/A | Do not expose as a consumer wallet top-up or call from mobile clients. |
| COL-03 | Pool Account | Retrieve or validate a pool account | Production-only endpoint; coordinate controlled provider validation | N/A | No app-held collection ledger is permitted. |
| COL-04 | Crypto Wallet | Create or retrieve a crypto wallet | Production-only endpoint; coordinate controlled provider validation | N/A | Crypto collection and payout are outside the current product scope. |

## Swap / FX

| Test ID | Service | Scenario | Expected result | VolunteersApp release status | Notes |
| --- | --- | --- | --- | --- | --- |
| FX-01 | Rates | Read `GET /api/v1/org/rates` for the configured source and target currencies | 200 with `data.rates` and `updatedAt` | In scope for server-side quote reference | Do not cache raw provider responses in mobile clients. |
| FX-02 | Quote | Present a provider-backed transfer quote with the applicable corridor fee and FX override | Quote is informational until a supported provider transfer is created | In scope for approved mobile-money corridors | Quote expiry and provider status must be visible in the review/receipt flow. |
| FX-03 | Swap | Execute a standalone provider FX/swap transaction | Provider contract test only if separately contracted | N/A | There is no standalone swap feature or internal wallet balance in the app. |

## Error handling

| Test ID | Scenario | Steps | Expected result | VolunteersApp release status |
| --- | --- | --- | --- | --- |
| ERR-01 | Missing or revoked API key | Call a server-side UAT endpoint without a valid `x-api-key` | Afriex returns 401; client receives a safe generic error; no state mutation | In scope |
| ERR-02 | API key lacks endpoint permission | Use a restricted key for balance, checkout, or transaction access | Afriex returns 403; no mirror or payout state is marked successful | In scope |
| ERR-03 | Invalid checkout configuration | Omit required server configuration, use a non-HTTPS return URL, unsupported channel, or invalid customer profile | Callable fails before opening a browser; no checkout URL or app balance is created | In scope |
| ERR-04 | Forced sandbox transaction failure | Include `fail` in `meta.reference` for an approved provider UAT transaction | Provider reports FAILED after the sandbox settle period; no internal wallet refund or credit occurs | In scope for mobile money, local bank, and SWIFT sandbox UAT |
| ERR-05 | Unsupported or coming-soon corridor | Select a route that is not in the approved corridor catalog | Server blocks the request before provider collection; UI presents unsupported/coming-soon state | In scope |
| ERR-06 | Invalid, unsigned, or replayed webhook | Deliver an invalid, unsigned, or duplicate provider event | Invalid/unconfigured events fail closed; duplicate evidence cannot create duplicate financial state | In scope |
| ERR-07 | Bank or SWIFT execution requested while the environment gate is off | Attempt local bank or SWIFT transfer without `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED=true` | Request is blocked before Stripe collection or provider submission | In scope |
| ERR-08 | Production Bank/SWIFT execution requested outside approved scope | Use production Afriex config without the rail-specific flag, use a Local Bank country not in `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES`, or request SWIFT while its separate flag is unset | Request is blocked before Stripe collection or provider submission | In scope |
| ERR-09 | Ambiguous external funding response | Cause a Stripe card or ACH timeout after the request may have reached Stripe | Quote and admin payout record become `FUNDING_RECONCILIATION_REQUIRED`; a sender-level lock blocks fresh Bank/SWIFT funding, and the app must not retry or create an internal wallet credit | In scope |
| ERR-10 | Ambiguous Afriex payout submission | Cause a timeout, 408, 429, or 5xx after the request may have reached Afriex | Payout remains `PROCESSING_PROVIDER` with `SUBMISSION_UNCERTAIN`; the scheduled poll safely reuses the persisted idempotency key and payment method | In scope |

## Test evidence

- Record the Test ID, date, environment, redacted request/response reference, provider transaction or event ID, final status, and defect reference.
- Do not record secrets, raw webhook signatures, payment details, or full customer PII in this file or app logs.
- A provider UAT pass does not by itself enable a consumer feature. The corresponding server adapter, reconciliation policy, receipt behavior, and release gate must also pass.
