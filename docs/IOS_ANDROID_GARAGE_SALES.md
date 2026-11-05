# Garage Sale modernisation (iOS, Nov 3): Android handoff

Garage sales now have a sale type, dates and daily hours, categories, and accepted payment methods. They come down
automatically after they end. iOS keeps the existing Shops and Discover layouts and adds day filters, Saved,
"Near me", a new detail page and a four-step posting flow.

**Android bug fixed by the new rules (no code change needed):** `garage_sales` used to deny every update, so
Android's edit (`AdvertisementViewModel` ~line 825) and delete (soft delete) failed with `PERMISSION_DENIED`. Both
writes are now allowed exactly as Android sends them, once the rules are deployed.

Android already pays through `requestGarageSalePurchase`. iOS was still writing the retired `garage_sale_payments`
documents, which the server rejects automatically; iOS now uses the callable too.

## 1) New fields on `garage_sales/{saleId}`

All of them are optional, so existing listings and older Android builds keep working.

| Field | Type | Notes |
|---|---|---|
| `saleType` | string | `GARAGE`, `YARD`, `MOVING`, `ESTATE`, `MULTI_FAMILY`, `COMMUNITY`. Missing means `GARAGE`. |
| `startsAt` | timestamp | First day at opening time. |
| `endsAt` | timestamp | Last day at closing time. Daily hours are the time of day of `startsAt` and `endsAt`. |
| `categories` | string[] | Up to 8 of `FURNITURE`, `CLOTHING`, `KIDS`, `ELECTRONICS`, `APPLIANCES`, `KITCHEN`, `TOOLS`, `BOOKS`, `SPORTS`, `DECOR`, `ANTIQUES`, `FREE`. |
| `paymentOptions` | string[] | Any of `CASH`, `IN_APP_CARD`, `MOBILE_MONEY`, `BANK_TRANSFER`. Missing or empty means in-app card (legacy behaviour). Show Pay only when it includes `IN_APP_CARD`. |
| `notes` | string | Up to 300 characters: rain date, "no early birds", parking. |
| `status` | string | `ACTIVE`, `ENDED` (seller ended it early) or `DELETED`. |
| `isDeleted` | bool | Soft delete, as Android already writes. |

`startsAt` and `endsAt` go together: send both or neither. Sales can run up to 14 days (iOS allows 7).

## 2) Rules (`my-firebase-project/firestore.rules`)

**Create** (owner only). Keys must be from: `title, description, shopName, contactName, contactPhone,
contactEmail, address, city, state, postalCode, latitude, longitude, media, ownerId, timestamp, saleType, startsAt,
endsAt, categories, paymentOptions, notes, status, updatedAt`. Android's current create already fits.

- Title 1–200 characters, description up to 4000, up to 20 media items (the apps use 120, 2000 and 8).
- `status` must be missing or `ACTIVE`. `endsAt` must be in the future, and `startsAt` less than 90 days ahead.

**Update** (owner only, not after soft delete). One of these three shapes:

1. **Edit** (sale still `ACTIVE`): any of the create keys except `ownerId`, `timestamp` and `status`, plus
   `updatedAt`. If the sale has dates, `endsAt` must still be in the future.
2. **End sale now**: only `status: "ENDED"`, `endsAt: serverTimestamp()` and `updatedAt`.
3. **Soft delete**: only `isDeleted: true`, `status: "DELETED"` and `updatedAt` (what Android sends today).

Hard deletes stay blocked, so the server can clean up Storage.

## 3) Automatic removal: `expireGarageSalesJob` (hourly)

Deletes the document and its media under `garage_sales/{ownerId}/` when:

- it is soft-deleted (`isDeleted == true`), on the next run;
- `endsAt` was more than 1 day ago (this includes sales ended early);
- it has no dates and was posted more than 60 days ago (legacy listings).

It writes a summary to `system/garage_sale_expiry`. Hide ended sales from buyers straight away rather than waiting
for the job. Sellers can still see their own until the job removes them.

## 4) Paying the seller: `requestGarageSalePurchase`

```
requestGarageSalePurchase({garageSaleId, amount})   // App Check enforced
→ {checkoutUrl, ...}
```

The buyer enters the total they agreed with the seller ($0.01–$50,000). Open `checkoutUrl` in a Custom Tab. The
server refuses sales that are deleted, ended (`status == ENDED` or `endsAt` passed), owned by the buyer, or whose
shop is closed. The seller needs a verified Stripe Connect payout account.

If the sale is deleted or changes owner between payment and the Stripe webhook, the buyer is now **refunded
automatically** (before, it needed manual reconciliation).

## 5) UI on iOS (for parity)

- **Feed** (Shops and Discover are unchanged): day chips All / Today / This weekend / Upcoming / Saved with
  counts, a "Near me" chip that sorts by distance, and search over title, description, area, type and categories.
  Sorted live first, then the soonest to open, then undated listings by newest.
- **Card**: a calendar block (month and day), a status badge ("Open now · until 2 PM", "Today · opens 8 AM",
  "Saturday · 8 AM", "Ended"), sale type, the first 3 categories, area and distance, and a thumbnail.
- **Detail**: photo pager, type and status, When (with "N-day sale"), Where with Directions, Good to know,
  What's for sale chips, Pay with chips, and a safety note. Bar for buyers: Chat, Call (if there's a phone), Pay
  (if `IN_APP_CARD` is accepted, the shop is open and the sale hasn't ended). Bar for the seller: Edit, End sale
  now, Delete. Toolbar: Save (stored on the device), Share, Report (`user_reports`, `contentType: garage_sale`).
- **Post or edit** (4 steps): (1) type and When: first day, last day, opens and closes times, note;
  (2) title, description, categories, up to 8 photos and videos; (3) "Use my current location" (pins
  lat/lng and fills the address), address, name, shop name, phone, email, payment toggles; (4) preview and
  fee note. Posting is free.

## 6) Deploy order

1. Firestore rules (fixes Android edit and delete immediately).
2. Functions: `expireGarageSalesJob` and the updated `requestGarageSalePurchase` plus checkout fulfillment (from the
   Android machine, together with the other pending functions).
