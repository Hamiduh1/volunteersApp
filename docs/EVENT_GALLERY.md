# Event Gallery (iOS live + Android handoff)

## What it is

Event Gallery is a shared photo feed where members post photos from events they hosted or volunteered at. Photos are visible to every signed-in member. It is **not** a private album and **not** a marketplace listing.

## How it works (member flow)

1. Open **Community Hub > Event Gallery**.
2. Browse the photo grid. Filter by event name or search for an event or member.
3. Tap **+** to add photos. First-time posters agree to Community Guidelines.
4. Enter the **event name** (reuse an existing name so photos stay grouped), choose up to **10 photos**, then **Upload**.
5. Open a photo to share it, delete your own, or **Report** / **Block** another member. Platform admins can delete any photo.

## Data model

- Firestore collection: `galleryUploads`
  - `name` (event name, 1–80 characters)
  - `imageUrl`, `imagePathInStorage`
  - `uploaderId`, optional `uploaderName`
  - optional `eventId`
  - `timestamp`
- Storage path: `gallery_uploads/{uid}/{uuid}.jpg` (images only, under 25 MB)

## Rules (deploy from `my-firebase-project` when the user says go)

- Any signed-in member can read.
- Create: own `uploaderId`, path under `gallery_uploads/{uid}/…`, HTTPS Storage URL, name length limits.
- Delete: the uploader, or a platform admin (`owner` / `admin`).
- Updates are not allowed.

## iOS (done)

- Entry: Community Hub card **Event Gallery**.
- Files under `Features/Community/Gallery/` and `Core/Repositories/GalleryRepository.swift`.
- Report reason category is `Gallery` (`gallery_photo`).

## Android changes

Android already has a gallery Activity and ViewModel, but nothing opens them.

Files:

- `app/.../ui/gallery/GalleryActivity.kt`
- `app/.../ui/gallery/GalleryScreen.kt`
- `app/.../ui/gallery/GalleryViewModel.kt`
- `app/.../models/ImgUpload.kt`

Do this:

1. Add an **Event Gallery** entry on Community Hub that starts `GalleryActivity`.
2. Keep the event-name field and photo pick; allow up to 10 photos per upload.
3. Save optional `uploaderName` (display name) with each document so the feed doesn't show a raw UID.
4. Let the uploader delete their own photo (delete the Firestore doc, then the Storage object). Admins can delete any photo if the client role matches `owner` / `admin`.
5. Add Report and Block on other members' photos, using the same safety flow as MindLoom / Live.
6. Hide photos from members the viewer has blocked.
7. Show Community Guidelines before the first upload.

## Related: ad listing fee by mobile money

The backend `postSponsoredAd` callable already collects the Ad listing fee by card **or** mobile money when:

- Owner Fee Settings sets a fee above $0
- first-party mobile money fee collection is enabled
- the member has a verified mobile money method in a live deposit country

iOS "Post an ad" now offers **Card** and **Mobile money** when the fee applies. Android should send `paymentRail: "STRIPE"` or `"MOBILE_MONEY"` plus `paymentMethodId` for mobile money. Older clients that sent `STRIPE_CARD` / `AFRIEX_MOBILE_MONEY` still work after the backend alias fix.
