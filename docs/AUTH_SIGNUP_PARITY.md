# Auth / signup security parity (iOS ↔ Android)

Audit date: Nov 2026. Backend + iOS hardened in this pass. Android client still needs the gates below.

## What is already solid (both platforms + server)

- Email codes: 6 digits, 10-minute TTL, App Check, Auth email from `context.auth` (client email ignored).
- Phone OTP (Twilio Verify): attempt lockout (transactional), cooldown, daily send caps.
- Firestore: clients cannot write `emailVerified` / `phoneVerified` to true themselves.
- Finance callables use `assertAccountVerified` (Auth `emailVerified` **or** profile `phoneVerified`).

## Backend changes (deploy from Android machine)

1. **Email verify** — attempt counting is transactional (blocks parallel brute force). Daily send cap: 10/user/day. Hard-coded default HMAC secret removed (requires env secret).
2. **Phone OTP** — `purpose=ACCOUNT_PHONE` must match the profile phone. Other purposes (e.g. `MOBILE_MONEY_METHOD`) require an **already verified** account (stops SMS pumping from throwaway signups).
3. **Password reset** (`requestPasswordResetLink`) — 3 requests per email per hour.

## iOS changes (shipped / pending push)

- Account unlock ignores `mobileMoneyPhoneOtpVerifiedAt` (only `phoneVerified` / `phoneVerifiedAt`).
- Password policy matches Android: 8+ chars and ≥3 of upper/lower/digit/symbol.
- Login errors: single “Email or password is incorrect.” (no enumeration).
- Removed pre-login “Verify Email” (requires a session).
- `verifyEmailCode` no longer sends client email.

## Android handoff (do these)

### High — deep-link unverified access

1. `LoginActivity.handleAppLink` / `isUserLoggedIn()` must require email **or** profile `phoneVerified` (same as server), not just `currentUser != null`.
2. Run verification-aware `checkAutoLogin` **before** stream/app-link redirects.
3. Gate `MainActivity` / `MainViewModel` with a central check mirroring iOS `SessionManager` (`.verificationRequired`).

### Medium — align verification definition

4. Drop `mobileMoneyPhoneOtpVerifiedAt` from `isPhoneVerified` in `LoginViewModel` / `EmailVerificationViewModel`.
5. Keep using profile phone + `purpose=ACCOUNT_PHONE` for account unlock OTP.
6. Move forgot-password to callable `requestPasswordResetLink` (App Check + server throttle). Stop calling `sendPasswordResetEmail` directly.
7. Stop showing raw callable / Twilio messages to users; map to neutral copy.

### Low

8. Remove hard-coded owner email constant from the client (server already enforces).
9. After email/phone verify success, either keep the session and route like iOS, or document the “sign out then login again” Android behavior.

## Remaining known gaps (do not treat as done)

- Not every callable uses `assertAccountVerified` (applyForEvent, chat, ads, etc.). Crafted clients can use non-finance features unverified until those callables and/or Firestore rules gain the same check.
- `users` collection is broadly readable when signed in — email/phone harvesting risk for unverified accounts. Prefer private docs / callables for PII.
- Confirm Firebase Auth **email enumeration protection** is ON in the console.
- Confirm deployed rules are `my-firebase-project/firestore.rules` (not a stale root copy).

## Quick test plan

- [ ] Signup → must verify email code or **profile** phone OTP before home.
- [ ] Verify a funding MM number only → must **not** unlock home.
- [ ] Parallel wrong email codes → lock after 5 total, not 5×N.
- [ ] Unverified account cannot request MM method OTP (server).
- [ ] ACCOUNT_PHONE OTP to a non-profile number fails.
- [ ] Android: kill app after signup, open live deep link → verification screen, not stream.
- [ ] Forgot password spam → rate limit after 3/hour.
