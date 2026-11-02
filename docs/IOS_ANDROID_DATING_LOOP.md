# iOS + Android Dating Loop Contract

Last updated: 2026-10-12

iOS shipped a redesigned Dating Loop (swipe deck, mutual matches, richer profiles, Super Likes, Likes You) in
VolunteersAppiOS commit `12f7898`. This note lists the Firestore data it reads/writes so Android can render it and stay
compatible. All new fields are optional; profiles without them keep working on both platforms.

## 1) `dating_profiles/{uid}` — new public fields

| Field | Type | Notes |
| --- | --- | --- |
| `age` | int | Derived from the private birthdate (section 2). 18–120 enforced by rules. Never store `birthDate` here. |
| `relationshipGoal` | string | One of `LONG_TERM`, `LONG_TERM_OPEN_TO_SHORT`, `SHORT_TERM_OPEN_TO_LONG`, `SHORT_TERM`, `NEW_FRIENDS`, `FIGURING_OUT`. Parse case-insensitively; ignore unknown values. |
| `interests` | list of strings | iOS caps at 5 (rules allow 10). Free text, picked from a fixed catalog on iOS. |
| `prompts` | list of maps `{question, answer}` | iOS caps at 3 (rules allow 5), answers ≤ 150 chars. Skip entries with an empty answer. |
| `jobTitle` | string | ≤ 60 chars on iOS (rules allow 80). |
| `school` | string | ≤ 60 chars on iOS (rules allow 80). |
| `updatedAt` | timestamp | Written on every save. |
| `createdAt` | timestamp | **Written only when the profile is first created.** |

Android currently resets `createdAt` on every save; please change it to write `createdAt` only when the document does not
exist yet (and keep saving with `SetOptions.merge()` so iOS-only fields survive Android edits).

Display: iOS shows `"Name, 27"` when `age >= 18`, the goal as a chip, and interests with ones shared with the viewer first.

Rules (`isSafeDatingProfilePayload`) reject `phone` / `birthDate` keys and wrongly typed or oversized values for the fields
above.

## 2) `users/{uid}/dating_private/*` — owner-only

- `contact` doc: `phone` (optional) and `birthDate` (timestamp, required by iOS before saving a dating profile; user must
  be 18+). Written with merge; clearing the phone deletes only the `phone` field. Android signup already stores
  `users/{uid}.birthDate`, which can pre-fill this.
- `swipes` doc: `passedIds` (list of uids the user passed on) + `updatedAt`. iOS hides these from the deck and syncs across
  devices. Android can read/write the same doc to share pass history.

Deleting the dating profile should also delete `dating_private/swipes`.

## 3) Likes, Super Likes and matches (`users/{recipient}/invitations/{sender}`)

A "like" is still the existing Dating Loop chat invitation:

```json
{
  "senderId": "<uid>",
  "senderName": "...",
  "senderProfilePicUrl": "...",
  "status": "pending",
  "timestamp": "<server timestamp>",
  "context": "Dating Loop Profile"
}
```

- **Super Like** = same invitation with `context: "Dating Loop Super Like"`. Detect it with a case-insensitive
  `contains("super like")`. It still contains "dating loop", so existing Android parsing treats it as a normal like.
  iOS allows 1 Super Like per day (counted from sent invitations' `timestamp`), free for everyone.
- **Mutual like:** before sending, check `users/{me}/invitations/{them}`. If a pending Dating Loop invitation from them
  exists, **accept it** instead of creating a new invitation: set `status: "accepted"` and `lastUpdatedAt` on that
  doc. **Do not create a chat** (see section 5). Android does not do this yet, so two people liking each other can end
  up with crossing pending invites.
- **Match** = an accepted Dating Loop invitation in either direction. iOS shows an "It's a Match!" screen to both
  sides and a matches strip; both lead to Blind Date ("Chat on Blind Date").
- **Likes You** = pending Dating Loop invitations received by the user, excluding passed/blocked/already matched
  people, Super Likes first.

## 4) Catalog: interests, prompt questions and relationship goals

Android must use exactly the same strings as iOS; they are compared as plain text (shared interests, prompt questions).

- Kotlin: `app/src/main/java/com/example/volunteersApp/date/DatingProfileCatalog.kt` (`DatingRelationshipGoal`,
  `DatingProfileCatalog.INTERESTS`, `DatingProfileCatalog.PROMPT_QUESTIONS`, limits).
- iOS source of truth: `VolunteersAppiOS/Features/Shared/Date/DatingProfileCatalog.swift`.
- Readable list: [`docs/DATING_LOOP_CATALOG.md`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/main/docs/DATING_LOOP_CATALOG.md)
  and JSON: [`docs/dating_loop_catalog.json`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/main/docs/dating_loop_catalog.json)
  in the iOS repo.

If either platform adds or renames an entry, update all three in the same change.

## 5) Blind Date is the paid chat for Dating Loop matches

Section 4 (shared catalog) lives on branch `feature/dating-loop-ios-parity`.

Product model (iOS, Oct 2026):

- **Dating Loop is free:** browse, like, Super Like, and comment. Liking each other is a match, but **no chat opens**.
- **Blind Date is paid:** chatting happens only there. **Both people need an active pass.** One pass covers one match;
  once a chat opens, the pass is used and the user pays again (`rejoinBlindDate`) to chat with someone else.
- People can also join Blind Date directly and invite members from the Blind Date pool, as before.
- The fee is `app_config/fee_settings.blindDateFeeUsd` (admin dashboard), read through `getPublicCommerceFeeSettings`.

### What Android needs to change

1. **No free chat for Dating Loop matches.** `DateViewModel.acceptMutualDatingLike` and
   `InvitationsViewModel.acceptInvitation` (for invitations whose `context` contains "dating loop") must only mark the
   invitation accepted (`status: "accepted"`, `lastUpdatedAt`). Show "It's a match! Open Date Hub > Blind Date to
   chat." Chats that already exist between matched users keep working.
2. **Match screen / matches strip / profile:** the button becomes "Chat on Blind Date" and opens Blind Date. Open the
   chat directly only if one already exists.
3. **Comments** (below): a comment button on the swipe deck and profile; show received comments in Likes You and on
   the sender's profile with "Like back"; reply only happens on Blind Date.
4. **Blind Date screen:** list Dating Loop matches with the per-pair state below, plus comments, invites, and the pool.
5. **Payment payloads** (below). Android's current Blind Date payment already uses `paymentRail`; keep it, and allow
   joining with the Dating Loop photo URLs as `mediaUrls`.

### Comments: `users/{owner}/dating_comments/{senderId}`

One doc per sender (doc id = sender uid; sending again overwrites it).

```json
{
  "senderId": "<uid>",
  "senderName": "Ada",
  "senderPhotoUrl": "https://...",
  "text": "1-300 chars",
  "createdAt": "<server timestamp>",
  "updatedAt": "<server timestamp>"
}
```

- Rules: only the sender creates or updates it, with exactly these keys. `createdAt` and `updatedAt` must be server
  timestamps. Both users need a dating profile, and neither may have blocked the other. Only the owner can list; the
  sender can read their own doc; either side can delete it (the owner deletes to dismiss).
- Sending a comment also sends a like (section 3). If they already liked you, that like-back is a match.
- Only the owner sees comments. "Reply" = like back (creates the match), then chat on Blind Date.

### Per-match state on the Blind Date screen

For each Dating Loop match, evaluated in this order:

| Condition | State | Action |
| --- | --- | --- |
| An accepted/matched Blind Date invitation with `matchedChatId` exists between the two (either inbox), or a legacy Dating Loop chat exists | Chat ready | Open chat |
| My `blindDateProfiles/{me}.status` is `awaiting_payment` | Payment processing | Wait for the status change |
| My status is missing, `matched`, or `expired` | Needs my pass | Pay: `joinBlindDate` if I have no profile, `rejoinBlindDate` if `matched` |
| A pending invite from them in `users/{me}/blindDateInvitations` | They're waiting | `acceptBlindDateInvitation({senderId})`, which returns `chatId` |
| My pending invite in `users/{me}/blindDateSentInvitations/{them}` | Request sent | Wait |
| Their `blindDateProfiles/{them}.status == "active"` | Both have a pass | Send the existing Blind Date invitation |
| Otherwise | Waiting for their pass | Nothing |

Accepting the invite creates the chat and sets both profiles to `matched`, which uses both passes.

### Payment payloads

- `joinBlindDate`: `{mediaUrls: [String] (non-empty), bio, gender: MALE|FEMALE|OTHER, lookingFor: MEN|WOMEN|EVERYONE,
  paymentRail: "STRIPE"|"MOBILE_MONEY", paymentMethodId?}`.
- `rejoinBlindDate`: `{paymentRail, paymentMethodId?}`.
- **Stripe** returns `{checkoutUrl, orderId, pending}`. Open `checkoutUrl` in the browser; the webhook sets the profile
  to `active`.
- **Mobile money** returns `{orderId, status, pending, message}`. The user approves the prompt on their phone, and the
  profile becomes `active` when it settles. `getPlatformFeeMobileMoneyOptions({orderId})` reports the order status.
- Use `getPlatformFeeMobileMoneyOptions({kind: "BLIND_DATE_JOIN"})` for the eligible method `id` and
  `estimatedLocalAmount` + `currency`. If `enabled` is false, offer card only.
- Staff-exempt users, or a fee of 0, activate immediately (`charged: false`).
- iOS listens to `blindDateProfiles/{me}` and both Blind Date invitation inboxes so payments and partner actions show up
  live.

### iOS reference code

These links are pinned to VolunteersAppiOS commit `249bab1`, so they keep showing this version.

| What | iOS file |
| --- | --- |
| Blind Date screen and paywall sheet | [`BlindDateHomeView.swift`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/BlindDateHomeView.swift) |
| Per-match state (table above) | [`DateHubViewModel.blindPairStage`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DateHubViewModel.swift#L170), [`BlindDatePairStage`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DatingLoopInviteState.swift) |
| Pay (join vs rejoin), start or accept a chat | [`payForBlindDate`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DateHubViewModel.swift#L1020), [`startBlindDateChat`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DateHubViewModel.swift#L1116) |
| Mutual like without a chat | [`acceptMutualDatingLike`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DateHubViewModel.swift#L472), [`DateRepository.acceptDatingLoopLike`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L561) |
| Social Inbox accept without a chat | [`ChatRepository.isDatingLoopInvitation`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/ChatRepository.swift#L1807) and its use in `acceptInvitation` |
| Comments: send, observe, dismiss | [`DateRepository.sendDatingComment`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L495), [`observeReceivedDatingComments`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L525), [`DateHubViewModel.sendDatingComment`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DateHubViewModel.swift#L1148) |
| Comment composer and comment card | [`DatingCommentViews.swift`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DatingCommentViews.swift) |
| Comment button on the swipe deck and profile | [`DatingSwipeDeckView.swift`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DatingSwipeDeckView.swift), [`DatingMemberProfileView.swift`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DatingMemberProfileView.swift) |
| Match screen "Chat on Blind Date" | [`DatingMatchViews.swift`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DatingMatchViews.swift) |
| Payment requests and mobile money options | [`joinBlindDate`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L695), [`rejoinBlindDate`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L733), [`fetchBlindDatePaymentPreview`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L627) |
| Overview, live listeners, partner statuses | [`fetchBlindDateOverview`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L356), [`observeBlindDateChanges`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L459), [`fetchBlindDateStatuses`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Core/Repositories/DateRepository.swift#L480) |
| Limits (comment length 300) and catalog | [`DatingProfileCatalog.swift`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/VolunteersAppiOS/Features/Shared/Date/DatingProfileCatalog.swift), [`DATING_LOOP_CATALOG.md`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/docs/DATING_LOOP_CATALOG.md), [`dating_loop_catalog.json`](https://github.com/Hamiduh1/VolunteersAppiOS/blob/249bab1/docs/dating_loop_catalog.json) |

### Server changes in this commit (not deployed yet)

- `acceptBlindDateInvitation` also writes `matchedChatId`, `matchedPartnerId`, `matchedAt`, and `updatedAt` on both
  profiles.
- `rejoinBlindDate` keys its order per match (`<uid>:<matchedChatId>`). Before this, the order was keyed per user, so
  every rejoin after the first reused the paid order and was free.

### Not enforced yet

Rules still let a client create a direct chat from a pending Dating Loop invitation (`canCreateDirectChatFromInvitation`),
so old clients can keep opening free chats. Once Android ships item 1, tighten that rule for invitations whose `context`
contains "dating loop".

**Update:** enforced. `hasPendingChatInvitation` ignores `users/{uid}/invitations` docs whose `context` contains
"dating loop", so a pending Dating Loop like no longer authorizes creating a direct chat. Legacy `chat_invitations`
docs are unchanged.

## 6) Push notifications for dating activity

Three Firestore triggers in `my-firebase-functions/src/index.ts` send the push and write an in-app row to
`users/{recipient}/notifications/{type}_{actorId}`. Both apps get them with no client change. Android only needs to
route taps and show the settings toggle.

| `data.type` | Sent to | When | Trigger |
|---|---|---|---|
| `dating_like` | the liked user | Dating Loop invitation becomes `pending` | `onDatingLoopInvitationWriteNotify` |
| `dating_super_like` | the liked user | same, `context` contains "super like" | `onDatingLoopInvitationWriteNotify` |
| `dating_match` | the original liker | that invitation becomes `accepted` | `onDatingLoopInvitationWriteNotify` |
| `dating_comment` | the profile owner | comment created, or text changed more than 1 hour after the last edit | `onDatingCommentWriteNotify` |
| `blind_date_invite` | the invited user | `blindDateInvitations` doc becomes `pending` | `onBlindDateInvitationWriteNotify` |
| `blind_date_match` | the inviter | that invite becomes `accepted` | `onBlindDateInvitationWriteNotify` |
| `blind_date_match` | the other match | `startBlindDateMatchChat` opened their chat | `startBlindDateMatchChat` |
| `blind_date_partner_joined` | each Dating Loop match | a Blind Date profile becomes `active` (join or rejoin) | `onBlindDateProfileActivatedNotifyMatches` |

- Payload `data`: `type`, `actorId`, `referenceId` (actor uid; `matchedChatId` for `blind_date_match`), `receiverId`.
- Invitation pushes only fire when `context` contains "dating loop", so other chat invitations are untouched.
- Skipped when either user blocked the other (`settings/chat_privacy.blockedUserIds`) or when the recipient set
  `users/{uid}/settings/notifications.datingActivity` to `false` (missing means on).
- Tap routing on iOS: `dating_like`, `dating_super_like` and `dating_comment` open Dating Loop; `dating_match` and
  every `blind_date_*` type open Blind Date, because a match chats there (`Core/Services/DatingPushRoute.swift`).

## 7) Match to chat, private birth date, safer names (Nov 2026)

Server and rules changes are in the backend repo. Functions need an Android-side deploy; the Firestore rules deploy
after the owner approves. Old Android builds keep working, apart from the login age check in item 2.

**Deploy order matters.** Deploy `firestore.rules` before these functions. The functions move `birthDate` off the root
doc, and the old `userCanAccessDating` rule requires a root `birthDate`, so migrated users would lose dating access
under the old rules. iOS builds before this change also gate dating on a root `birthDate`, so ship the new iOS build
before (or close to) the functions deploy.

### 1. Liking each other is enough to open the Blind Date chat

- New callable `startBlindDateMatchChat({partnerId})` returns `{success, chatId, otherUserId}`.
  - It requires an accepted Dating Loop like in either direction, an active pass for both people, no block, and dating
    eligibility for both.
  - It respects the 3-day rejoin hold on both sides.
  - It creates the same `source: "blind_date"` direct chat that `acceptBlindDateInvitation` creates, marks both
    profiles `matched`, closes any pending Blind Date invite between them, and pushes `blind_date_match` to the partner.
  - Calling it again for the same pair returns the existing chat.
- iOS calls it from "Start chat" (the ready-to-start stage) and from "Start chatting" on the match screen. If the
  function isn't deployed yet (`NOT_FOUND`), iOS falls back to the old invite. Suggested for Android `startPairChat`:
  in the ready-to-start stage, call this instead of `sendInvitation(uid)` when the partner is a Dating Loop match, and
  open the returned chat.

### 2. The account birth date leaves the public `users/{uid}` doc

- `birthDate` and `dateOfBirth` are now private profile fields in `user_private/{uid}`.
  - `moveNewUserBirthDateToPrivate` (users onCreate) moves them right after sign-up.
  - `migrateUserPrivateProfileFieldsJob` (state doc `system/user_private_profile_migration_v3`) moves existing users.
  - `ensureDatingEligibility` also moves any root copy it finds.
- In their place the root doc gets `birthDateOnFile: true` plus `canAccessDating`, `isMinor` and
  `requiresParentalConsent` derived from the real date. Clients can't write `birthDateOnFile` or
  `datingEligibilityVerifiedAt`.
- Rules: `userCanAccessDating` accepts `birthDateOnFile == true` in place of a root `birthDate`.
- `getCurrentUserPrivateFlags` now returns `birthDateMs` (the caller's own birth date in milliseconds, or `null`).
- **Android action, needed before the migration runs:** `general/LoginViewModel.kt` around line 162 computes `isMinor`
  from `userDoc.getTimestamp("birthDate")`. With the root copy gone that becomes `false`, so a minor would not be age
  restricted at login. Read the server-derived `isMinor` / `requiresParentalConsent` flags when `birthDate` is absent,
  or call `getCurrentUserPrivateFlags` for `birthDateMs`.
- `DateViewModel.loadPrivateDatingState` falls back to `users/{uid}.birthDate` when `dating_private/contact` has none.
  After the move, use `getCurrentUserPrivateFlags.birthDateMs` for that fallback.
- The dating access resolver already reads the flags, so it needs no change.

### 3. Names and photos must match the sender's real profile (rules)

- **Blind Date invites** (`users/{recipient}/blindDateInvitations/{sender}`, create and resend):
  - Only these keys may be written: `senderId`, `senderName`, `senderProfilePictureUrl`, `recipientId`,
    `recipientName`, `recipientProfilePictureUrl`, `status`, `matchedChatId`, `sentAt`, `updatedAt`, `respondedAt`.
  - `matchedChatId` must be blank.
  - `senderName` must equal `blindDateProfiles/{sender}.name`. A blank profile name allows `""` or "Anonymous User".
  - `senderProfilePictureUrl` must be blank or equal that profile's `profilePictureUrl`.
  - Recipient fields follow the same pattern; a blank name allows `""` or "Unknown User".
  - Android's current payload passes these checks.
- **Dating comments:**
  - `senderName` must equal the sender's `dating_profiles` name (trimmed is fine). A blank name allows `""` or
    "Member".
  - `senderPhotoUrl` must be blank or one of that profile's photos (`imageUrls`, `images`, `photos`, `mediaUrls`
    or `imageUrl`).
  - Android's current payload passes these checks.
- The Dating Loop like push no longer uses the invitation's client-written `senderName`; it reads `users/{uid}`.

### 4. Group chats can't include someone with a block against the creator

- `removeBlockedMembersFromNewGroupChat` (chats onCreate, `chatType == "group"`) removes every participant who blocked
  the creator or whom the creator blocked. It deletes the chat if fewer than two people remain.
- No client change is needed. Android's contact picker could also hide people who blocked the user.

### 5. Messages that walk both people to the chat

| Moment | Who | Message |
|---|---|---|
| Like received | owner with an active pass | "Like them back to accept, then start chatting on Blind Date." |
| Like received | owner without a pass | "Like them back to accept, then join Blind Date to start chatting." ("get a new Blind Date pass" if their pass is used) |
| Like accepted | liker; owner has a pass and liker has one | "{owner} accepted your like. Open Blind Date to start chatting." |
| Like accepted | liker; owner has a pass, liker doesn't | "{owner} accepted your like. Join Blind Date to start chatting with {owner}." |
| Like accepted | liker; owner has no pass | "You and {owner} liked each other. We'll tell you when {owner} is on Blind Date so you can chat." |
| A match joins or rejoins Blind Date | each match | "{name} joined Blind Date" / "Open Blind Date to start chatting with {name}." or "Join Blind Date to start chatting with {name}." |

Android should route the new `blind_date_partner_joined` type, and preferably `dating_match`, to the Blind Date
matches pane.

### 6. Joining Blind Date no longer dead-ends

- A leftover profile no longer blocks a new join. This covers expired, unknown or legacy statuses, and an
  abandoned payment that has no open checkout or collection.
  - Before this change, all of these failed with "You already have an active Blind Date profile."
  - An open payment now says "Your earlier Blind Date payment is still processing...".
  - An active pass says "Your Blind Date pass is already active."
- Photo links the user doesn't own (see item 7) are dropped instead of failing the whole join. The join fails only when no valid photo remains.

### 7. Members can't show someone else's photos, and payment IDs stay private

- **Photo ownership.** A photo link counts as the user's own only when its storage object path is
  `blind_date_media/{uid}/…`, `dating_images/{uid}/…` or `profile_images/{uid}/…` in this project's bucket. The
  check parses the object path, so text in the query string can't fake it.
  - `onDatingProfileWriteDropForeignMedia` (`dating_profiles/{uid}` writes) removes any other link from `imageUrls`,
    `images`, `photos`, `mediaUrls` and `media`. It replaces a foreign `imageUrl`, `profilePictureUrl` or
    `profileImageUrl` with the first photo the user owns, or `""`.
  - `onBlindDateProfileWriteDropForeignMedia` does the same for `blindDateProfiles/{uid}`. Without these triggers, a
    member could paste another member's photo links into their profile and pass the invite and comment photo rules
    in item 3.
  - Android already uploads to these folders, so it needs no change. A link from any other folder or site (an old
    upload path, a Google profile photo) disappears from the profile on its next write.
- **Payment IDs.** `blindDateProfiles` is readable by every dating member. Stripe and mobile money fulfillment no
  longer copy `stripeCheckoutSessionId`, `stripePaymentIntentId`, `providerTransferId` or
  `mobileMoneyFeePayoutRequestId` onto it. Those IDs stay on the order and on `users/{uid}/transactions`. The trigger
  above deletes old copies the next time a profile is written. No client reads them from the profile.
- **Suggested for Android (not enforced):** `chat/InvitationsViewModel.kt` shows the invitation's own
  `senderProfilePicUrl`, which the sender writes. Prefer the sender's live `users/{uid}` photo, as iOS does, so a
  forged invite photo isn't displayed.
