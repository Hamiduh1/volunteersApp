# Seller earnings (Stripe) vs Send Money rails

iOS shipped this on `recovery-deployed-functions-source` (Payment Methods + Marketplace / paid-event gates). Mirror on Android without changing backend callables.

## Product rails

| Job | UI section | Rail |
|---|---|---|
| Fund Send Money | Funding | Card, verified US ACH, verified mobile money |
| Receive from members | Receive | Afriex local bank / SWIFT / mobile money |
| Marketplace, Garage Sales, paid events, organizer earnings | **Seller earnings (Stripe)** | One Stripe Express account; bank inside Stripe |

Mobile money receive routes **never** settle Marketplace / Garage / paid-event earnings.

## Bugs iOS fixed (Android parity)

1. **Heading** — Avoid “Business” alone. Prefer **Seller earnings (Stripe)** so it is not confused with Afriex.
2. **Do not hide seller setup** when the user already has Afriex MM/bank receive routes. Show Connect whenever the profile country is Stripe-supported.
3. **Settlement gate** — Before listing/selling, require `stripePayoutReady` (Connect account with details submitted + charges + payouts). Do **not** treat verified mobile money / Afriex bank as “ready to sell.”
4. **One Express account** — Enabling Marketplace then Garage tags `stripeConnectCommerceServices` on the **same** account. Copy should say that explicitly.
5. **Picker** — Offer only: Marketplace, Garage Sales, Paid events, Organizer earnings. Ads/dating fees pay the platform and do not need Connect.
6. **Parse `commerceServices`** from `getConnectAccountStatus` and show which products are already enabled.
7. **After `createConnectAccount`** — Always re-fetch status before skipping the onboarding link (stale `payoutsEnabled` skipped Stripe).

## Android files to touch

- `wallet/PaymentMethodsScreen.kt` — section title/subtitle, service chips, enabled-product display, dialog copy
- Marketplace / Garage / Organizer sell flows — replace any “has MM or bank → OK” check with Stripe Connect ready
- ViewModels that call `getConnectAccountStatus` / `createConnectAccount` / `createConnectOnboardingLink`

## Server errors users hit today

- `{label} has not enabled Stripe Connect for this in-app commerce service.`
- `{label} must finish Stripe Connect payout setup before accepting payments.`

Those mean the product was never tagged / Express is incomplete — not that Afriex is an alternative for those products.
