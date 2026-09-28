# Afriex Hosted Checkout & Settlement Boundary

Official references:

- [Create Checkout Session](https://docs.afriex.com/api-reference/endpoint/checkout-sessions/create)
- [Get Business Balance](https://docs.afriex.com/api-reference/endpoint/balance/get)
- [Top Up Sandbox Balance](https://docs.afriex.com/api-reference/endpoint/balance/topup)

## What checkout is

`POST /api/v1/checkout-session` creates a hosted payment page and returns `data.checkoutUrl`. The customer is redirected to that URL to pay. The request must contain:

- `amount` in the currency's minor units, with a minimum of `100`
- Uppercase ISO-4217 `currency`
- A unique `merchantReference`
- An HTTPS `redirectUrl`
- `customer.name`, `customer.email`, `customer.phone`, and `customer.countryCode`
- At least one allowed channel: `VIRTUAL_BANK_ACCOUNT`, `MOBILE_MONEY`, or `CARD`

Hosted checkout is currently sandbox/staging validation work. It is not generally available for production according to the current Afriex endpoint reference.

## VolunteersApp Rules

1. Android and iOS never call Afriex REST or receive an Afriex API key. Cloud Functions own session creation, redirect allowlisting, webhook verification, and reconciliation.
2. A `checkoutUrl` does not credit `users/{uid}`, `wallets/{uid}`, `system/platform_revenue`, or `system/afriex_business_wallet`.
3. Create one opaque `merchantReference` per checkout attempt. Persist it with the intended business order before redirecting, and use it to match provider events and resulting transactions.
4. A redirect return alone is not payment confirmation. The current UAT webhook records a verified provider event as pending reconciliation only; it does not mark any internal balance paid.
5. The owner dashboard may show provider settlement state only. It must not offer hosted checkout as an internal-float or customer-wallet top-up action.

## Owner Settlement Mirror

| Operation | Server action | Firestore effect |
| --- | --- | --- |
| Refresh provider balance | `GET /api/v1/org/balance?currencies=USD` | Mirror the returned business balance with sync time. |
| Add sandbox test credit | `POST /api/v1/org/balance/topup` then `GET /api/v1/org/balance` | Record provider transaction ID/status and the refreshed balance. Sandbox only. |
| Create sandbox UAT checkout | `createAfriexSandboxCheckoutSession` posts `POST /api/v1/checkout-session` with configured amount, channels, and return URL | Store a pending reconciliation record keyed by `merchantReference`; do not credit a balance. |

## UAT Cases

| Test ID | Scenario | Expected result |
| --- | --- | --- |
| CHK-01 | Create a sandbox checkout with one allowed channel | 201 with `data.checkoutUrl`; `merchantReference` is unique. |
| CHK-02 | Return from hosted checkout without an authoritative provider confirmation | Order remains pending; no wallet or settlement-mirror credit. |
| CHK-03 | Receive and verify the applicable checkout/transaction provider event | Record the matching merchant reference as pending reconciliation exactly once; no internal credit is created. |
| CHK-04 | Attempt `POST /org/balance/topup` in production | 403 from Afriex; the app shows no production top-up control. |
