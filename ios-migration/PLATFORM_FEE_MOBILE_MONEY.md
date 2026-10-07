# Platform Fees — Mobile Money Collection (Afriex DEPOSIT)

First-party service fees (Sponsored Ad, Blind Date join, Blind Date rejoin) can be paid with a
verified mobile money number as an opt-in alternative to Stripe Checkout. Stripe stays the default.

## Why not Afriex hosted Checkout

Afriex hosted Checkout (including its mobile money screen) is sandbox-only unless Afriex signs off
production access separately. Clients must not show a hosted Afriex checkout button in production.
Fees are collected through the documented server flow instead:
customer → DEPOSIT payment method (MOBILE_MONEY) → DEPOSIT transaction → signed webhook.

## Guarantees

- Fees are **never** stored funds: no wallet credit, no wallet debit, no stored-funds ledger entry.
  Buyer receipts are `type: "INFO"` with `source: "PLATFORM_FEE_MOBILE_MONEY"`.
- Isolated from Send Money: requests carry `source: "PLATFORM_FEE_MOBILE_MONEY_COLLECTION"` and
  branch off before any transfer, wallet settlement, or transfer-lock logic.
- The service activates only after the provider reports a final `COMPLETED` with a live transaction
  reference and an amount/currency/payer that matches the server fee order.
- One provider request per attempt (`payout_requests/{orderId}-{attempt}`); the request id is the
  Afriex `idempotencyKey`/`reference`.
- The chosen rail is locked until a final outcome: Stripe checkout and a second mobile money attempt
  are refused while a mobile money order is pending or under reconciliation (and vice versa).
- A declined or timed-out prompt never downgrades the payer's number verification.

## Enablement (all required)

| Gate | Where |
| --- | --- |
| `MOBILE_MONEY_PROVIDER_NAME=AFRIEX` (default) and `MOBILE_MONEY_PROVIDER_MODE=HTTP_API` | Functions env |
| `AFRIEX_FIRST_PARTY_MOBILE_MONEY_FEE_COLLECTIONS_ENABLED=true` | Functions env (deployment flag) |
| `enableFirstPartyMobileMoneyFeeCollection: true` | `app_config/system_config` (public switch) |
| Payer country in the live Afriex collection allowlist | `AFRIEX_MOBILE_MONEY_COLLECTION_PRODUCTION_COUNTRIES` |
| Payment method `phoneOwnershipVerified == true` and `verificationStatus == "VERIFIED"` | user data |

In simulated or manual provider modes, fee requests wait (`providerStatus: WAITING_LIVE_PROVIDER`)
instead of completing, so a paid service can never be activated without real funds.

## Client contract

### Eligibility — `getPlatformFeeMobileMoneyOptions({ kind })`

`kind`: `SPONSORED_AD` | `BLIND_DATE_JOIN` | `BLIND_DATE_REJOIN`

```json
{
  "enabled": true,
  "defaultRail": "STRIPE",
  "amountUsd": 5,
  "methods": [
    { "id": "pm_1", "network": "MTN", "country": "GH", "currency": "GHS", "last4": "1234",
      "eligible": true, "reason": null, "estimatedLocalAmount": 61.25 }
  ]
}
```

Show mobile money only when `enabled` is true, and allow selecting only `eligible` methods. Show the
`reason` text for ineligible methods. Any error means Stripe only.

### Start payment — existing callables with two extra fields

`postSponsoredAd`, `joinBlindDate`, `rejoinBlindDate` accept:

- `paymentRail`: `"STRIPE"` (default) or `"MOBILE_MONEY"`
- `paymentMethodId`: required for `MOBILE_MONEY`

Mobile money response (pending):

```json
{ "success": true, "orderId": "…", "payoutRequestId": "…", "status": "PENDING_PROVIDER",
  "paymentStatus": "pending", "pending": true, "accessUnlocked": false, "charged": false,
  "message": "Approve the mobile money prompt on your phone. Your service activates after confirmation." }
```

There is no `checkoutUrl`. The presence of `payoutRequestId` identifies a mobile money order.

### Status — `getPlatformFeeMobileMoneyOptions({ orderId })`

Returns `{ orderId, kind, status, pending, accessUnlocked, charged, message, amountUsd, sourceAmount,
sourceCurrency, paymentMethodLast4 }` for the caller's own order only. Poll every ~5 s while `pending`
(Android stops after 5 minutes), then refresh the ad list or Blind Date status.

Order `status`: `PENDING_PROVIDER` → `PROCESSING_PROVIDER` → `COMPLETED` | `FAILED` |
`RECONCILIATION_REQUIRED` (collected or uncertain; blocks new attempts until support resolves it).

## Server records

- `platform_fee_mobile_money_orders/{sha256("MOBILE_MONEY:{kind}:{buyerId}:{resourceId}")}` — server only.
- `payout_requests/{orderId}-{attempt}` — `type: "CASH_IN"`, `fundingSource: "PLATFORM_FEE_MOBILE_MONEY"`,
  `platformFeeFulfillmentStatus`, `walletCreditedAmount: 0`.
- Revenue: `system/platform_revenue` (`advertisementFees` / `blindDateFees`).

## Android reference

- `wallet/PlatformFeePayment.kt` — models, eligibility/status calls, `PlatformFeePaymentSelector`.
- `advertisement/PostAdvertisementScreen.kt`, `date/DateScreen.kt` — selector in the confirm dialogs.
