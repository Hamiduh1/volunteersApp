# Ads → Bulletin board (iOS, Nov 3) — Android handoff

The Ads part of Sponsored is now a community bulletin board (Nextdoor / Citizen style), not a sponsored-campaign
gallery. Garage Sale is unchanged. iOS keeps the old photo grid as a second "Discover" tab.

## 1) New fields on `advertisements/{adId}`

Written by `postSponsoredAd` (server only; client creates are still blocked by rules).

| Field | Type | Notes |
|---|---|---|
| `adType` | string | `LOST`, `FOUND`, `NOTICE`, `DOCUMENT`, `AD`. Missing on legacy sponsored ads (show them as `AD`). |
| `documentState` | string | `LOST` or `FOUND`, only when `adType == DOCUMENT`. |
| `locationName` | string | Required, up to 200 characters. |
| `happenedAt` | timestamp or null | Date it was lost or found, or the event date. `AD` has none. |
| `timeNote` | string | Free text up to 60 characters ("Around 4 pm", "Mon–Sat, 9–6"). |
| `contactMethods` | string[] | Any of `CHAT`, `CALL`, `WHATSAPP`, at least one. Found documents are always `["CHAT"]`. |
| `durationDays` | number | `7` or `30`. |
| `expiresAt` | timestamp | Set by the server. Notices end the day after the event if that is sooner. |
| `status` | string | `ACTIVE`, or `RESOLVED` after the owner marks it found or resolved. |
| `resolvedAt` | timestamp | Set when resolved. |

## 2) Posting: `postSponsoredAd`

Send the new fields to get a bulletin ad. Requests without `adType` behave exactly as before (link and media
required, listing fee charged), so older Android builds keep working.

```
{
  adType, documentState?, title, description, locationName,
  happenedAtMillis?, timeNote, contactMethods, ownerPhone, targetUrl?, durationDays,
  media: [{url, type, name}], mediaUrls, paymentRail: "STRIPE_CARD"
}
```

- `LOST`, `FOUND`, `NOTICE` and `DOCUMENT` are free and go live immediately: `{success, adId, message}`.
  Each person can have 5 free posts up at a time.
- `AD` pays the existing listing fee: the response has `checkoutUrl`. Open it (Custom Tab); the ad appears after
  the Stripe webhook confirms payment. Staff stay exempt.
- Photos are optional and must be uploaded to `ads/{uid}/` first. Found documents can't have photos.
- `CALL` or `WHATSAPP` need `ownerPhone` with 7 to 15 digits. WhatsApp opens `https://wa.me/<digits>`, so ask for
  the country code.

Fixes an existing bug on iOS (maybe Android too): paid ads ignored the returned `checkoutUrl`, so nobody paid and
the ad was never created.

## 3) Editing and resolving (rules)

Owners may update `title`, `description`, `ownerPhone`, `targetUrl`, `media`, `mediaUrls`, `locationName`,
`timeNote`, `happenedAt`, `contactMethods` and `updatedAt`. Type, duration and `expiresAt` can't change. Found
documents must stay chat-only with no photos.

Mark as resolved: `update({status: "RESOLVED", resolvedAt: serverTimestamp()})`, owner only, only from `ACTIVE`.

## 4) Automatic deletion — needs a functions deploy

New scheduled function `expireBulletinAdsJob` (hourly) deletes ads whose `expiresAt` has passed and ads resolved
more than 24 hours ago, together with their files under `ads/{ownerId}/`. Legacy ads without `expiresAt` are left
alone. Until deployed, iOS hides expired ads but they stay in Firestore.

## 5) What iOS shows (please match)

- **List, not grid.** Each ad is its own card: type icon, badge, title, one line of description, map pin + place,
  date/time, contact icons, days left, share button. Optional small photo on the right.
- **Badges:** LOST red (filled), FOUND green (filled), NOTICE blue, DOCUMENT indigo (red/green when lost/found),
  AD orange. Resolved shows "FOUND ✓" or "RESOLVED" in green with the title struck through.
- **Filters:** All · Lost · Found · Notice · Document · Ad. Sections: "Urgent · lost recently" (unresolved lost
  posts from the last 72 hours), Today, This week, Earlier, Resolved.
- **Hide** ads whose `expiresAt` has passed.
- **Detail:** badge header, Where/When rows with "Open in Maps", description, photos, safety tip, and a bottom
  bar with only the contact methods the poster chose. Owners get Mark as found/resolved, Edit and Delete.
  Others get Share and Report (`user_reports`, category `Ads`, content type `bulletin_ad`).
- **Post flow (4 steps):** type → details (title, description, where, when, up to 4 photos) → contact → duration
  (7 or 30 days) with a live preview and the fee line.

## Deploy order

1. Firestore rules (`my-firebase-project/firestore.rules`) — needed for edit and resolve on new ads.
2. Functions (Android) — needed for posting new ads at all: until then, iOS posts fail because the old
   `postSponsoredAd` requires a link and media.

---

Card colours, garage tile grid and screen order: see `IOS_ANDROID_SPONSORED_CARDS.md`.
