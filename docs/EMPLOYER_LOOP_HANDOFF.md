# Employer loop handoff (iOS ↔ Android)

Audit + harden pass: Nov 2026. Closes unverified self-grant of employer + job posting.

## What changed (backend — deploy from Android `.env` machine)

1. **`enrollAsEmployer` callable** (App Check + `assertAccountVerified`)  
   Sets `users.role/userType/userRole = employer` and creates `employers/{uid}`.

2. **Firestore rules** (`my-firebase-project/firestore.rules`)  
   - `employers` **create: false** (Admin SDK / enroll only)  
   - `isEmployer()` = verified account **and** `users.role == employer` **and** `employers/{uid}` exists  
   - Catch-all `/{path=**}/applications` no longer applies when `path` is empty (stops loosening top-level `applications/{id}`)

3. **iOS** uses enroll after verify/login; no client `employers` create at signup; job create validates lengths + enrolled employer doc; edit no longer forces closed jobs active.

## Android must implement

### P0 — security

1. **Shell verification gate** in `MainActivity` / `MainViewModel` before any role home (including FCM / exported intents). Match iOS `SessionManager.verificationRequired`.

2. **Stop writing `employers/{uid}` from the client**  
   - Remove batch create in `SignUpViewModel`  
   - Remove login/profile self-heal creates  
   - After email/phone verify (and on login when `role == employer`), call:

```kotlin
FunctionsClient.callMap(
  CallableFunction.ENROLL_AS_EMPLOYER,
  mapOf(
    "organizationName" to companyName,
    "email" to email
  )
)
```

3. **Add `ENROLL_AS_EMPLOYER` / `enrollAsEmployer`** to your callable enum (same as iOS `CallableFunction.enrollAsEmployer`).

4. **Deploy** functions + rules together:

```bash
firebase deploy --only functions:enrollAsEmployer,firestore:rules
# or full functions + rules deploy
```

Until rules deploy, old clients can still self-create employers. Until functions deploy, new enroll calls fail.

### P1 — product parity

5. Job composer: require org name 2–120 chars; title/role/description non-empty with sane max lengths; refuse create if `employers/{uid}` missing (prompt re-verify / enroll).

6. Edit job: do **not** set `isActive=true` when status is closed/deleted.

7. Do not treat `mobileMoneyPhoneOtpVerifiedAt` as account unlock (see `AUTH_SIGNUP_PARITY.md`).

8. Prefer staying signed in after verify (iOS keeps session).

### P2

9. Auto-login: don’t `signOut` on every transient network error.  
10. Confirm catch-all applications change with rules emulator for `applications/{id}` and `jobs/{id}/applications/{id}`.

## Employer loop (expected after deploy)

| Step | Behavior |
| --- | --- |
| Create account (employer) | Auth + `users` with role employer + company fields; **no** `employers` doc yet |
| Verify email or profile phone | Required before home |
| Enroll | `enrollAsEmployer` → `employers/{uid}` |
| Home tabs | Home / Jobs / Requests / Wallet / Profile |
| Create job | Only if verified + enrolled; rules enforce `isEmployer()` |
| Forgot password | Shared callable `requestPasswordResetLink` (App Check + throttle) |

## Quick tests

- [ ] Unverified user cannot create `employers/{uid}` (PERMISSION_DENIED)  
- [ ] Unverified / volunteer cannot create `jobs/{id}`  
- [ ] Verified employer enroll → create job succeeds  
- [ ] Android FCM open while unverified → verification screen, not employer shell  
- [ ] Top-level `applications/{id}` forge/delete still blocked  
