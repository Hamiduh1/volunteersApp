# Phase 26 Final Hardening (Build + QA + Release Prep)

Use this checklist on Mac/Xcode before TestFlight submission.

## 1) Build-Fix Pass (Xcode)
- Clean build folder in Xcode.
- Build on latest iOS simulator target.
- Build on one physical device target.
- Archive once for `Any iOS Device`.

Recommended CLI checks:
```bash
xcodebuild -scheme VolunteersAppiOS -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16' build
xcodebuild -scheme VolunteersAppiOS -configuration Release -destination 'generic/platform=iOS' archive
```

Script helper:
```bash
chmod +x ios-migration/scripts/phase26_build_check.sh
./ios-migration/scripts/phase26_build_check.sh VolunteersAppiOS "iPhone 16"
```

## 2) Firebase Contract Validation
- Confirm `GoogleService-Info.plist` is from project `volunteersapp-968b2`.
- Confirm Auth sign-in/out works on simulator and device.
- Confirm Firestore reads for:
  - `users/{uid}`
  - `events`
  - `jobs`
  - `applications` and subcollection `applications`
- Confirm Storage upload for `profile_images/{uid}/...`.
- Confirm Functions:
  - `initiateTransfer`
  - `getSecureExchangeRate`
  - `createBeneficiaryVerification`
  - `getAgoraRtcToken`

## 3) End-to-End QA Matrix
- Volunteer:
  - Events list -> detail -> apply -> My Activity reflects status.
  - Jobs list -> detail -> apply -> My Activity reflects status.
  - Wallet summary/history/send-money opens and submits safely.
- Organizer:
  - Hosted events list/create/edit/delete works.
  - Applications review loads across pending/approved/rejected.
  - Approve/reject updates propagate to volunteer-facing status.
- Employer:
  - Posted jobs list/create/edit/delete works.
  - Applications review loads for all/pending/approved/rejected.
  - Approve/reject updates propagate to volunteer-facing status.
- Shared tools:
  - Chat list/detail loads, send message works.
  - Call history list loads.
  - Live sessions list/join token flow works.
  - Privacy/Terms/Support/AI screens open with fallback content if cloud docs unavailable.
- Owner/Admin:
  - Dashboard, payout queue, support console, reports, KYC, fee/system config screens load.

## 4) Reliability & Regression
- Toggle airplane mode and verify user-friendly network errors.
- Re-login after token expiration and verify session recovery.
- Verify permission denied errors surface cleanly (not raw crashes).
- Validate no duplicate application updates on rapid-tap approve/reject.
- Confirm pull-to-refresh works on key screens.

## 5) Observability
- Ensure Crashlytics and Analytics are enabled in Release build.
- Verify no critical runtime errors in Xcode console during smoke tests.
- Capture baseline logs/screenshots for:
  - Wallet transfer
  - Application approval
  - Profile update
  - Live join

## 6) TestFlight Readiness
- App icon and launch assets finalized.
- Privacy nutrition labels prepared.
- Terms/Privacy links verified in-app.
- Test account set prepared for all roles:
  - volunteer, organizer, employer, owner/admin.
- Upload release notes with known limitations.

## 7) Go / No-Go
Go only if:
- No blocker crash in role-critical loops.
- Firebase permission/index errors are resolved or safely handled.
- All role QA paths pass on at least one device.
- Archive and TestFlight upload succeed.
