# iOS Migration Kickoff (No Android Disruption)

This folder starts iOS migration without changing your current Android structure.

## Non-Interference Rules
- Keep `app/` (Android) unchanged while bootstrapping iOS.
- Add iOS work in a separate branch first (`ios-migration-kickoff`).
- Treat backend schema as a contract: additive changes only, no breaking field renames.
- Reuse existing Firebase project/backend (`volunteersapp-968b2`).

## What We Reuse Directly
- Firebase Auth + Firestore + Storage + Cloud Functions backend.
- Existing collection design and role model (`users`, `organizers`, `employers`, `owner/admin`).
- Existing callable function APIs.
- Existing business behavior (applications, wallet flows, status transitions).

## What We Rebuild for iOS
- UI, navigation, and screen components (SwiftUI).
- iOS media picker/camera and file handling.
- APNs/FCM iOS notification plumbing.
- iOS-specific call/media SDK integration and app lifecycle handling.

## Files In This Pack
- `ios-migration/FIREBASE_CONTRACT.md`:
  Canonical backend contract to follow in iOS.
- `ios-migration/PHASED_ROLLOUT.md`:
  Execution order, effort, and acceptance criteria.
- `ios-migration/PHASE26_FINAL_HARDENING.md`:
  Final build-fix, QA, and TestFlight/App Store readiness checklist.
- `ios-migration/starter/FirebaseContract.swift`:
  Compile-ready enums/path helpers for collections/functions.
- `ios-migration/starter/CoreModels.swift`:
  Starter Firestore models with status normalization for iOS.
- `ios-migration/scripts/check_target_coverage.ps1`:
  Verifies that all files listed in `starter/README.md` are present in your iOS source tree.
- `ios-migration/scripts/android_ios_contract_diff.ps1`:
  Diffs Android collection/function/storage references against iOS `FirebaseContract.swift`.

## Immediate Next Steps
1. Create iOS project target in Xcode (SwiftUI, iOS 16+ recommended).
2. Add Firebase iOS SDK (Auth, Firestore, Storage, Functions, Messaging).
3. Add `GoogleService-Info.plist` for `volunteersapp-968b2`.
4. Copy starter Swift files from `ios-migration/starter/` into your iOS target.
5. Implement auth + role bootstrap first, then MVP screens from `PHASED_ROLLOUT.md`.
