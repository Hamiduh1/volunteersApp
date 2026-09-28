# iOS Social Inbox -> Android Parity Guide

Date: 2026-07-06

Use this document as the behavior spec when updating the native Android app (`volunteersApp`). iOS is the reference implementation; Firebase is the shared contract.

**Primary iOS sources:**
- `VolunteersAppiOS/Core/Repositories/ChatRepository.swift`
- `VolunteersAppiOS/Core/Repositories/CallsRepository.swift`
- `VolunteersAppiOS/Features/Shared/Chat/ConversationsListView*.swift`
- `VolunteersAppiOS/Features/Shared/Chat/ConversationDetailView*.swift`
- `VolunteersAppiOS/Features/Shared/Calls/CallHistoryView*.swift`
- `VolunteersAppiOS/Core/Services/SessionManager.swift`
- Push/call: `IncomingPushDispatchCoordinator`, `CallKitIncomingCallCoordinator`, `VolunteersAppDelegate`

**Related docs:**
- `ios-migration/ANDROID_UI_UX_HANDOFF.md`
- `ios-migration/FIREBASE_CONTRACT.md`
- Backend: `my-firebase-project/my-firebase-functions/src/index.ts`

---

## 1. What Social Inbox includes

Three tabs in one hub:

| Tab | Purpose |
|-----|---------|
| **Chats** | Conversation list + search + new group |
| **Invitations** | Pending/accepted chat invitations from User Directory |
| **Calls** | Call history (audio/video/missed filters) |

Also:
- **User Directory** - find users, send chat invitations, block/unblock
- **Conversation detail** - messages, media, reply, delete, audio/video call buttons
- **Active call UI** - Agora audio/video
- **Background plumbing** - push tokens, incoming call wake, listeners even when inbox is not open

---

## 2. Firestore & Storage paths

| Path | Use |
|------|-----|
| `chats/{chatId}` | Conversation doc |
| `chats/{chatId}/messages/{messageId}` | Messages |
| `chats/{chatId}/call_logs/{callLogId}` | Per-chat call log |
| `call_sessions/{sessionId}` | Live ringing/accepted call session |
| `users/{uid}/call_history/{callLogId}` | Denormalized call feed (read this first) |
| `users/{uid}/call_history_hidden/{callLogId}` | User hid a call row |
| `chat_invitations` / user invitations | Directory invite flow |
| `chat_attachments/{chatId}/{messageId}.{ext}` | Media storage |

### Key `chats/{chatId}` fields
- `chatType`: `direct` | `group`
- `participants`
- `exitedParticipantIds`
- `groupName`, `groupCreatorId`
- last-message summary fields

### Key message fields
- Text: `messageText` + `text`
- Sender: `senderId`, `senderDisplayName`
- `timestamp`
- Read/delivery: `deliveredTo`, `readBy`
- Media: `mediaUrl`, `mediaType`, `mediaStoragePath`
- Reply: `replyToMessageId`, `replyToText`, `replyToSenderId`, `replyToSenderName`
- Soft delete: `isDeleted`, `deletedAt`, `deletedBy` -> render **Message deleted**

### Key `call_sessions` fields
- `chatId`, `callerId`, `receiverId`, `participantIds`
- `callType`: `audio` | `video`
- `status`: `ringing` -> `accepted` / `declined` / ended
- `agoraChannelName`: `call_{sessionId}`
- `callerName`, `createdAt`, `updatedAt`
- `acceptedParticipantIds`, `declinedParticipantIds`, `endedParticipantIds`

---

## 3. Cloud Functions Android must use

| Callable | When |
|----------|------|
| `registerUserPushTokens` | Save `fcmToken` on sign-in / refresh |
| `getConversationMessagingAvailability` | Before send/upload if blocked/exited |
| `getConversationCallAvailability` | Before placing call |
| `getAgoraRtcToken` | Join Agora channel |
| `getSocialInboxPushDiagnostics` | Optional push setup screen |

Server triggers:
- `onCallSessionCreated` -> incoming call push
- `onCallSessionUpdated` -> call cancel push
- `onChatMessageCreatedNotifyRecipients` -> message push

---

## 4. Inbox UI

### Chats tab
- Realtime conversation list for current user
- Search by name, last message, group name
- Show last message preview, timestamp, unread hints
- Presence from `presenceState` + `lastActiveAt`
- Actions: open chat, start call, create group

### Invitations tab
- Filters: All / Pending / Accepted / Declined
- Pending -> Accept / Decline
- Accepted -> Open chat (`matchedChatId` or find/create direct chat)
- Badge count on tab for pending invitations

### Calls tab
- Load from `users/{uid}/call_history` first
- Filters: type + direction
- Search by peer name
- Swipe/delete -> write `call_history_hidden` tombstone + delete from `call_history`
- Tap row -> open chat or redial

---

## 5. Conversation detail

### Realtime messages

```text
chats/{chatId}/messages
  .orderBy("timestamp", DESC)
  .limit(150)
```

- Query descending, render ascending
- Use a snapshot listener
- Composer fixed at bottom

### Send text
- Batch message doc + chat summary patch
- Support reply fields
- Validate participant, exit state, and block state

### Media upload
1. Pick photo or video
2. Show pending preview before send
3. Limits: images <= 8 MB, videos <= 20 MB
4. Reserve `messageId` before upload
5. Upload to `chat_attachments/{chatId}/{messageId}.ext`
6. Write Firestore media fields

### Delete / reply / share
- Long-press menu: **Reply**, **Share**, **Delete**
- Delete labels: `Delete Photo` / `Delete Video` / `Delete Message`
- Soft delete in Firestore
- Deleted bubble: **Message deleted**
- Share disabled for deleted messages

### Read receipts
- Mark others' messages delivered/read while open
- Show ticks on sent messages

### Group chat
- Create with name + members
- Exit group -> add uid to `exitedParticipantIds`
- Block send/upload after exit

---

## 6. User Directory
- List/search users
- Send chat invitations
- Track sent invitations
- Block/unblock users
- Accepted invitation opens or creates direct chat

---

## 7. Calls

### Outbound
1. Call `getConversationCallAvailability`
2. Handle blocked / in-call / exited / push reachability states
3. Create `call_sessions` + call log
4. Server sends push
5. Caller auto-opens call UI when `ringing` or `accepted`
6. Fetch Agora token and join

### Inbound
- Android equivalent of iOS PushKit/CallKit is high-priority FCM + full-screen incoming call UI
- Do **not** auto-open receiver call UI from a chat listener
- Receiver answers from incoming UI only

### End / cancel
- Session updates drive hang-up / cancel
- `call_cancelled` dismisses ring UI

---

## 8. Push notifications

### Registration
Register with `registerUserPushTokens({ fcmToken, platform })` on sign-in and token refresh
(`platform`: `"android"` | `"ios"`). Both stores write `users/{uid}.fcmToken` (FCM registration
token — not a raw APNs device token).

### Android → iOS call delivery
1. Android creates `call_sessions` with full participant fields + `agoraChannelName: call_{sessionId}`
2. Server mirrors to `users/{callee}/incoming_call_sessions`
3. Server sends FCM with `type: incoming_call` (data payload for Android; APNs alert/category for iOS)
4. iOS must handle push (CallKit / `IncomingPushDispatchCoordinator`) **and** keep `fcmToken` current
5. `call_cancelled` dismisses ring when session leaves `ringing`

### Message push
- `type: chat_message`
- Tap opens conversation

### Incoming call push
- `type: incoming_call`
- Full-screen / lock-screen ring
- `type: call_cancelled` dismisses ring

### Background runtime
On app start/resume:
- Register FCM token
- Start incoming `call_sessions` / `incoming_call_sessions` listener
- Re-surface missed ringing sessions after unlock

---

## 9. Call history

Primary: `users/{uid}/call_history`

Fallback: `chats/{chatId}/call_logs`

Hide path: `users/{uid}/call_history_hidden/{callLogId}`

---

## 10. Suggested Android module map

| Android module / class | iOS reference |
|------------------------|---------------|
| `SocialInboxScreen` | `ConversationsListView` |
| `ConversationViewModel` | `ConversationDetailViewModel` |
| `ChatRepository` | `ChatRepository.swift` |
| `CallsRepository` | `CallsRepository.swift` |
| `IncomingCallService` | incoming push + call coordinator |
| `FirebaseMessagingService` | iOS push handlers |
| `AgoraCallActivity` / call screen | iOS call UI |
| `UserDirectoryScreen` | iOS user directory |

---

## 11. Acceptance checklist
- [ ] Realtime message listener with 150 limit
- [ ] Photo/video send with size limits
- [ ] Reply preview + deleted bubble
- [ ] Group create + exit blocks sending
- [ ] Invitations accept/decline + open chat
- [ ] Outbound call opens caller UI
- [ ] Incoming call full-screen ring
- [ ] Notification tap opens call or chat
- [ ] Push token registered via callable
- [ ] Call history loads from `users/{uid}/call_history`

---

## 12. Recommended implementation order
1. Chat repository parity
2. Social Inbox 3-tab UI
3. FCM + incoming call full-screen flow
4. Agora call screen
5. Call history + directory + invitations
6. Push diagnostics

---

## 13. Working reference note
This file preserves the earlier Social Inbox parity guide from chat as a repo-local working reference for later Android/iOS parity passes.
