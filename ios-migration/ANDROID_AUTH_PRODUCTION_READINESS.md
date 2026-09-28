# Android Authentication Production Readiness

This checklist covers the Android create-account, sign-in, email verification, phone OTP, and password-reset flows.

## Enforced In Code

1. New profiles are created only for the signed-in Firebase UID with matching initial role fields and no app-held wallet field.
2. Client updates cannot change `role`, `userRole`, `userType`, or `wallet`; privileged role changes are server-owned or use the explicit organizer enrollment rule.
3. Registration normalizes email and phone input, applies the app password baseline, and only cleans up the Firebase user created by the failed attempt.
4. Password reset always returns the same success message for an existing or unknown email address.
5. Email verification code submission requires both Firebase Auth and App Check. The code is bound to the signed-in UID; Android never looks up an account by typed email.
6. Sign-in errors use customer-safe messages. Provider details remain in protected logs only.

## Firebase Console Release Checks

1. Enable Email/Password in Firebase Authentication.
2. Enable Firebase Authentication email-enumeration protection and configure the project password policy at least as strong as the Android baseline.
3. Confirm the password-reset action domain is authorized and the Firebase Auth email template points to the production project.
4. Configure the email-code provider and sender identity required by `requestEmailVerificationCode`; keep provider credentials in Functions runtime secrets only.
5. In Play Console, open `Protected with Play > Play Integrity API` and link the Google Cloud project behind `volunteersapp-968b2`. The linked project must be the same Firebase project used by the released Android app.
6. In Firebase Console, open `Security > App Check > Apps` and register the production Android Firebase app with application ID `com.volunteersapp.app` using the **SHA-256 of the Play app-signing certificate**. Retrieve it from `Protected with Play > Play Store protection > Manage Play app signing`; do not use only the debug or upload-key fingerprint. For the Internal Testing track, require `PLAY_RECOGNIZED` and `LICENSED`, but do not explicitly require a device-integrity level.
7. If Google sign-in is added later, copy the Play app-signing **SHA-1** into the Android app settings in Firebase Authentication, enable the Google provider, download the refreshed `app/google-services.json`, and ship it in the next AAB. The current Android flow supports email/password and does not contain a Google sign-in provider.
8. Confirm Email/Password is enabled in Firebase Authentication for `volunteersapp-968b2` and that the authorized password-reset domain and verification email sender are production values.
9. Deploy `firestore.rules` with the Android and Functions release, then test a volunteer, organizer, and employer account from a fresh install.

## Google Play Internal Testing Checks

1. Confirm the Internal Testing release is package `com.volunteersapp.app` and its version code is newer than every version already installed by the tester. The current source is version `1.0.3` / version code `6`.
2. Add the tester's exact Google account to the Internal Testing tester list, open the opt-in URL with that account, and install or update **from Google Play**. Do not test a side-loaded APK or an Android Studio install when validating Play Integrity.
3. In `Protected with Play > Play Store protection > Manage Play app signing`, copy the **App signing key certificate** SHA-256, not the upload key SHA-256. Firebase App Check accepts the certificate of the APK Play delivers to testers. If that page is unavailable, the account owner must grant `Release to production, exclude devices, and use Play App Signing` or Admin access.
4. After registering the certificate and linking Play Integrity, wait for the Firebase App Check metrics to show valid requests before enforcing a Firebase product. Do not remove enforcement as a workaround.
5. From a freshly installed Internal Testing build, create and verify one volunteer, organizer, and employer account, then sign out and sign back in. Confirm the initial `users/{uid}` profile write and subsequent profile read complete without `PERMISSION_DENIED`.
6. Capture the `LoginViewModel` or `SignUpViewModel` Logcat line if a test still fails. It reports the failed stage plus the Firebase Auth and Firestore status without exposing credentials.

## Required Release Tests

1. Create each supported account type, verify by email code and phone OTP, then sign in.
2. Attempt a duplicate email and malformed or weak password; verify the UI gives safe, actionable feedback.
3. Request a password reset for both a registered and an unregistered email; the customer-visible response must be indistinguishable.
4. Try email-code verification without an authenticated session; it must be rejected.
5. Attempt direct Firestore updates to `role`, `userRole`, `userType`, and `wallet`; each must be denied for a normal user.
