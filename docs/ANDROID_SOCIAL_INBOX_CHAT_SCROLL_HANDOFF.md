# Android Social Inbox chat scroll handoff

iOS Social Inbox conversation detail was modernized so **Send always lands on the latest bubble** — no manual scroll to find your own message.

Backend/Firestore is unchanged. Android should match the client UX.

## Problem

After tapping Send, the thread often stayed mid-scroll. Users had to drag down to see the message they just sent.

## iOS behavior to mirror

Files (iOS reference):

- `Features/Shared/Chat/ConversationDetailView.swift`
- `Features/Shared/Chat/ConversationDetailViewModel.swift`

### 1. Pin transcript to bottom

- Use a **stable bottom sentinel** at the end of the message list (clear 1pt view with a fixed id).
- On appear, after send, when the last message id changes, and when the composer gains focus: **scroll to that sentinel** (`anchor = bottom`).
- Prefer a default bottom scroll anchor if the platform supports it (iOS uses `.defaultScrollAnchor(.bottom)`).
- Wait one frame / post-layout before scrolling so newly appended rows are measurable (Lazy lists).

### 2. Optimistic local bubble on Send

- As soon as Send is accepted (before the network/Firestore write finishes), append a **local message** at the end of the in-memory list and clear the composer.
- Use a temporary id such as `local-{uuid}` and set `clientSentAtMs` to now (same field Android already writes on outbound messages).
- Bump a scroll token / trigger so the UI scrolls immediately.
- If the write fails, remove the local row and restore composer text / reply / pending media.

### 3. Merge listener snapshots without duplicates

- When the Firestore (or RTDB) messages listener delivers the confirmed message, **drop** the matching optimistic row.
- Match on: same `senderId`, same body text (or same media type for media sends), and `clientSentAtMs` within ~15s.
- Keep any still-unconfirmed optimistic rows appended and sorted with the server list by timestamp / `clientSentAtMs`.

### 4. Stable row identity

- Do **not** key the message list by array index/offset.
- Key each row by message document id, or a stable fallback (`senderId + clientSentAtMs + text prefix`) for local rows.

## Suggested Android checklist

- [ ] Conversation message `LazyColumn` / RecyclerView: bottom item sentinel + `scrollToItem(last)` / `animateScrollToItem` on send and on new last message.
- [ ] Optimistic insert in the conversation ViewModel before `sendMessage` / media upload commit.
- [ ] Listener merge removes optimistic duplicates via `clientSentAtMs` + sender + body/media type.
- [ ] List keys use message id (not adapter position).
- [ ] Keyboard open / IME inset: keep pinned to bottom when the user was already at the latest message (or always after own send).

## Out of scope

- No Cloud Functions or rules changes.
- Receipt ticks, push, and call UI are unchanged.
- Group vs 1:1 uses the same scroll/optimistic rules.
