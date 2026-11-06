# Events loop fixes — Android handoff

Organizer creates an event → volunteers register (free or paid) → organizer reviews → event live stream → My Activity.

Backend and rules changes are shared by both apps. Deploy order: **functions first, then rules**
(the new rules expect `participantsCountVersion`, which the functions and the daily job write).

---

## What was broken

1. **Seats were never counted.** `applyForEvent` and the paid-ticket fulfillment stopped checking
   `volunteerLimit` and stopped updating `participantsCount`, so every event stayed at 0:
   unlimited signups, no "Event full", and the rules that lock the price and deletion once people
   have registered never kicked in (organizers could change the price or delete an event after paid signups).
2. **Paid registrations were stuck at `PENDING`.** Rules stop organizers reviewing paid applications,
   and nothing on the server approved them, so paid guests could never watch the event stream.
3. **iOS never opened Stripe Checkout** for paid events (it ignored `checkoutUrl`). Check Android does open it.
4. **Event-only live streams were undiscoverable**: not in the Live directory, no push, share link disabled.

---

## Backend (functions)

### Seats

- An application **holds a seat** when its status is `PENDING`, `APPROVED`, `ACCEPTED`, `ATTENDED` or `COMPLETED`.
  `PENDING_PAYMENT`, `REJECTED`, `WITHDRAWN` do not.
- `events.participantsCount` = seats taken. `events.participantsCountVersion == 2` means the count is trusted.
- `applyForEvent` (free): refuses with **"This event is full."** when seats ≥ `volunteerLimit`, and takes the seat
  in the same transaction. Writes `seatTakenAt` on the application.
- `applyForEvent` (paid): refuses checkout when the event is already full. The seat is taken at payment.
- Older events without the version are recounted on their next signup and by **`reconcileEventSeatCountsJob`** (daily).
- New trigger **`onEventApplicationSeatChange`**: when an application stops holding a seat (rejected, withdrawn,
  deleted) the count goes down; when a rejected/withdrawn one is re-approved it goes up.

### Paid tickets

- Stripe fulfillment for `EVENT_TICKET` now sets the application to **`APPROVED`** (`approvedBy: "PAYMENT"`,
  `paymentStatus: "PAID"`) and takes the seat.
- If the event closed, filled up, or the volunteer cancelled before payment completed, the payment is
  **refunded automatically**; the application becomes `WITHDRAWN` with `paymentStatus: "REFUND_PENDING"` and `closureReason`.

### New callables (App Check enforced)

| Callable | Input | Result |
|---|---|---|
| `withdrawEventApplication` | `{eventId}` | `{success, status: "WITHDRAWN", message}`. Allowed for free registrations and unfinished checkouts (`PENDING_PAYMENT`) before the event starts. Paid tickets: error "Paid tickets can't be cancelled in the app. Message the organizer about a refund." |
| `getMyEventLiveSessions` | none | `{sessions: [{sessionId, eventId, hostId, hostName, title, startedAtMillis}]}` — live event-only streams for events where the caller is approved. |
| `deleteOrganizerEvent` (existing) | `{eventId}` | `{outcome: "DELETED" \| "CLOSED", message}`. Events with registrations or a linked stream are closed, not deleted. **Use this instead of deleting the document.** |

- A volunteer who **withdrew** can register again (`applyForEvent` accepts an existing `WITHDRAWN` application).
  A `REJECTED` one cannot.

### Live

- Going live with audience **Accepted Event Guests** now pushes the event's approved guests
  ("{host} is live for your event", type `liveStream`, `referenceId` = session id). Respects their live-stream notification setting.
- The Agora token check accepts `attended` and `completed` too (matches the rules).

---

## Rules (`my-firebase-project/firestore.rules`)

- New `eventHasNoRegistrations(data)`: `participantsCountVersion == 2 && participantsCount == 0`.
- Changing `payment` / `eventFee`, flipping `isActive` / `isDeleted`, and deleting an event now require
  `eventHasNoRegistrations`. Untrusted (old) counts are treated as "may have registrations".
- Event create accepts `participantsCountVersion` (must be 2 if present). **Android should write
  `participantsCountVersion: 2` with `participantsCount: 0` when creating an event** so the organizer can still
  edit the price before anyone registers.
- Organizers can still set `closeEntries` true/false on their events.

---

## Android client changes

### Volunteer: event detail

| State | Show |
|---|---|
| No application, open | Apply / Choose payment |
| `PENDING_PAYMENT` | Orange card "Payment not finished — your spot isn't held until payment completes". Primary button **Continue payment** (call `applyForEvent` again; the server returns the same `checkoutUrl`). **Cancel registration** button. |
| `PENDING` | "Application received — waiting for organizer confirmation". Cancel registration if free. |
| `APPROVED` (free) | "You're going". Cancel registration if free and before start. |
| `APPROVED` + `paymentStatus == PAID` | "Ticket confirmed". No cancel. |
| `WITHDRAWN` | Treat as not registered (can apply again). |
| `REJECTED` | "Application closed". |

- Paid apply: open `checkoutUrl` in a Custom Tab. When it closes, reload the application;
  if `APPROVED` show "Payment received. Your ticket is confirmed."
- Ticket line: "Ticket: $X • Paid by card through Stripe" (don't hardcode the 5% fee; the rate is server-side).
- If approved and `getMyEventLiveSessions` returns a session for this event: red **Live now** card with **Watch live**
  (open the live room for that session id).

### Volunteer: Live tab

- Call `getMyEventLiveSessions` on refresh; if not empty, show **"Your events are live"** above the other shelves,
  one row per session (title, "{host} · for registered guests", Watch).

### Volunteer: My Activity

- `PENDING_PAYMENT` belongs in **Pending**, labelled "Payment pending", subtitle "Finish payment to hold your spot".

### Organizer: hosted events

- Row shows "Registered: N of LIMIT" and a badge: **Closed** (`isActive == false`), **Entries closed**
  (`closeEntries == true`), or **Full** (count ≥ limit).
- Swipe actions: **Close entries / Reopen** (update `closeEntries`, `lastUpdatedAt`) and **Delete** via
  `deleteOrganizerEvent`, with a confirmation: "Events nobody has registered for are deleted. Events with
  registrations or a stream are closed and hidden from discovery, so volunteers keep their records."
  Show the returned `message`.
- Editing an event must **not** write `isActive: true` (that reopened closed events and is denied once people registered).

### Organizer: requests

- Paid applications can't be approved or rejected in the app; show "Paid registrations are confirmed automatically
  when payment completes."
- Approve/reject writes only `status` and `lastUpdatedAt` on `events/{eventId}/applications/{uid}`.
  Don't also write the root `applications` collection (rules deny it for events).

---

## Deploy checklist (Android dev, from the machine with `.env`)

1. `firebase deploy --only functions` — includes `applyForEvent`, `withdrawEventApplication`,
   `getMyEventLiveSessions`, `onEventApplicationSeatChange`, `reconcileEventSeatCountsJob`,
   `onLiveSessionStartedNotifyFollowers`, `onLiveSessionBecameLiveNotifyFollowers`, the Stripe webhook,
   plus the earlier garage functions (`expireGarageSalesJob`, `requestGarageSalePurchase`).
2. Optionally trigger `reconcileEventSeatCountsJob` once from Cloud Scheduler so old events are counted right away.
3. Tell the iOS side; the Firestore rules are deployed from there after the functions are live.
