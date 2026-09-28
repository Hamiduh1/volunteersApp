# Android-to-iOS Calling Setup

Android and iOS use one call contract:

- Firestore session: `call_sessions/{sessionId}`
- Incoming mirror: `users/{uid}/incoming_call_sessions/{sessionId}`
- Agora channel: `call_{sessionId}`
- Token callable: `getAgoraRtcToken({ sessionId, channelName, role: "publisher", uid })`
- Push types: `incoming_call` and `call_cancelled`
- Push token field: `users/{uid}.fcmTokens` (an array retaining every signed-in device)

## Add To The iOS Target

Copy these starter files into the actual Xcode target:

- `Core/Calls/IncomingCallCoordinator.swift`
- `Core/Calls/AgoraCallMediaClient.swift`
- `Core/Services/VolunteersAppDelegate.swift`
- `Features/Shared/Calls/IncomingCallPresentation.swift`

Also copy the `incomingCallSessions` case in `FirebaseContract.swift` and the app changes in `VolunteersAppiOSApp.swift`.

Add the Agora iOS RTC SDK to the target, then set `AGORA_APP_ID` in the target build settings and Info.plist. The media client is intentionally guarded by `canImport(AgoraRtcKit)`: without the SDK, an incoming call is ended rather than appearing answered but never connecting.

Merge [Info.plist.calls.template.xml](starter/Config/Info.plist.calls.template.xml) into the target Info.plist. Enable Push Notifications and Background Modes for Remote notifications, Audio/Video, and VoIP. Configure Firebase Messaging with the production APNs key/certificate for the iOS bundle ID.

`AgoraCallMediaClient` and `IncomingCallPresentation` now own separate local-preview and remote-video canvases. This is required for video calling: a single canvas cannot reliably switch between the local and remote feed when the other participant joins. The client publishes 720p/30fps video, renews its token before expiry, and places the local preview above the remote feed.

## Closed-App Calls On iOS

The shared Firebase function sends a high-priority FCM data message to Android and an APNs alert through FCM to iOS. That produces an incoming-call notification when the iOS app is terminated, provided the user has allowed notifications and the device FCM token is registered.

For a true iOS system incoming-call screen while the process is terminated, the production iOS target must also use PushKit and CallKit:

1. Register a `PKPushRegistry` for `.voIP` pushes after sign-in and send its VoIP token to a server-only endpoint.
2. Receive that push in `pushRegistry(_:didReceiveIncomingPushWith:for:completion:)` and report it immediately to a `CXProvider`.
3. Have the server send the VoIP payload directly to APNs with the configured APNs key, team ID, key ID, and production bundle topic. Do not send APNs credentials or a VoIP token through Firebase client code.

FCM notification delivery is the safe fallback in this starter snapshot. It must not be presented as a replacement for PushKit/CallKit because iOS reserves that background call behavior for the VoIP path.

## Deploy And Test

1. Deploy `firestore.rules` and Firebase Functions.
2. Sign in on both iOS and Android, allow notifications, and confirm both FCM registration tokens are present in `users/{uid}.fcmTokens`.
3. With the receiver app terminated, start an Android-to-iOS call and an iOS-to-Android call. Confirm each receiver gets an incoming-call notification and opens the right `sessionId`.
4. Answer both an audio and a video call. Confirm `call_sessions/{sessionId}` changes to `accepted`, both devices join `call_{sessionId}`, each person sees the other person, and both local preview cards remain visible.
5. End or decline from either device. Confirm both ring surfaces dismiss and the call log status updates.

Use two physical devices for background-push testing. iOS simulators cannot validate APNs delivery.
