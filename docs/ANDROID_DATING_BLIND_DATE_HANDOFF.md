# Android handoff — Dating Loop / Blind Date second-eye

Backend + iOS shipped Nov 2026. Deploy **functions + firestore rules** together from the Android `.env` machine.

## What changed

| Finding | Fix |
| --- | --- |
| Blind Date profiles readable by all dating users (payment/match leak) | Rules: peers only read `status in [active]`; owner can read own docs |
| Join without completed Dating Loop / delete leaves Blind Date active | `joinBlindDate` / `rejoinBlindDate` require completed Dating Loop; `leaveBlindDate` + delete trigger hide Blind Date |
| Blind Date invites without preference compatibility | Rules require `hasCompatibleBlindDateProfiles` |
| Super Like / Like client-only limits | Client Dating Loop like creates denied; use `sendDatingLike` (1 Super Like/day, 100 likes/day) |
| Media ownership repaired after write | Blind Date client media writes denied; `joinBlindDate` / `updateBlindDateProfile` validate owned Storage URLs |

## Android must implement

### P0

1. **Wire callables** (App Check):
   - `leaveBlindDate`
   - `updateBlindDateProfile` (`bio`, `gender`, `lookingFor`, `name`, `mediaUrls`)
   - `sendDatingLike` (`recipientId`, `superLike: Boolean`)

2. **Stop client writes** of Dating Loop likes into `users/{recipient}/invitations/{sender}` with Dating Loop context — rules will `PERMISSION_DENIED`.

3. **Stop client writes** of Blind Date `media` / `profilePictureUrl` on `blindDateProfiles/{uid}` — use `updateBlindDateProfile`.

4. **Leave Blind Date UI**: visible action when status is active; after success show “Profile hidden” and refresh discovery (status `left` → treat as not in pool).

5. **Delete Dating Loop profile**: call `leaveBlindDate` first (or rely on `onDatingProfileDeleteLeaveBlindDate`); matched members may need to finish the match before leave succeeds.

6. **Join Blind Date**: require a completed Dating Loop profile (name, bio, gender, lookingFor, owned photo). Surface the new `failed-precondition` messages.

### P1

7. Treat Blind Date discovery as active-only (already typical). Do not attempt to read other users’ `awaiting_payment` / `matched` docs.

8. Super Like: honor server `resource-exhausted` (“already used today’s Super Like”).

9. Deploy:

```bash
firebase deploy --only functions:leaveBlindDate,functions:updateBlindDateProfile,functions:sendDatingLike,functions:joinBlindDate,functions:rejoinBlindDate,functions:onDatingProfileDeleteLeaveBlindDate,firestore:rules
# or full functions + rules
```

## Quick tests

- [ ] Non-owner cannot read `awaiting_payment` Blind Date profile  
- [ ] Join without Dating Loop profile → failed-precondition  
- [ ] Leave Blind Date → hidden from pool; copy shows profile hidden  
- [ ] Incompatible Blind Date invite → PERMISSION_DENIED  
- [ ] Client Dating Loop like write → PERMISSION_DENIED; callable like works  
- [ ] Super Like twice same day → resource-exhausted  
- [ ] Foreign media URL on Blind Date update → rejected  
