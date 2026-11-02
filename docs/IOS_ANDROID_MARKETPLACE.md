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

## 7) Unchanged

- Purchases still go through the `requestMarketplacePurchase` callable.
- Chat still uses the chat invitation flow.
- The posting limit is unchanged (3 posts, plus Garage Shop).
- Photo analysis (suggested title and category, "Clean background") runs on the iPhone and writes nothing new to
  Firestore. The cleaned cover is uploaded like any other photo.

## Deploy order

Deploy `my-firebase-project/firestore.rules` and `storage.rules` before the iOS release. Without them, condition edits,
saves and listing videos are rejected. iOS shows a friendly message for saves in that case. The block check in section 6
needs a functions deploy (Android).
