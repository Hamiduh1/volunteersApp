# Live Stream Request System - Implementation Guide

## Overview
This document describes the new **Bidirectional Live Stream Request System** that enables volunteers to request to join live streams hosted by organizers. The system features real-time notifications, Material 3 design, and smooth animations.

## Architecture Overview

### Data Models

#### 1. JoinLiveStreamRequest (NEW)
Located: `streams/JoinLiveStreamRequest.kt`
```kotlin
data class JoinLiveStreamRequest(
    val requestId: String               // Firestore document ID
    val volunteerId: String             // UID of requesting volunteer
    val volunteerName: String           // Display name
    val volunteerProfilePicUrl: String? // Avatar for request card
    val streamId: String                // Agora channel name (stream ID)
    val status: String                  // "pending", "accepted", "rejected"
    val requestedAt: Date               // Server timestamp
    val respondedAt: Date?              // When organizer responded
)
```

#### 2. LiveSession (ENHANCED)
Located: `streams/LiveSession.kt`
- **New Field**: `acceptedVolunteerIds: List<String>` - Track which volunteers have been accepted to join

#### 3. LiveStreamUiState (ENHANCED)
```kotlin
data class LiveStreamUiState(
    // ... existing fields ...
    val incomingRequests: List<JoinLiveStreamRequest>  // For organizers during stream
    val requestsLoading: Boolean                       // Loading state for request actions
)
```

#### 4. LiveStreamsUiState (ENHANCED)
```kotlin
data class LiveStreamsUiState(
    // ... existing fields ...
    val pendingRequestStreamIds: Set<String>   // Streams where user has pending request
    val acceptedStreamIds: Set<String>         // Streams where user was accepted
    val submittingRequestStreamId: String?     // Currently submitting request
    val requestSubmissionError: String?        // Error message if request fails
)
```

## User Flows

### For Organizers (Hosts)

#### 1. Go Live Flow
```
OrganizerMainScreen/Dashboard
  ↓
  [Go Live Button]
  ↓
StartStreamScreen (Fill title + description)
  ↓
  [GO LIVE NOW]
  ↓
LiveStreamScreen (Broadcast active)
  ↓
  [Shows Incoming Requests Panel]
  ↓
  [Accept/Decline buttons for each request]
```

#### 2. Request Management During Broadcast
- **Real-time Listener**: LiveStreamViewModel listens to `join_requests` collection filtered by:
  - `streamId` == current stream's agoraChannelName
  - `status` == "pending"
- **In the UI**: Animated panel appears with incoming requests
- **Badge**: Request count displayed in stream header
- **Actions**:
  - ✅ Accept: Updates request status to "accepted", adds volunteerId to `acceptedVolunteerIds`
  - ❌ Decline: Updates request status to "rejected"


### For Volunteers (Users)

#### 1. Browse & Request Flow
```
VolunteerScreen → LiveStreamsScreen
  ↓
  [Shows all active live streams]
  ↓
  Per Stream Card:
  ├─ "Request Join" button (enabled)
  ├─ "Requesting..." button (submitting)
  ├─ "Joined" button (accepted)
  └─ Watch button (for non-request flows)
```

#### 2. Request Tracking
- **Real-time Listener**: LiveStreamsViewModel listens to `join_requests` collection filtered by:
  - `volunteerId` == current user's UID
- **UI States**:
  - If `status == "pending"`: Shows "Requesting..." button with spinner
  - If `status == "accepted"`: Shows "Joined" button (disabled)
  - If `status == "rejected"` or no request: Shows "Request Join" button
  - If `status == "accepted"`: Added to `acceptedStreamIds` set


## UI Components

### 1. IncomingRequestsPanel (LiveStreamScreen)
**Location**: `LiveStreamScreen.kt`
**Purpose**: Shows organizers incoming join requests during broadcast
**Features**:
- Horizontal slide-in animation with fade
- Request count badge in header
- Max height: 300dp (scrollable if many requests)
- Request cards show: volunteer name, avatar, accept/decline buttons

**Visibility Logic**:
```kotlin
AnimatedVisibility(
    visible = uiState.isHost && uiState.incomingRequests.isNotEmpty(),
    enter = slideInVertically + fadeIn,
    exit = slideOutVertically + fadeOut
)
```

### 2. RequestCard (LiveStreamScreen)
**Purpose**: Individual request card within IncomingRequestsPanel
**Features**:
- Volunteer avatar (or default person icon)
- Volunteer name
- Decline button (red X icon, 32dp)
- Accept button (green check icon, 32dp)
- Smooth press animations on buttons
- Disabled state while processing


### 3. ModernStreamCard (LiveStreamsScreen - ENHANCED)
**Changes**:
- Added optional `onRequestJoin` callback
- New parameters: `isRequestPending`, `isJoined`
- Dynamic button states:
  - "Request Join" → "Requesting..." → "Joined"
  - Button styling changes based on state (enabled/disabled/accepted)
  - Loading spinner on "Requesting..." state


### 4. StreamHeader (LiveStreamScreen - ENHANCED)
**Changes**:
- Added `incomingRequestsCount` parameter
- Red badge showing request count (max display "9+")
- Badge only shown if organizer and count > 0


## Firebase Integration

### Firestore Collections

#### join_requests
**Path**: `/join_requests/{requestId}`
**Document Structure**:
```
{
  volunteerId: "user123",
  volunteerName: "John Doe",
  volunteerProfilePicUrl: "https://...",
  streamId: "agora_channel_123",
  status: "pending" | "accepted" | "rejected",
  requestedAt: Timestamp,
  respondedAt: Timestamp (null until organizer responds)
}
```

**Indexes Required**:
```
- Collection: join_requests
  - Fields: (streamId, status) - for organizer queries
  - Fields: (volunteerId, status) - for volunteer queries
```

**Firestore Rules**: See `firestore.rules` for complete security rules
```
- Volunteers: CREATE requests (with their own UID)
- Volunteers: READ their own requests
- Organizers: READ requests for their streams
- Organizers: UPDATE request status (accept/reject only)
```

### Real-time Listeners

#### Organizers (LiveStreamViewModel)
```kotlin
private fun listenToIncomingRequests(streamId: String) {
  db.collection("join_requests")
    .whereEqualTo("streamId", streamId)
    .whereEqualTo("status", "pending")
    .addSnapshotListener { /* updates UI state */ }
}
```
- **Triggers**: When organizer joins as host
- **Updates**: `LiveStreamUiState.incomingRequests`
- **Cleanup**: Removed in `leaveStream()`

#### Volunteers (LiveStreamsViewModel)
```kotlin
private fun listenForUserRequests() {
  db.collection("join_requests")
    .whereEqualTo("volunteerId", currentUserId)
    .addSnapshotListener { /* updates request tracking */ }
}
```
- **Triggers**: At ViewModel initialization
- **Updates**: `pendingRequestStreamIds`, `acceptedStreamIds`
- **Cleanup**: Removed in `onCleared()`


## Implementation Checklist

✅ Created `JoinLiveStreamRequest` data model
✅ Enhanced `LiveSession` with `acceptedVolunteerIds`
✅ Enhanced `LiveStreamUiState` with request tracking
✅ Enhanced `LiveStreamsUiState` with request status
✅ Added request listener in `LiveStreamViewModel`
✅ Added `acceptJoinRequest()` method in `LiveStreamViewModel`
✅ Added `rejectJoinRequest()` method in `LiveStreamViewModel`
✅ Added `IncomingRequestsPanel` composable
✅ Added `RequestCard` composable
✅ Updated `StreamHeader` with request badge
✅ Enhanced `ModernStreamCard` with request join flow
✅ Added request listener in `LiveStreamsViewModel`
✅ Added `submitJoinRequest()` method in `LiveStreamsViewModel`
✅ Updated Firestore security rules
✅ All stream files compile without errors

## Testing Checklist

### Organizer Testing
1. Start live stream from OrganizerMainScreen
2. Wait for incoming requests from volunteers
3. See request panel appear with animations
4. Accept request → verify request status changes to "accepted"
5. Decline request → verify request status changes to "rejected"
6. End stream → verify request panel cleanup

### Volunteer Testing
1. Open LiveStreamsScreen to see available streams
2. See "Request Join" button on stream cards
3. Click "Request Join" → button becomes "Requesting..."
4. Request submitted → button state tracked in real-time
5. When organizer accepts → button shows "Joined"
6. Verify error handling if request submission fails

## Material 3 Design Features

### Colors & Styling
- **Request Panel**: `surfaceVariant` with alpha 0.95f
- **Request Cards**: `surfaceVariant` with alpha 0.7f
- **Action Buttons**: 
  - Accept: `MaterialTheme.colorScheme.primary` (green)
  - Decline: `MaterialTheme.colorScheme.error` (red)
  - Both: 32dp circles with smooth transitions

### Animations
- **Panel Entry**: `slideInVertically` + `fadeIn` (smooth spring physics)
- **Panel Exit**: `slideOutVertically` + `fadeOut`
- **Button Press**: Material 3 ripple effect
- **Request Badge**: Fades in/out with count changes

### Spacing & Dimensions
- **Panel Height**: Max 300dp (scrollable overflow)
- **Request Cards**: 12dp vertical spacing
- **Rounded Corners**: 12-16dp throughout
- **Icon Sizes**: 16dp buttons, 36dp avatars, 20dp icons

## Error Handling

### Request Submission Errors
- **Displayed**: Via Snackbar at bottom of LiveStreamsScreen
- **Duration**: Short (3 seconds)
- **Dismissible**: Auto-dismiss or manual
- **Action**: `viewModel.clearRequestError()`

### Request Accept/Reject Errors
- **Displayed**: Via `uiState.error` Snackbar
- **Duration**: Long (5 seconds)
- **Fallback**: UI state shows buttons as disabled during operation

## Performance Optimizations

1. **Firestore Indexes**: Proper compound indexes on frequently queried fields
2. **Real-time Listeners**: Automatically unsubscribe on ViewModel cleanup
3. **UI Updates**: Only update when state actually changes
4. **Request Cards**: Lazy item rendering in scrollable column
5. **Network**: Batch request status updates with single write

## Database Migration Notes

If adding to existing app:
1. Add Firestore rules for `join_requests` collection
2. Create Firestore indexes for queries:
   - Index 1: `live_sessions` + `request_status`
   - Index 2: `volunteerId` + `status`
3. Default empty list for `acceptedVolunteerIds` in existing `LiveSession` documents
4. No data migration needed (new collections only)

## Future Enhancements

- [ ] Request message field (volunteer explains why they want to join)
- [ ] Auto-accept for followers of organizer
- [ ] Request expiration (auto-reject after stream ends)
- [ ] Analytics: Track accept/reject ratios per organizer
- [ ] Notifications: Push notification when request accepted
- [ ] Participant limit: Organizer can set max participants
- [ ] Video grid UI: Show accepted participants' video feeds
