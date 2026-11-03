# iOS + Android Marketplace Contract

Last updated: 2026-11-02

iOS redesigned the marketplace: a photo-first feed, a full listing page, and a faster posting flow. This note lists the
Firestore changes so Android stays compatible. Everything is additive; old listings keep working on both platforms.

## 1) `marketplace_items/{itemId}` — new optional `condition`

| Value      | Label    |
|------------|----------|
| `NEW`      | New      |
| `LIKE_NEW` | Like new |
| `GOOD`     | Good     |
| `FAIR`     | Fair     |

- Optional on create. When present it must be one of the four values above (rules reject anything else).
- Sellers may change it when editing: `condition` is now in the editable-field allowlist.
- Listings without `condition` should render without a condition badge.

## 2) `users/{uid}/marketplace_saves/{itemId}` — saved items (owner-only)

```
{ itemId: string,          // must equal the document ID
  savedAt: serverTimestamp }
```

- Only the owner can read, create or delete. No other keys are allowed, and `savedAt` must be the server timestamp.
- Unsave by deleting the document.
- A save can outlive its listing. Hide saves whose listing is gone or no longer `AVAILABLE`.

## 3) Categories

iOS now offers: `Electronics`, `Furniture`, `Home Goods`, `Clothing`, `Books`, `Other`. The stored value is the label
as shown. Listings with older category values still appear under "All". Android can use the same list so filters match.

## 4) Seller phone is no longer copied automatically

iOS used to fill `sellerPhone` from the account phone number when the seller left it blank. Listings are readable by every
signed-in member, so iOS now publishes only a phone the seller typed in. Please do the same on Android.

## 5) Stricter listing rules

Both apps already write exactly these fields, so nothing should break. Keep new client code inside them.

- **Create allowlist:** `title`, `description`, `price`, `category`, `condition`, `sellerName`, `sellerId`,
  `sellerPhone`, `imageUrls`, `media`, `locationName`, `countryCode`, `latitude`, `longitude`, `status`, `timestamp`.
  Commerce fields (`buyerId`, `soldAt`, `stripeCommerceOrderId`) stay server-owned.
- **Create:** `status` must be `AVAILABLE` and `timestamp` must be the server timestamp, so a listing can't
  backdate or future-date itself to stay at the top of "Newest".
- **Size limits** (create and edit): title 1–200 characters, description up to 8000, category 60, location name 200,
  seller phone 40, seller name 120. `latitude` and `longitude` must be numbers when present.
- **`media`:** 1–20 entries, each `{url, type, name?}`. `type` is `image` or `video`, and `url` must be in the
  seller's own `marketplace_images/{uid}/` folder (the same check `imageUrls` already had). It's checked on create,
  and on edit whenever `media` changes.
- **Storage:** `marketplace_images/{uid}/` now also accepts videos (`video/*`, under 100 MB). Images stay under 25 MB.

## 6) Buying from someone who blocked you

`requestMarketplacePurchase` now refuses checkout with `permission-denied` ("This listing isn't available to you.")
when either the buyer or the seller has blocked the other. It checks both `blocked_users` and `blockedUsers`.
**Needs a functions deploy from Android.**

## 7) Checkout hardening (Nov 3) — needs a functions deploy

`requestMarketplacePurchase` → Stripe Checkout → webhook stays the only purchase path. Changes:

- **Open links are re-checked.** Calling `requestMarketplacePurchase` again used to hand back the old Stripe link
  without looking at the listing. It now re-checks availability, seller, blocks and price first. It returns the same
  link only if the quote is unchanged and the link has more than 2 minutes left; otherwise it expires the old session
  and creates a new one.
- **Marketplace links last 31 minutes** (Stripe's minimum is 30). Before, they lasted Stripe's default of 24 hours.
- **No new link while a payment is processing.** If the order is `PAYMENT_PROCESSING` (delayed methods such as bank
  debits), the callable fails with `failed-precondition` ("Your last payment for this is still processing…"). Before,
  a new session replaced the old one, and if the old payment cleared it was silently ignored.
- **Automatic refunds.** Nothing reserves the item during checkout, so two buyers can both pay. The first payment wins.
  These payments are now refunded to the card automatically (reversing the seller transfer and the platform fee):
  - the item was sold, removed or changed seller before payment completed (order becomes `REFUND_PENDING`, then `REFUNDED`);
  - a payment arrived through a replaced link, or a second payment arrived after the order was already paid (the
    order keeps its status).

  Each refund is logged in `stripe_commerce_refunds/{sessionId}`, which clients can't read. The buyer gets a
  `users/{uid}/transactions/stripe-refund-{sessionId}` receipt (`type: "INFO"`, `status: "REFUNDED"`,
  `source: "STRIPE_COMMERCE_REFUND"`). If Stripe rejects the refund, the order and the log go to
  `RECONCILIATION_REQUIRED` for support. Other order kinds (Garage Sale, ads, Blind Date, tickets) are unchanged.
- **`purchase_requests` is closed** (`allow create: if false`). The server already rejected every request there, since
  wallet custody is off. iOS was still writing to it and showing "Card checkout submitted", which was wrong. It now uses
  the callable like Android.

## 8) How iOS shows a purchase (optional for Android)

- The buyer confirms, `requestMarketplacePurchase` returns `checkoutUrl`, and iOS opens it in an in-app Safari sheet.
- When the sheet closes, iOS reads `marketplace_items/{itemId}` from the server for up to about 10 seconds:
  - `status == "SOLD"` and `buyerId == me` means "You bought this", with a "Message the seller" button;
  - `SOLD` to someone else, or deleted, means "Someone else bought this first… refunded automatically";
  - still open means "No payment confirmed yet."
- My listings now has **Live / Sold / Purchases** tabs. They query `marketplace_items` where `sellerId == me` or
  `buyerId == me`, and `status == "SOLD"`.

## 9) Feed query: filter on the server

Both apps loaded the newest 120 listings and then dropped sold and deleted ones on the phone, so the feed shrinks as
items sell. iOS now queries `where("status", "in", ["AVAILABLE","ACTIVE","OPEN"]).orderBy("timestamp", DESC)`. That
uses the existing `status` + `timestamp` index (already deployed). Please do the same in
`MarketplaceViewModel.kt` (around line 926).

## 10) Unchanged

- Purchases still go through the `requestMarketplacePurchase` callable.
- Chat still uses the chat invitation flow.
- The posting limit is unchanged (3 posts, plus Garage Shop).
- Photo analysis (suggested title and category, "Clean background") runs on the iPhone and writes nothing new to
  Firestore. The cleaned cover is uploaded like any other photo.

## Deploy order

Deploy `my-firebase-project/firestore.rules` and `storage.rules` before the iOS release. Without them, condition edits,
saves and listing videos are rejected. iOS shows a friendly message for saves in that case. Sections 6 and 7 need a
functions deploy (Android). Until then, purchases work, but two buyers who both pay need a manual refund in Stripe.
