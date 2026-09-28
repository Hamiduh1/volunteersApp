# Setup & Reference — customers, institutions, resolve, rates, balance

> **Endpoint normalization:** all paths are relative to `/api/v1`. The current Business API paths are `/org/balance`, `/org/balance/topup`, and `/org/rates`; do not substitute the older shorthand `/balance`, `/balance/topup`, or `/rates`.

| Test ID | Channel / Rail | Service | Country (example) | Currency | Test Scenario | Preconditions | Steps / API Call | Expected Result | Actual Result | Status | Severity | Date Tested | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SET-01 | Core | Setup | — | — | Create a customer with valid KYC fields | Valid sandbox API key | `POST /api/v1/customer` with fullName, email, phone (E.164), countryCode | 201 Created; response returns a `customerId`; record stored for later steps | | Not Started | High | | |
| SET-02 | Core | Setup | — | — | Create customer with phone/country mismatch | Valid API key | `POST /api/v1/customer` with phone country not matching countryCode | 400 with code `INVALID_BUSINESS_CUSTOMER_REQUEST` and friendlyMessage about phone/country mismatch | | Not Started | Medium | | |
| SET-03 | Core | Setup | — | — | Retrieve, list, update KYC and delete a customer | Customer exists | `GET /customer/{id}`; `GET /customer?page=0&limit=10`; PATCH KYC; `DELETE /customer/{id}` | 200 on get/list/update; list paginated with page/total/data; 204 on delete | | Not Started | Medium | | |
| SET-04 | Core | Reference | NG | NGN | List institutions for a bank channel & country | Valid API key | `GET /payment-method/institution?channel=BANK_ACCOUNT&countryCode=NG` | 200 with list of institutions, each with `institutionCode` and `institutionName` | | Not Started | High | | |
| SET-05 | Core | Reference | GH | GHS | List institutions for mobile money channel | Valid API key | `GET /payment-method/institution?channel=MOBILE_MONEY&countryCode=GH` | 200 with mobile money providers (e.g. MTN) and their codes | | Not Started | High | | |
| SET-06 | Core | Reference | DE | EUR | Resolve SWIFT/BIC code to institution name | Valid API key | `GET /payment-method/institution/codes` with a known SWIFT/BIC or US routing number | 200 resolving the code to the correct bank/institution name | | Not Started | Medium | | |
| SET-06b | Core | Reference | AE | USD | List SWIFT institutions for a recipient country | Valid API key | `GET /payment-method/institution?channel=SWIFT&countryCode=AE` | 200 with valid SWIFT/BIC institutions for UAE (one of 100 SWIFT countries) | | Not Started | Medium | | See [05_SUPPORTED_CURRENCIES_AND_RAILS.md](./05_SUPPORTED_CURRENCIES_AND_RAILS.md) |
| SET-06c | App guard | Any enabled Bank/SWIFT corridor | — | Reject a stale, missing, or mismatched institution code before external funding | Existing beneficiary with altered code/name | Call `initiateTransfer` for Bank/SWIFT | `failed-precondition`; no Stripe charge and no provider payment-method request | | Not Started | High | | Confirms no manual/free-text code can produce provider HTTP 422 |
| SET-07 | Core | Reference | NG | NGN | Resolve bank account holder name before payout | Institution code known | Call Resolve Payment Method for a bank account/mobile money | 200 returning the resolved account holder name for verification | | Not Started | Medium | | |
| SET-08 | Core | Reference | — | USD/NGN | Get real-time exchange rate | Valid API key | `GET /api/v1/org/rates` for a currency pair | 200 with current rate; rate cached client-side | | Not Started | Medium | | |
| SET-09 | Core | Reference | — | Multi | Get business wallet balance | Valid API key | `GET /api/v1/org/balance?currencies=USD,NGN` | 200 with a `data` map keyed by currency; mirror only the provider result | | Not Started | High | | |
| SET-10 | Core | Setup | — | USD | Add a sandbox test credit | Sandbox API key | `POST /api/v1/org/balance/topup` with `amount` + uppercase `currency` | 200 with provider `transactionId` + `status`; endpoint returns 403 in production | | Not Started | High | | Never increment a Firestore balance before this response and a provider balance refresh. |
| SET-11 | Checkout | Hosted payment | NG | NGN | Create a hosted checkout session | Sandbox API key, unique merchant reference, HTTPS redirect URL | `POST /api/v1/checkout-session` with minor-unit `amount`, `currency`, `merchantReference`, `redirectUrl`, `customer`, and at least one channel | 201 with `data.checkoutUrl`; redirect customer to it and reconcile by merchant reference | | Not Started | High | | See [07_HOSTED_CHECKOUT_AND_SETTLEMENT.md](./07_HOSTED_CHECKOUT_AND_SETTLEMENT.md). |

## 2026-07-27 Submission Execution Results

The rows below preserve the original template above and record the partner-reported sandbox results from [09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md](./09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md). Redacted provider evidence is retained privately; these entries do not constitute Afriex production approval.

| Test ID | Actual Result | Status | Severity | Date Tested | Notes |
| --- | --- | --- | --- | --- | --- |
| SET-01 | Customer created/reused before payout; private provider customer ID stored. | Pass | High | 2026-07-12 to 2026-07-27 | Redacted provider IDs retained privately. |
| SET-02 | Invalid phone/country data rejected before payout submission; user receives a mapped validation error. | Pass | Medium | 2026-07-12 to 2026-07-27 | Retain the exact redacted provider response in the private evidence channel. |
| SET-03 | Not exposed in the transaction-only product. | N/A | N/A | 2026-07-27 | Requires a separate compliance-approved identity-data lifecycle. |
| SET-04 | Current Nigeria Local Bank list loaded; selected provider code is persisted and revalidated before funding. | Pass | High | 2026-07-12 to 2026-07-27 | Provider institution codes, not free-text names, are submitted. |
| SET-05 | Ghana Mobile Money providers and returned codes loaded before recipient registration. | Pass | High | 2026-07-12 to 2026-07-27 | Provider code is retained with the beneficiary route. |
| SET-06 | Not executed. | N/A | N/A | 2026-07-27 | SWIFT is excluded from this submission and requires separate fixtures/UAT. |
| SET-07 | Supported recipient details resolved/reverified before final payout confirmation. | Pass | Medium | 2026-07-12 to 2026-07-27 | Resolution is used where the provider supports it. |
| SET-08 | Provider rate fetched server-side; configured platform FX margin applied to a 60-second quote. | Pass | Medium | 2026-07-12 to 2026-07-27 | Android and iOS do not call Afriex or cache raw rate responses. |
| SET-09 | Business balance refreshed into owner reporting mirror; not a customer spendable balance. | Pass | High | 2026-07-12 to 2026-07-27 | Owner operations and settlement visibility only. |
| SET-10 | Sandbox top-up and provider balance refresh completed; production top-up is blocked. | Pass | High | 2026-07-12 to 2026-07-27 | No Firestore customer-balance credit is created. |

**Template correction for SET-08:** use `GET /api/v1/org/rates`, and cache the rate only in trusted server-side quote logic, not in the mobile client.

## VolunteersApp callable mapping

| UAT | Server-side (Functions) | App callable (if exposed) |
| --- | --- | --- |
| SET-01 customer create | `ensureAfriexCustomer` / provider bootstrap | `ensureAfriexCustomer` |
| SET-04 / SET-05 institutions | institution cache for MM/bank corridors | `getAfriexInstitutions` |
| SET-07 resolve account | Android requires a re-entered E.164 number, current Afriex mobile-money institution code, and explicit confirmation of the returned account-holder name before beneficiary save; Functions repeats the resolve before payout | `resolveAfriexAccount` / `createBeneficiaryVerification` |
| SET-08 rates | provider FX preview / final transfer quote | `getAfriexRates` / `getWalletTransferQuote` |
| SET-09 balance | owner-only callable reads `GET /org/balance`; `system/afriex_business_wallet` mirrors the provider response | `syncAfriexBusinessWalletMirror` (owner dashboard only) |
| SET-10 sandbox credit | owner-only callable posts `POST /org/balance/topup`, then refreshes provider balance | `topupAfriexSandboxBusinessWallet` (sandbox only) |
| SET-11 hosted checkout | owner-only sandbox UAT callable creates a provider session with server-configured amount, channels, and return URL; it stores a pending reconciliation record only | `createAfriexSandboxCheckoutSession` (owner dashboard sandbox action) |

## Evidence and release boundary

- `SET-09` and `SET-10` prove a provider business-wallet mirror only. They must not be used to fund an app user's wallet.
- `SET-11` proves hosted checkout creation and a trusted redirect only. A redirect return or webhook event remains `PENDING_PROVIDER_CONFIRMATION` and creates no app balance or revenue credit.
- Provider customer, payment-method, and transaction UAT must use server-side tooling. The mobile clients receive only the approved callable result and consumer-safe receipt state.
- Rate data is requested and cached only in trusted server-side quote logic; Android and iOS do not call Afriex or cache raw provider responses.
