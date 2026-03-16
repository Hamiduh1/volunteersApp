# Firebase Contract For iOS Client

Source of truth is current backend used by Android:
- Firestore rules: `my-firebase-project/firestore.rules`
- Storage rules: `my-firebase-project/storage.rules`
- Functions: `my-firebase-project/my-firebase-functions/src/index.ts`

## 1. Auth and Role Contract
- User must be authenticated for almost all app features.
- Primary role source:
  - `users/{uid}.role` and/or `users/{uid}.userRole`
  - `organizers/{uid}` existence for organizer checks.
  - `employers/{uid}` existence for employer checks.
- Elevated roles:
  - Owner/admin checks use custom claims and/or `users/{uid}.role` in `['owner','admin']`.

## 2. Firestore Collections (Observed In App/Rules)
Top-level collections currently in active use:
- `users`
- `organizers`
- `employers`
- `events`
- `jobs`
- `jobPosts` (legacy alias)
- `applications` (root-level job applications)
- `event_applications` (legacy event applications collection)
- `live_sessions`
- `join_requests`
- `chats`
- `call_sessions`
- `deposit_requests`
- `payout_requests`
- `marketplace_items`
- `purchase_requests`
- `advertisements`
- `garage_sales`
- `garage_sale_payments`
- `app_config`
- `system`
- `settings` (legacy/config docs)
- `howToUseTips`
- `galleryUploads`
- `general_support_items`
- `aml_cft_content`
- `user_reports`
- `userReports`
- `blindDateProfiles`
- `blindDateInvitations` (legacy top-level)
- `blindDateSentInvitations` (legacy top-level)
- `dating_profiles`

Key subcollections:
- `users/{uid}/transactions`
- `users/{uid}/payment_methods`
- `users/{uid}/beneficiaries`
- `users/{uid}/invitations`
- `users/{uid}/chat_invitations` (legacy alias)
- `users/{uid}/blindDateInvitations`
- `users/{uid}/blindDateSentInvitations`
- `users/{uid}/hostedEvents`
- `users/{uid}/jokes`
- `users/{uid}/followers`
- `users/{uid}/following`
- `events/{eventId}/applications`
- `events/{eventId}/event_applications` (legacy)
- `jobs/{jobId}/applications`
- `chats/{chatId}/messages`
- `chats/{chatId}/call_logs`

Collection group queries used:
- `applications`
- `jokes`

## 3. Storage Paths (Allowed By Rules)
- `profile_images/{userId}/{fileName}`
- `event_images/{userId}/{eventId}/{fileName}`
- `blind_date_media/{userId}/{fileName}`
- `dating_images/{userId}/{fileName}`
- `gallery_uploads/{fileName}`
- `joke_images/{userId}/{fileName}`
- `joke_videos/{userId}/{fileName}`
- `joke_docs/{userId}/{fileName}`
- `joke_misc/{userId}/{fileName}`
- `ads/{userId}/{fileName}`
- `garage_sales/{userId}/{fileName}`
- `marketplace_images/{userId}/{fileName}`

## 4. Callable Functions Used By Mobile
Functions currently called from client side:
- `getAgoraRtcToken`
- `requestEmailVerificationCode`
- `verifyEmailVerificationCode`
- `bootstrapOwnerSelf`
- `joinBlindDate`
- `acceptBlindDateInvitation`
- `declineBlindDateInvitation`
- `rejoinBlindDate`
- `getSecureExchangeRate`
- `createBeneficiaryVerification`
- `initiateTransfer`
- `payForAgentRole`
- `processAgentPayout`
- `cashOutAgentEarnings`
- `cashOutOwnerRevenue`
- `addPaymentMethod`
- `attachExternalAccount`
- `createUsBankAccountSetupIntent`
- `addUsBankAccountFromFinancialConnections`
- `requestMobileMoneyMethodVerification`
- `createConnectAccount`
- `createConnectOnboardingLink`
- `getConnectAccountStatus`
- `adminAddSupportAssociate`
- `supportListUsers`
- `supportGetUserAccountDetails`
- `adminListPayoutRequests`
- `adminReversePayoutRequestsCallable`

## 5. Status/Enum Normalization Requirement
Android already had status-case issues (`"approved"` vs enum uppercase).  
iOS should normalize incoming status values before enum mapping:
- uppercase
- trim spaces
- map unknown values to `.unknown`

This applies to:
- Application status
- Job status/opportunity status fields

## 6. Known Query/Index Sensitivity
Historically sensitive queries:
- Collection-group `applications` with organizer/employer filters.
- Filtered/sorted organizer and employer application lists.

Recommendation:
- Keep current query shapes for parity.
- If iOS adds new where/order combinations, add composite indexes in `firestore.indexes.json` before release.

## 7. Backward Compatibility Rules For iOS
- Do not rename existing collections.
- Do not remove legacy alias fields (`organizerId` and `organizerUid`, etc.) until both clients are migrated.
- For write operations, preserve current keys expected by rules/functions.
