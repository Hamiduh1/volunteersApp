# iOS Migration Phased Rollout

This plan keeps Android stable and ships iOS in controlled phases.

## Phase 0: Contract Freeze (2-3 days)
Scope:
- Lock backend contract from `FIREBASE_CONTRACT.md`.
- Confirm all required Firebase indexes exist for current query patterns.
- Confirm Cloud Functions and Storage paths are stable.

Exit criteria:
- No breaking backend schema changes pending.
- iOS team has approved contract baseline.

## Phase 1: iOS Foundation (3-5 days)
Scope:
- Create iOS SwiftUI project and environments (dev/prod config).
- Integrate Firebase Auth/Firestore/Storage/Functions/Messaging SDKs.
- Add `GoogleService-Info.plist`.
- Add shared constants/models from `ios-migration/starter/`.

Exit criteria:
- App launches and authenticates.
- Firestore read/write smoke test passes for signed-in user.

## Phase 2: Core Access + Shell (1 week)
Scope:
- Login/register/email verification.
- Load current user profile + role resolution.
- App shell/navigation by role (volunteer, organizer, employer, owner/admin).

Exit criteria:
- Role-based landing pages work.
- User session persistence works across relaunch.

## Phase 3: Volunteer MVP (2 weeks)
Scope:
- Events list + event detail + apply flow.
- Jobs list + job detail + apply flow.
- My Activity (events + jobs applications status).
- Wallet read-only summary (balance/history view first).

Exit criteria:
- End-to-end volunteer loop works with live backend data.
- Status updates from organizer/employer are visible on iOS.

## Phase 4: Organizer/Employer MVP (2-3 weeks)
Scope:
- Organizer: hosted events, applicants review, approve/reject.
- Employer: posted jobs, applications review, approve/reject.
- Basic organizer wallet actions and transaction history.

Exit criteria:
- End-to-end organizer/employer loops match Android behavior.

## Phase 5: Advanced Features (3-4 weeks)
Scope:
- Chat/invitations/call history and call session integration.
- Live stream join/host flow.
- Payment methods and payout setup integration.
- AI assistant and profile/support extras.

Exit criteria:
- Feature parity target reached for release scope.

## Phase 6: Release Hardening (1-2 weeks)
Scope:
- Regression and performance test pass.
- Crash/analytics instrumentation.
- TestFlight rollout and App Store prep.

Exit criteria:
- Production quality bar met.
- TestFlight feedback addressed.

## Recommended Order Inside Xcode
1. `FirebaseContract.swift`
2. `CoreModels.swift`
3. Auth/Session layer
4. Repositories (Events/Jobs/Applications)
5. Screen-by-screen UI
