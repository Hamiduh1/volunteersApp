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

Android (branch `feature/dating-loop-ios-parity`) writes `createdAt` only when the document has none and adds `updatedAt`.
Keep saving with `SetOptions.merge()` so iOS-only fields survive Android edits.

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
  exists, **accept it** (normal invitation-accept flow that creates the direct chat) instead of creating a new
  invitation. Android does this in `DateEvaViewModel.sendChatInvitation` (branch `feature/dating-loop-ios-parity`).
- **Match** = an accepted Dating Loop invitation in either direction. iOS shows an "It's a Match!" screen to both
  sides and a matches strip with "Send a message".
- **Likes You** = pending Dating Loop invitations received by the user, excluding passed/blocked/already matched
  people, Super Likes first.
