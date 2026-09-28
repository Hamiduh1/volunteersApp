# Runtime Configuration and Key Hygiene

This is the shared Android, iOS, and Functions configuration reference. It intentionally contains no credential values.

## Ownership Boundary

| Layer | Responsibility | Must not contain |
| --- | --- | --- |
| Android app | Firebase callable client; presents provider-backed transfer state | Afriex API keys, webhook keys, Stripe secret keys, or direct provider REST calls |
| iOS app | Firebase callable client; presents provider-backed transfer state | Afriex API keys, webhook keys, Stripe secret keys, or direct provider REST calls |
| Cloud Functions | Afriex and Stripe API calls, webhook verification, idempotency, and provider reconciliation | Client-disclosed credentials or an app-held money ledger |
| Provider dashboards | Key issue/rotation and webhook destination configuration | Source-controlled secret values |

## Functions Runtime Variables

Configure these only in the Cloud Functions runtime environment or an approved secret store. Do not add values to Kotlin, Swift, migration documents, or committed environment files.

| Variable | Required when | Purpose |
| --- | --- | --- |
| AFRIEX_MODE or AFRIEX_ENV | Every Afriex environment | Selects sandbox or production |
| AFRIEX_BUSINESS_API_KEY | Every Afriex Business API flow | Server-side Business API authentication |
| AFRIEX_BUSINESS_API_BASE_URL | Optional override | Defaults to the supported sandbox or production Afriex endpoint |
| AFRIEX_BUSINESS_API_TIMEOUT_MS | Optional | Provider request timeout |
| AFRIEX_BUSINESS_API_VERSION | Only when Afriex issues a date-based pin | Optional provider API-version header |
| AFRIEX_BUSINESS_WALLET_CURRENCY | Balance/top-up UAT | Business wallet settlement currency |
| AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED | Bank or SWIFT execution | Explicit release gate for provider-backed bank/SWIFT payouts |
| GOOGLE_PLACES_API_KEY | Optional USA/SWIFT address suggestions | Server-only Google Places Autocomplete credential used by `searchRecipientAddress`; without it, address entry remains manual |
| MOBILE_MONEY_PROVIDER_WEBHOOK_PUBLIC_KEY | Afriex webhook validation | RSA public key used to verify the raw request body and x-webhook-signature |
| MOBILE_MONEY_PROVIDER_WEBHOOK_SECRET | Legacy fallback only | Fallback secret header; it is not a replacement for an Afriex public-key signature |
| STRIPE_ENV or STRIPE_MODE | Stripe flows | Selects test or live |
| STRIPE_SECRET_KEY_TEST / STRIPE_SECRET_KEY_LIVE | Stripe flows | Server-side Stripe API authentication |
| STRIPE_WEBHOOK_SECRET | Stripe webhook processing | Stripe webhook signature verification |

The deployment must use matching keys and endpoints: sandbox key with the sandbox host, production key with the production host. The Function configuration validates the host and rejects clear test/live key mismatches.

## Provider Dashboard Setup

1. Configure the deployed Cloud Function webhook URL in the Afriex Business dashboard.
2. Retrieve the environment-specific Afriex webhook public key and store it only in the Functions runtime configuration.
3. Configure the Stripe destination as `https://us-central1-<firebase-project-id>.cloudfunctions.net/stripeWebhook`. It is an unauthenticated HTTP endpoint because Stripe calls it directly, but it verifies every `stripe-signature` against the raw request body before processing.
4. Store that destination's signing secret only as `STRIPE_WEBHOOK_SECRET` in the Functions runtime configuration. Never place a signing secret, destination ID, or copied Stripe Dashboard details in Android or iOS source.
5. Enable only the Stripe events used by the deployed product: Checkout settlement (`checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`, `checkout.session.expired`), bank funding settlement (`charge.pending`, `charge.succeeded`, `charge.failed`, `charge.updated`, `charge.expired`), and Connect payouts (`payout.created`, `payout.updated`, `payout.paid`, `payout.failed`, `payout.canceled`). The handler safely ignores unrelated event types.
6. Keep sandbox and production settings separate. Never reuse a sandbox key, public key, or webhook setting in production.
7. Send an authenticated sandbox event and confirm the raw-body signature verification succeeds before enabling production traffic.

## Client Release Checks

1. Android and iOS use Firebase callables only for customer setup, institution lookup, route resolution, quotes, transfer initiation, and transaction polling.
2. A saved recipient is server-verified before it is persisted. A changed route, account number, mobile number, institution, country, or SWIFT/BIC invalidates that verification.
3. USA and SWIFT address suggestions use `searchRecipientAddress`; the Google key is server-only, and a complete manually entered address remains valid when suggestions are unavailable.
4. Provider failures remain provider failures; neither app creates a local wallet refund or treats a pending transfer as delivered.
5. Logs, receipts, Firestore records, support tickets, UAT evidence, and error messages contain no API key, webhook signature, full account number, or raw provider payload.

## Send-Money Funding Contract

The quote endpoint and transfer endpoint enforce the same provider-backed funding rules:

| Send lane | Allowed funding | Delivery |
| --- | --- | --- |
| App User | External card, verified US ACH bank, or eligible verified mobile-money collection | Recipient's server-resolved verified local-bank or mobile-money receive route |
| Mobile Money | External card, verified US ACH bank, or verified mobile money | Saved and Afriex-verified mobile money recipient |
| Local Bank / SWIFT | External card or verified US ACH bank | Saved and Afriex-verified local bank or SWIFT institution route |

No client can obtain a quote with a legacy wallet funding type. A quote is bound to its sender, amount, route, recipient, funding method, and expiry; it is reserved before funding and cannot be reused after consumption or an in-progress attempt.

## Credential Exposure Response

If a credential is pasted into an app source file, documentation file, chat, ticket, build log, or repository:

1. Revoke or rotate it in the relevant provider dashboard immediately.
2. Update the replacement value only in the approved Functions secret/runtime configuration.
3. Reconfigure the matching webhook public/signing material if it was exposed.
4. Redeploy the affected Functions and test sandbox authentication, signature verification, and one safe provider flow.
5. Remove the exposed value from source/history according to repository-security procedures. Do not copy it into an issue, migration document, or client application while fixing the incident.

## References

- [Afriex UAT workbook](./README.md)
- [Android/iOS/Functions live-mode parity](../ANDROID_AFRIEX_LIVE_MODE_PARITY.md)
- [Test environment prerequisites](./01_TEST_ENVIRONMENT_AND_PREREQUISITES.md)
