# Android Marketplace handoff (backend second-eye fixes)

Backend and `firestore.rules` are already updated in both mirrors (`VolunteersAppiOS/my-firebase-project` and
`volunteersApp-merge/my-firebase-project`). Android client work is **not** done; this doc is the checklist.
All callables below use App Check (`enforceAppCheck: true`) and require a signed-in user.

## 1. Stop client Firestore create of `marketplace_items`

`firestore.rules` now has `allow create: if false` on `marketplace_items`. Any direct `set`/`add` from the app fails with
`PERMISSION_DENIED`.

Flow for posting a listing:

1. Upload photos/videos to Storage at `marketplace_images/{uid}/{file}` (unchanged).
2. Call **`createMarketplaceListing`** with the download URLs.
3. Use the returned `itemId`.

Request:

| Field | Type | Notes |
| --- | --- | --- |
| `title` | string | required, <= 200 chars |
| `description` | string | required, <= 8000 chars |
| `price` | number | > 0 and <= 50000 USD (rounded to cents) |
| `category` | string | <= 60 chars, defaults to `Other` |
| `condition` | string? | `NEW` / `LIKE_NEW` / `GOOD` / `FAIR` |
| `media` | `[{url, type: "image"\|"video", name?}]` | 1-20 entries; every `url` must be in `marketplace_images/{uid}/` of this project's bucket |
| `imageUrls` | `[string]?` | optional; derived from `media` images if omitted; at least one image is required |
| `locationName` | string? | defaults to `Global` |
| `countryCode` | string? | defaults to `INT` |
| `latitude`, `longitude` | number? | server rounds to 2 decimals (~1 km) **before** write; `0` = no location |
| `sellerPhone` | string? | see section 4 |
| `shareSellerPhone` | bool | must be `true` for the phone to be published |

Response: `{ itemId: string, success: true }`.

Server-enforced errors to surface in the UI:

- `resource-exhausted`: seller already has 3 open listings (status `AVAILABLE`/`ACTIVE`/`OPEN`, not deleted).
- `failed-precondition`: seller has not finished Stripe Connect payout setup for the marketplace service
  (`resolveVerifiedStripeConnectDestination(uid, "Seller", "MARKETPLACE")`). Send them to Seller earnings onboarding first.
- `invalid-argument`: bad title/description/price/condition/media/coordinates.

`update` (edit copy/media while open) and soft-delete on `marketplace_items` are unchanged and still go through Firestore.

## 2. Use `marketplace_orders` for purchase history / ownership

Public listings **never** carry the buyer (do not read or write `buyerId` on `marketplace_items`). After Stripe confirms payment the
webhook writes a private projection at `marketplace_orders/{orderId}` (orderId = Stripe commerce order id).

Readable by the buyer, the seller, or a platform admin. Clients cannot write it.

Fields: `orderId, itemId, buyerId, sellerId, title, amountUsd, platformFeeUsd, sellerNetUsd, imageUrl, status, fulfillmentStatus,
soldAt, createdAt, updatedAt, stripeCommerceOrderId, stripeCheckoutSessionId, stripePaymentIntentId, paymentProvider ("STRIPE")`,
plus workflow fields (`deliveredAt`, `helpReason`, `helpMessage`, `helpRequestedAt`, `helpResponse`, `helpResponseNote`,
`refundedAt`, `stripeRefundId`, `refundReason`, `resolution`, ...).

Queries:

- Buyer "My purchases": `where("buyerId", "==", uid)`
- Seller "My sales": `where("sellerId", "==", uid)`

Order `status` values: `PAID` -> `DELIVERED` | `HELP_REQUESTED` -> `REFUNDED` | `HELP_DECLINED` | `CLOSED`.
`fulfillmentStatus` starts at `AWAITING_HANDOFF`, then mirrors the status.

Wallet receipts are now deterministic: `users/{uid}/transactions/marketplace-purchase-{orderId}` (buyer) and
`marketplace-sale-{orderId}` (seller). Seller receipt status is **`STRIPE_SETTLEMENT_PENDING`** (was `PENDING_STRIPE_PAYOUT`); the note says
funds go to the seller's Stripe Connect balance and Stripe pays out on its own schedule; it is not an in-app wallet payout. Receipts also carry
`kind` and `itemId`/`resourceId`. Refunds flip them to `REFUNDED` and add `marketplace-refund-{orderId}` for the buyer.

When a listing is sold the server also deletes `sellerPhone` from the listing. On a refund the listing returns to `AVAILABLE` (and `soldAt`
is cleared) only if it is still SOLD by that same sale.

## 3. Wire order callables in the seller/buyer order UI

All take `{ orderId }` plus the fields shown and return `{ success: true, orderId, status }`.

| Callable | Caller | Transition | Extra data |
| --- | --- | --- | --- |
| `markMarketplaceOrderDelivered` | seller | `PAID` -> `DELIVERED` | none |
| `requestMarketplaceOrderHelp` | buyer | `PAID`/`DELIVERED` -> `HELP_REQUESTED` | `reason`: `NOT_RECEIVED` \| `CANCEL` \| `OTHER`; `message?` (<= 1000) |
| `respondMarketplaceOrderHelp` | seller | from `HELP_REQUESTED` | `action`: `APPROVE_REFUND` (Stripe refund -> `REFUNDED`) \| `DECLINE` (-> `HELP_DECLINED`); `note?` |
| `resolveMarketplaceOrder` | platform owner/admin only | any open order | `action`: `REFUND` \| `CLOSE`; `note?` |

UI suggestions:

- Seller order detail: "Mark delivered" while `PAID`; Approve refund / Decline buttons while `HELP_REQUESTED`.
- Buyer order detail: "Need help" (reason picker + optional message) while `PAID` or `DELIVERED`; show `HELP_DECLINED` as "Under platform review".
- Admin tools can call `resolveMarketplaceOrder` for `HELP_DECLINED`/`HELP_REQUESTED` orders.
- Refund failures return `failed-precondition` and leave the order unchanged so the action can be retried.

Refunds go back to the buyer's original card via Stripe (`reverse_transfer` + `refund_application_fee`), see
`MARKETPLACE_STRIPE_CHARGE_MODEL.md`.

## 4. Seller phone consent (`shareSellerPhone`)

Listings are readable by every signed-in member. The server stores `sellerPhone` **only** when `shareSellerPhone === true` **and** the phone is
non-empty. Otherwise the field is omitted. Add an explicit opt-in toggle ("Show my phone number on this listing") and never prefill/publish the
account phone silently. The phone is removed from the listing automatically when it sells.

## Android drift reminder

Android `index.ts` still uses `assertAccountVerified` on `deleteOrganizerEvent` and `applyForEvent`; iOS does not. Marketplace patches were
applied to both files without touching those two callables.
