# Webhooks & Notifications — real-time events, signature validation, retries

| Test ID | Channel / Rail | Service | Test Scenario | Preconditions | Steps / API Call | Expected Result | Actual Result | Status | Date Tested | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| WH-01 | Webhooks | Notification | Receive `TRANSACTION.CREATED` event | Webhook URL configured | Create any transaction; observe inbound POST to webhook URL | `TRANSACTION.CREATED` received with full payload incl. `transactionId`, status `PENDING` | | Not Started | | |
| WH-02 | Webhooks | Notification | Receive `TRANSACTION.UPDATED` on settlement | Transaction created | Await settlement (sandbox ~1–2 min) | `TRANSACTION.UPDATED` received with terminal status (`SUCCESS`/`FAILED`) | | Not Started | | |
| WH-03 | Webhooks | Security | Reject an invalid or unsigned provider event | Webhook URL configured | Send a payload with an invalid signature and then without any configured verification material | 403 for invalid signature; verification-misconfigured requests fail closed; no payout, balance, or revenue state changes | | Not Started | | |
| WH-04 | Webhooks | Idempotency | Replay an already processed provider event | A verified event has been delivered | Deliver the same event ID and payload again | Reconciliation record is updated safely; no duplicate receipt, provider-mirror credit, revenue entry, or app-wallet movement | | Not Started | | |
| WH-05 | Checkout | Reconciliation | Receive a verified hosted checkout event | Sandbox checkout session exists | Deliver the matching provider event with the server-created `merchantReference` | `afriex_checkout_sessions` remains `PENDING_PROVIDER_CONFIRMATION`; no app balance, platform revenue, or business-wallet mirror credit | | Not Started | | |
| WH-07 | Webhooks | Notification | Customer & payment-method events received | Webhook URL configured | Create/update a customer and a payment method | Corresponding customer and payment-method webhook events received | | Not Started | | |
| WH-08 | Status Polling | Notification | Poll transaction status as fallback | Transaction exists | `GET /api/v1/transaction/{transactionId}` | 200 with current status; matches webhook outcome | | Not Started | | |

## 2026-07-27 Submission Execution Results

The rows below are partner-reported sandbox results from [09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md](./09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md). Redacted provider event IDs, timestamps, and payload hashes are retained privately. `Pass` does not replace required production webhook-key validation.

| Test ID | Actual Result | Status | Date Tested | Notes |
| --- | --- | --- | --- | --- |
| WH-01 | `TRANSACTION.CREATED` delivered; pending/processing provider state mirrored to the payout record. Raw request-body signature validation and idempotent event handling completed server-side. | Pass | 2026-07-12 to 2026-07-27 | No customer delivery receipt is finalized from card funding alone. |
| WH-02 | `TRANSACTION.UPDATED` terminal event updated payout state and customer receipt/status from provider evidence. | Pass | 2026-07-12 to 2026-07-27 | Terminal success/failure follows the provider event; no internal app-wallet refund is created. |
| WH-07 | Customer and payment-method events were acknowledged; private provider customer/payment-method mapping was refreshed server-side. | Pass | 2026-07-12 to 2026-07-27 | Provider identifiers are not exposed to Android or iOS. |
| WH-08 | Stored Afriex transaction ID was polled successfully and matched the provider terminal outcome. | Pass | 2026-07-12 to 2026-07-27 | Scheduled reconciliation runs every five minutes; user polling is ownership-scoped. |

## Signature verification

- Header: `x-webhook-signature`
- Algorithm: RSA-SHA256 over the **raw** request body, base64-encoded
- Keys: staging and production webhook public keys differ (Dashboard > Developers > Webhooks)

## VolunteersApp mapping

| UAT | Backend | App |
| --- | --- | --- |
| WH-01 / WH-02 | Functions webhook handler updates `payout_requests` / wallet transactions | Receipt + history listen / refresh |
| WH-08 | Server poll helper | `pollAfriexTransactionStatus` callable (optional while pending) |
| WH-09 | Verified Afriex checkout event updates `afriex_checkout_sessions` to `PENDING_PROVIDER_CONFIRMATION` only | No consumer balance, provider mirror, or revenue credit is created |
| Push | FCM category / type `afriex_transaction` | Deep link to receipt / activity |

Client apps must **not** verify Afriex webhook signatures; that is server-only.

The webhook endpoint fails closed when neither a valid Afriex signature nor the configured fallback secret is present. Checkout events are reconciliation evidence only; hosted checkout remains sandbox UAT and cannot settle into an app-held balance.

## UAT evidence

- Store the provider event ID, transaction or merchant reference, terminal provider status, delivery timestamp, and redacted payload hash.
- Never store raw signatures, API keys, full account numbers, or unredacted customer payloads in the workbook or mobile logs.
- Polling is a fallback only. A polling response must not bypass the same provider-confirmation and no-custody rules used for webhooks.
