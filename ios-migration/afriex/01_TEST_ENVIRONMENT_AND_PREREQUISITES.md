# Test Environment & Prerequisites

| Item | Detail / Value | Owner | Confirmed (Y/N) |
| --- | --- | --- | --- |
| Sandbox base URL | `https://sandbox.api.afriex.com` | Afriex | Y |
| Production base URL | `https://api.afriex.com` | Afriex | Y |
| Sandbox API key issued | Sent in `x-api-key` header on every request | Afriex | Y |
| Production API key issued (for prod-only channels) | Required for Virtual Account / Pool Account / Crypto validation | Afriex | N - production onboarding approval pending |
| Authentication verified | `GET /api/v1/customer` returns 200 with valid key, 401 without | Partner | Y - sandbox UAT reported Pass |
| Business balance readable | `GET /api/v1/org/balance?currencies=USD` returns the provider business balance | Partner | Y - sandbox UAT reported Pass |
| Sandbox test credit confirmed | `POST /api/v1/org/balance/topup` returns a provider transaction ID and status (sandbox only) | Partner | Y - sandbox UAT reported Pass |
| Hosted checkout sandbox tested | `POST /api/v1/checkout-session` returns `data.checkoutUrl`; do not treat it as a wallet credit | Partner | N/A - excluded from submitted production scope |
| Webhook URL configured | Set in Dashboard > Developers > Webhooks | Partner | Y - sandbox UAT reported Pass |
| Webhook public key retrieved | Dashboard > Developers > Webhooks (staging & prod keys differ) | Partner | Y - sandbox key retrieved; production key pending |
| Webhook signature verification implemented | RSA-SHA256, base64, over the raw request body (`x-webhook-signature`) | Partner | Y - sandbox UAT reported Pass |
| Idempotency strategy in place | Fresh UUID v4 per transaction attempt | Partner | Y - server-generated and persisted |
| Institution codes cached | `GET /api/v1/payment-method/institution` per channel+country | Partner | Y - server cache by channel and country |
| In-scope channels confirmed | List the channels this integration will use | Partner | Y - Mobile Money plus Local Bank; SWIFT/collection rails excluded |
| In-scope countries/currencies confirmed | List target corridors | Partner | Y - 19 MM corridors; NG/GH Local Bank requested |

## VolunteersApp notes

- Local-bank and SWIFT payout execution is server-owned and feature-gated. Set `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED=true` only in the intended sandbox Function environment while completing payout UAT. Production Local Bank additionally requires `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED=true` and `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES`; production SWIFT separately requires `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true`. The 2026-07-27 submission requests only Local Bank and leaves SWIFT production disabled.
- The shipped bank/SWIFT funding lanes are `EXTERNAL_CARD` and a verified US ACH `EXTERNAL_BANK` method. A pending ACH charge remains `PENDING_BANK_SETTLEMENT`; only a succeeded charge advances to Afriex. Wallet and mobile-money funding are not enabled for these two delivery rails.
- An ambiguous Stripe funding response creates an operator-visible `FUNDING_RECONCILIATION_REQUIRED` payout record and a sender-level reconciliation lock. Support must reconcile the external payment and clear that lock before another Bank/SWIFT funding attempt; neither client retries nor app-wallet credit is permitted.
- Bank/SWIFT recipient payment methods and `WITHDRAW` transactions use the Afriex Business API from Functions. Functions creates the Afriex customer from the sender's verified name, email, E.164 phone, and ISO2 country, caches provider identifiers on the payout request, and polls `GET /transaction/{id}` as a webhook fallback.
- The existing provider webhook verifies Afriex RSA-SHA256 signatures whenever Bank/SWIFT execution is enabled, even if the legacy mobile-money adapter uses another provider.
- Android loads `getAfriexInstitutions` for the exact `MOBILE_MONEY`, `BANK_ACCOUNT`, or `SWIFT` channel and recipient ISO2 country. Bank and SWIFT beneficiary setup is blocked until the user selects a returned institution code. Functions resolves that code against the current provider list again before external funding, preventing stale or manually typed codes from reaching Afriex as HTTP 422 payment-method requests.
- Do not enable the production flag until both successful and forced-failure sandbox cases pass, including provider polling/webhook evidence and no internal wallet credit or refund.
- Business-balance calls are server-only. Configure `AFRIEX_BUSINESS_API_KEY`, `AFRIEX_BUSINESS_API_BASE_URL`, `AFRIEX_BUSINESS_API_VERSION`, and `AFRIEX_BUSINESS_API_TIMEOUT_MS` in Cloud Functions. The existing Afriex mobile-money key is only a compatibility fallback.
- `AFRIEX_BUSINESS_API_BASE_URL` must include the `/api/v1` prefix. The implemented balance paths are `GET /org/balance`, `POST /org/balance/topup`, and `GET /org/rates` below that prefix.
- The optional `x-api-version` header is date-based in the current Afriex Business API. Do not set `AFRIEX_BUSINESS_API_VERSION=1.0.7`; leave it unset for the latest stable API unless Afriex provides an approved ISO date.
- Hosted checkout UAT is server-owned. Configure `AFRIEX_CHECKOUT_TEST_AMOUNT_MINOR`, `AFRIEX_CHECKOUT_TEST_CURRENCY`, `AFRIEX_CHECKOUT_TEST_CHANNELS`, and `AFRIEX_CHECKOUT_REDIRECT_URL`; the owner profile must also have name, email, E.164 phone, and ISO2 country code.
- Hosted checkout is sandbox/staging validation work. Its `checkoutUrl` is a customer redirect, and the unique `merchantReference` is the reconciliation key.
- API keys, base URLs, and webhook verification live **only** in Cloud Functions / Firebase config; never in Android or iOS.
- App clients never hold Afriex keys.
- Record a redacted request/response pair, provider transaction ID, webhook event ID, and test date for every executed test. Do not commit secrets, signatures, full payment-method details, or raw customer PII.
- A UUID v4 is created for each logical transfer attempt. Retrying the same pending attempt must reuse its original idempotency key; a new user intent must receive a new key.
- Primary corridor for transaction-only release: **Uganda / MOBILE_MONEY / UGX** (expand via institution list after UAT).
- Sandbox settle window: ~1–2 minutes; force fail with `fail` in `meta.reference`.
