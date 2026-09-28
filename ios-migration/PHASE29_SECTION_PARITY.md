# Phase 29: iOS vs Android Section Parity Audit

Date: 2026-03-16
Branch: `ios-migration-starter-v3`

## 1) Contract/Data Structure Parity

Completed in this phase:
- Expanded `ios-migration/starter/FirebaseContract.swift` to include Android-used aliases/legacy names:
  - Collections: `jobPosts`, `event_applications`, `settings`, `galleryUploads`, `userReports`, `blindDateProfiles`, `blindDateInvitations`, `blindDateSentInvitations`, `dating_profiles`
  - Subcollections: `blindDateSentInvitations`, `event_applications`
  - Storage folders: `gallery_uploads`
- Updated `ios-migration/FIREBASE_CONTRACT.md` with the same legacy/active structure references.
- Added audit script: `ios-migration/scripts/android_ios_contract_diff.ps1`
- Hardened community create loops for iOS starter:
  - MindLoom now writes/reads Android-compatible paths (`users/{uid}/jokes` + `collectionGroup("jokes")`).
  - MindLoom create flow supports text, image, video, and document uploads.
  - Sponsored Ads creation now mirrors Android wallet-debit transaction behavior and media payload shape.
  - Garage Sale creation now mirrors Android fields and media upload flow.
- Added community UX alignment updates:
  - MindLoom feed now supports `Following` vs `All Posts` filtering and dedicated composer sheet flow.
  - Sponsored/Garage section now uses a persistent bottom action CTA for create flow parity style.
  - Applied final pixel-parity tuning for community:
    - MindLoom now mirrors Android `For You` / `Following` labeling, dark feed style, creator strip, and floating create CTA.
    - Ad/Garage create screens now use Android-like section hierarchy and horizontal media-card picker UX.

Validation result:
- `android_ios_contract_diff.ps1` currently reports no missing collection/function/storage names between extracted Android references and iOS contract enums.

## 2) Auth/Access Parity

Already completed (previous + current updates):
- Staff login mode on iOS login.
- Role-based login selection parity (`volunteer`, `organizer`, `employer`, `admin`, `associate`).
- Resend verification email with cooldown behavior.
- Verification code entry flow (`EmailVerificationView`).
- Signup account-type picker with role-specific fields.
- Volunteer 18+ birthdate validation parity.
- Session gating for unverified email users.

## 3) Section-by-Section Feature Parity Snapshot

Status legend:
- `Aligned`: iOS starter section exists with matching backend structure.
- `Partial`: section exists but needs deeper behavior parity pass.
- `Missing`: Android section exists, iOS section not yet migrated.

Current snapshot:
- `Auth`: Aligned
- `Volunteer Events/Jobs/My Activity`: Partial
- `Organizer`: Partial
- `Employer`: Partial
- `Wallet/Payments`: Partial
- `Chat/Calls/Live`: Partial
- `Profile/Support`: Partial
- `Admin/Owner`: Partial
- `Community (MindLoom/Marketplace/Sponsored/Garage)`: Partial
- `Date / Blind Date`: Partial
- `Alerts/Notifications`: Aligned
- `Gallery uploads flow`: Aligned

## 4) Recommended Next Execution Order

1. `Volunteer core behavior parity pass` (application/write loops, status updates, role routing).
2. `Organizer + Employer behavior parity pass` (approval/rejection write loops + wallets).
3. `Date / Blind Date hardening` (UI polish, call/chat handoff polish, and full invitation edge-case QA).
4. `Community UX polish pass` (closer visual parity tuning for post/create layouts and attachment affordances).

## 5) Repeatable Audit Commands

```powershell
powershell -ExecutionPolicy Bypass -File ios-migration\scripts\android_ios_contract_diff.ps1 `
  -AndroidSourceRoot app/src/main/java `
  -IOSContractFile ios-migration/starter/FirebaseContract.swift
```

```powershell
powershell -ExecutionPolicy Bypass -File ios-migration\scripts\check_target_coverage.ps1 `
  -StarterDir ios-migration/starter `
  -TargetSourceDir <YourIOSSourceRoot>
```
