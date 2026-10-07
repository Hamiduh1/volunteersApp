package com.example.volunteersApp.streams

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup
import com.example.volunteersApp.firebase.FirestoreSubcollection

data class LiveRoomComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val replyToCommentId: String? = null,
    val replyToAuthorName: String? = null,
    val replyToText: String? = null,
    val authorPhotoUrl: String? = null,
)

// --- UI State and ChatMessage data classes remain the same ---
data class LiveStreamUiState(
    val session: LiveSession? = null,
    val isHost: Boolean = false,
    val isOnStage: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val remoteUid: Int? = null,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isSpeakerMuted: Boolean = false,
    val needsBroadcastPermissions: Boolean = false,
    val incomingRequests: List<JoinLiveStreamRequest> = emptyList(),
    val requestsLoading: Boolean = false,
    val liveLikesCount: Long = 0L,
    val liveCommentsCount: Long = 0L,
    val hasLikedLive: Boolean = false,
    val liveComments: List<LiveRoomComment> = emptyList(),
    val chromeVisible: Boolean = true,
    val sessionEnded: Boolean = false,
    val shareInProgress: Boolean = false,
    val shareError: String? = null,
    val showHostTools: Boolean = false,
    val roomViewers: List<LiveRoomViewer> = emptyList(),
    val blockedViewerIds: Set<String> = emptySet(),
    val mindLoomShareInProgress: Boolean = false,
    val mindLoomReplayShareInProgress: Boolean = false,
    val statusMessage: String? = null,
    /** Every remote broadcaster in the channel (host + stage guests), in join order. */
    val remoteUids: List<Int> = emptyList(),
    val hostAgoraUid: Int? = null,
    val isReconnecting: Boolean = false,
    /** Active viewers from presence heartbeats; null until the first presence snapshot. */
    val liveViewerCount: Long? = null,
    val peakViewerCount: Long = 0L,
    val endSummary: LiveEndSummary? = null,
    /** Raw status of this viewer's own stage request ("pending", "accepted", "rejected"), if any. */
    val myStageRequestStatus: String? = null,
    /** Remote broadcasters who turned their camera / mic off (Agora mute callbacks). */
    val mutedVideoUids: Set<Int> = emptySet(),
    val mutedAudioUids: Set<Int> = emptySet(),
)

data class LiveEndSummary(
    val durationMs: Long,
    val peakViewers: Long,
    val likes: Long,
    val comments: Long,
)

// You should define ChatMessage if it's not defined elsewhere
//data class ChatMessage(val userId: String, val authorName: String, val message: String)

class LiveStreamViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "LiveStreamVM"

    private val _uiState = MutableStateFlow(LiveStreamUiState())
    val uiState = _uiState.asStateFlow()

    private var agoraEngine: RtcEngine? = null
    private var applicationContext: Context? = null
    private var sessionListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null  // NEW: Listen for incoming requests
    private var likesListener: ListenerRegistration? = null
    private var commentsListener: ListenerRegistration? = null
    private var viewersListener: ListenerRegistration? = null
    private var blockedUsersListener: ListenerRegistration? = null
    private var viewerHeartbeatJob: Job? = null
    private var hostHeartbeatJob: Job? = null
    private var engagementSessionId: String? = null
    private var channelJoinJob: Job? = null
    /** Uid the current channel was joined with, as issued by the token endpoint. */
    private var joinedAgoraUid: Int? = null
    private var myStageRequestListener: ListenerRegistration? = null
    private var myStageRequestSessionId: String? = null
    private var commentCountJob: Job? = null
    private var likeInFlight = false
    private var lastCommentSentAtMs = 0L
    private var currentSessionId: String? = null
    private var currentShareAccessToken: String? = null
    private var shareTokenValidated: Boolean = false
    private var suppressReconnect = false
    private var reconnectAttempts = 0
    private val acceptedEventIds = mutableSetOf<String>()
    // Shared rules keep acceptedVolunteerIds server-owned: accepted join requests define the stage.
    private var rawSession: LiveSession? = null
    private var acceptedStageGuestIds: Set<String> = emptySet()
    private var acceptedRequestsListener: ListenerRegistration? = null
    /** A guest can't revoke an accepted request under shared rules, so stepping down is local. */
    private var leftStageSessionId: String? = null
    private var sessionSnapshotSeq = 0
    private var stageDeniedSessionId: String? = null
    private val hostHiddenCommentIds = mutableSetOf<String>()

    // --- DELETED: Removed redundant properties (`rtcEngine`, `_remoteUsers`, `appId`, `token`) ---
    // --- DELETED: Removed unused `initStream` method ---

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d(TAG, "Successfully joined Agora channel: $channel with uid: $uid")
            // This is the local user's UID, you can store it if needed.
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "Remote user joined with uid: $uid")
            _uiState.update { state ->
                val uids = (state.remoteUids + uid).distinct()
                state.copy(remoteUids = uids, remoteUid = preferredMainRemoteUid(uids, state.hostAgoraUid))
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Remote user left with uid: $uid, reason: $reason")
            _uiState.update { state ->
                val uids = state.remoteUids - uid
                state.copy(
                    remoteUids = uids,
                    remoteUid = preferredMainRemoteUid(uids, state.hostAgoraUid),
                    mutedVideoUids = state.mutedVideoUids - uid,
                    mutedAudioUids = state.mutedAudioUids - uid,
                )
            }
        }

        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            _uiState.update { state ->
                state.copy(mutedVideoUids = if (muted) state.mutedVideoUids + uid else state.mutedVideoUids - uid)
            }
        }

        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            _uiState.update { state ->
                state.copy(mutedAudioUids = if (muted) state.mutedAudioUids + uid else state.mutedAudioUids - uid)
            }
        }

        override fun onLeaveChannel(stats: IRtcEngineEventHandler.RtcStats?) {
            _uiState.update {
                it.copy(
                    remoteUids = emptyList(),
                    remoteUid = null,
                    isReconnecting = false,
                    mutedVideoUids = emptySet(),
                    mutedAudioUids = emptySet(),
                )
            }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            val session = _uiState.value.session ?: return
            viewModelScope.launch {
                renewRtcToken(session)
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            if (suppressReconnect) return
            _uiState.update { it.copy(isReconnecting = state == Constants.CONNECTION_STATE_RECONNECTING) }
            // Agora recovers RECONNECTING on its own; forcing leave/join mid-recovery drops the stream.
            if (state == Constants.CONNECTION_STATE_FAILED && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts += 1
                val session = _uiState.value.session ?: return
                viewModelScope.launch {
                    delay(RECONNECT_BACKOFF_MS)
                    joinAgoraChannel(session, forceRejoin = true)
                }
            }
            if (state == Constants.CONNECTION_STATE_CONNECTED) {
                reconnectAttempts = 0
            }
        }
    }

    fun getEngine(): RtcEngine? = agoraEngine

    fun joinStream(sessionId: String, context: Context, shareAccessToken: String? = null) {
        if (!LiveLaunchIntent.isValidLiveDocId(sessionId)) {
            _uiState.update { it.copy(isLoading = false, error = "This live link is invalid.") }
            return
        }
        if (agoraEngine != null && currentSessionId != sessionId) {
            leaveStream()
        }
        if (agoraEngine != null && currentSessionId == sessionId) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                applicationContext = context.applicationContext
                val trimmedToken = shareAccessToken?.trim()?.ifBlank { null }
                if (!trimmedToken.isNullOrBlank()) {
                    val resolved = LiveRepository.resolveLiveShareAccess(trimmedToken)
                    if (!resolved.valid) {
                        throw IllegalStateException("This live invite link is invalid or expired.")
                    }
                    if (resolved.sessionId.isNotBlank() && resolved.sessionId != sessionId) {
                        throw IllegalStateException("This live invite link does not match the session.")
                    }
                    shareTokenValidated = true
                } else {
                    shareTokenValidated = false
                }
                currentSessionId = sessionId
                // Re-entering the room must not switch the camera back on for a guest who stepped down.
                leftStageSessionId = sessionId.takeIf { it in leftStageSessionsInProcess }
                currentShareAccessToken = trimmedToken
                suppressReconnect = false
                reconnectAttempts = 0
                // Initialize Agora and then listen to the session for joining logic
                initializeAgora(context.applicationContext)
                listenToSession(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Error joining stream", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private fun initializeAgora(context: Context) {
        try {
            val appId = BuildConfig.AGORA_APP_ID.trim()
            if (appId.isBlank()) {
                throw IllegalStateException(
                    "Agora App ID is missing in app build config. " +
                        "Set agora.appId in local.properties and rebuild."
                )
            }
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = appId
                mEventHandler = rtcEventHandler
            }
            agoraEngine = RtcEngine.create(config)
            agoraEngine?.enableVideo()
            Log.d(TAG, "Agora engine initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Agora initialization failed", e)
            throw e // Propagate the error to be caught in joinStream
        }
    }

    private fun listenToSession(sessionId: String) {
        sessionListener = db.collection(FirestoreCollection.LIVE_SESSIONS).document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val denied = error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    val message = if (denied) {
                        "This stream is unavailable or access has changed. Refresh and try again."
                    } else {
                        "We couldn't load this stream. Check your connection and try again."
                    }
                    if (denied && !_uiState.value.isHost) {
                        // Shared rules stop serving the doc when access narrows, or when a stream ends with a
                        // non-public replay. The listener is dead either way, so stop receiving media too.
                        sessionSnapshotSeq++
                        agoraEngine?.leaveChannel()
                        stopViewerHeartbeat()
                        if (_uiState.value.session != null) {
                            _uiState.update { it.copy(isLoading = false, sessionEnded = true, isOnStage = false) }
                            return@addSnapshotListener
                        }
                    }
                    _uiState.update { it.copy(isLoading = false, error = message) }
                    return@addSnapshotListener
                }
                val snapshotSeq = ++sessionSnapshotSeq
                rawSession = snapshot?.toLiveSession()
                val session = rawSession?.let(::withStageMembership)
                if (session == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Stream not found.", session = null) }
                    return@addSnapshotListener
                }

                val currentUid = auth.currentUser?.uid
                if (!currentUid.isNullOrBlank() &&
                    (session.blockedViewerIds.contains(currentUid) || _uiState.value.blockedViewerIds.contains(currentUid))
                ) {
                    _uiState.update { it.copy(isLoading = false, error = "You cannot join this stream.", session = null) }
                    leaveStream()
                    return@addSnapshotListener
                }

                viewModelScope.launch {
                    // A failed access lookup must deny, not crash the room.
                    val canWatch = runCatching { canCurrentUserWatchSession(session, currentUid) }
                        .onFailure { Log.w(TAG, "Live access check failed", it) }
                        .getOrDefault(false)
                    // A slower check from an older snapshot must not undo a newer one (e.g. rejoin after end).
                    if (snapshotSeq != sessionSnapshotSeq) return@launch
                    if (!canWatch) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = accessDeniedMessage(session.viewAccessMode),
                                session = null
                            )
                        }
                        leaveStream()
                        return@launch
                    }

                    if (session.isEnded) {
                        _uiState.update {
                            it.copy(
                                session = session,
                                sessionEnded = true,
                                isLoading = false,
                                isHost = currentUid == session.hostId
                            )
                        }
                        agoraEngine?.leaveChannel()
                        stopViewerHeartbeat()
                        return@launch
                    }

                    val isHost = currentUid == session.hostId
                    val isOnStage = shouldPublishToStage(session, currentUid)
                    val wasOnStage = _uiState.value.isOnStage
                    val hostAgoraUid = session.hostAgoraUid ?: stableAgoraUid(session.hostId)
                    _uiState.update {
                        it.copy(
                            session = session,
                            isHost = isHost,
                            isOnStage = isOnStage,
                            isLoading = false,
                            sessionEnded = false,
                            hostAgoraUid = hostAgoraUid,
                            remoteUid = preferredMainRemoteUid(it.remoteUids, hostAgoraUid),
                        )
                    }

                    // The host heartbeat updates this doc every minute; only (re)join when the channel
                    // is idle or the stage role changed, never while Agora is connecting/recovering.
                    val connectionState = agoraEngine?.connectionState ?: Constants.CONNECTION_STATE_DISCONNECTED
                    val inChannel = connectionState == Constants.CONNECTION_STATE_CONNECTED ||
                        connectionState == Constants.CONNECTION_STATE_CONNECTING ||
                        connectionState == Constants.CONNECTION_STATE_RECONNECTING
                    val stageRoleChanged = inChannel && isOnStage != wasOnStage
                    val idle = !inChannel &&
                        connectionState != Constants.CONNECTION_STATE_FAILED &&
                        channelJoinJob?.isActive != true &&
                        _uiState.value.error == null
                    if (stageRoleChanged) {
                        // Each stage turn starts with camera and mic on, matching the controls shown.
                        agoraEngine?.muteLocalAudioStream(false)
                        agoraEngine?.muteLocalVideoStream(false)
                        _uiState.update { it.copy(isAudioMuted = false, isVideoMuted = false) }
                    }
                    if (stageRoleChanged || idle) {
                        joinAgoraChannel(session, forceRejoin = stageRoleChanged)
                    }
                    if (!isHost && myStageRequestSessionId != session.sessionId) {
                        listenToMyStageRequest(session.sessionId)
                    }

                    if (isHost) {
                        listenToIncomingRequests(session.sessionId, session.hostId)
                    }

                    // Session docs update often (host heartbeat, settings); subscribe once per room.
                    if (engagementSessionId != session.sessionId) {
                        engagementSessionId = session.sessionId
                        listenToLiveEngagement(session.sessionId)
                        listenToViewers(session.sessionId)
                        listenToBlockedUsers(session.sessionId)
                    }
                    if (viewerHeartbeatJob?.isActive != true && hostHeartbeatJob?.isActive != true) {
                        startViewerHeartbeat(session)
                    }
                }
            }
    }

    private suspend fun canCurrentUserWatchSession(session: LiveSession, currentUid: String?): Boolean {
        if (session.isEnded) return true
        if (currentUid.isNullOrBlank()) return false
        if (currentUid == session.hostId) return true
        if (shareTokenValidated && !currentShareAccessToken.isNullOrBlank()) return true

        return when (session.viewAccessMode) {
            LiveViewAccessMode.PUBLIC -> true
            // Shared rules gate followers-only rooms on users/{host}/followers/{viewer}; reading the
            // viewer's own "following" copy could disagree when one side of a follow write failed.
            LiveViewAccessMode.FOLLOWERS_ONLY -> {
                db.collection(FirestoreCollection.USERS)
                    .document(session.hostId)
                    .collection(FirestoreSubcollection.FOLLOWERS)
                    .document(currentUid)
                    .get()
                    .await()
                    .exists()
            }
            LiveViewAccessMode.INVITE_ONLY -> {
                db.collection(FirestoreCollection.JOIN_REQUESTS)
                    .document("${session.sessionId}_$currentUid")
                    .get()
                    .await()
                    .getString("status")
                    ?.equals(LiveJoinRequestStatus.ACCEPTED.raw, ignoreCase = true) == true
            }
            // The linked event's organizer may always watch (but is not auto-staged like accepted volunteers).
            LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS -> isLinkedEventOrganizer(session, currentUid) || run {
                val accepted = isAcceptedEventVolunteer(session, currentUid)
                if (accepted) {
                    session.linkedEventId?.trim()?.takeIf { it.isNotBlank() }?.let {
                        acceptedEventIds.add(it)
                    }
                }
                accepted
            }
        }
    }

    private fun accessDeniedMessage(mode: LiveViewAccessMode): String {
        return when (mode) {
            LiveViewAccessMode.PUBLIC -> "You cannot open this stream right now."
            LiveViewAccessMode.FOLLOWERS_ONLY -> "This live stream is only available to followers."
            LiveViewAccessMode.INVITE_ONLY -> "You need an approved invite or share link to join this stream."
            LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS ->
                "This live stream is limited to the event's organizer and accepted volunteers."
        }
    }

    // NEW: Listen for incoming join requests
    private fun listenToIncomingRequests(streamId: String, hostId: String) {
        if (requestsListener != null) return  // Already listening

        requestsListener = db.collection(FirestoreCollection.JOIN_REQUESTS)
            .whereEqualTo("streamId", streamId)
            .whereEqualTo("hostId", hostId)
            .whereEqualTo("status", LiveJoinRequestStatus.PENDING.raw)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to join requests", error)
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents
                    ?.mapNotNull { document -> document.toJoinLiveStreamRequest() }
                    ?: emptyList()
                _uiState.update { it.copy(incomingRequests = requests) }
                Log.d(TAG, "Updated incoming requests: ${requests.size} pending")
            }

        acceptedRequestsListener?.remove()
        acceptedRequestsListener = db.collection(FirestoreCollection.JOIN_REQUESTS)
            .whereEqualTo("streamId", streamId)
            .whereEqualTo("hostId", hostId)
            .whereEqualTo("status", LiveJoinRequestStatus.ACCEPTED.raw)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to stage guests", error)
                    return@addSnapshotListener
                }
                acceptedStageGuestIds = snapshot?.documents
                    ?.mapNotNull { it.getString("volunteerId")?.takeIf { id -> id.isNotBlank() } }
                    ?.toSet()
                    ?: emptySet()
                refreshStageMembership()
            }
    }

    private fun withStageMembership(session: LiveSession): LiveSession {
        val uid = auth.currentUser?.uid
        val selfAccepted = uid != null && uid != session.hostId &&
            _uiState.value.myStageRequestStatus.equals(LiveJoinRequestStatus.ACCEPTED.raw, ignoreCase = true)
        var ids = (session.acceptedVolunteerIds + acceptedStageGuestIds + listOfNotNull(uid.takeIf { selfAccepted }))
            .distinct()
        if (uid != null && leftStageSessionId == session.sessionId) ids = ids - uid
        return if (ids == session.acceptedVolunteerIds) session else session.copy(acceptedVolunteerIds = ids)
    }

    /** Re-derives the stage after a join request changes (those don't touch the session doc). */
    private fun refreshStageMembership() {
        val raw = rawSession ?: return
        if (_uiState.value.sessionEnded || raw.isEnded) return
        val uid = auth.currentUser?.uid
        val session = withStageMembership(raw)
        val wasOnStage = _uiState.value.isOnStage
        val isOnStage = shouldPublishToStage(session, uid)
        _uiState.update { it.copy(session = session, isOnStage = isOnStage) }
        val connectionState = agoraEngine?.connectionState ?: Constants.CONNECTION_STATE_DISCONNECTED
        val inChannel = connectionState == Constants.CONNECTION_STATE_CONNECTED ||
            connectionState == Constants.CONNECTION_STATE_CONNECTING ||
            connectionState == Constants.CONNECTION_STATE_RECONNECTING
        if (uid != session.hostId && inChannel && isOnStage != wasOnStage) {
            agoraEngine?.muteLocalAudioStream(false)
            agoraEngine?.muteLocalVideoStream(false)
            _uiState.update { it.copy(isAudioMuted = false, isVideoMuted = false) }
            joinAgoraChannel(session, forceRejoin = true)
        }
    }

    private suspend fun setStageRequestStatus(sessionId: String, volunteerId: String, status: String) {
        db.collection(FirestoreCollection.JOIN_REQUESTS)
            .document("${sessionId}_$volunteerId")
            .update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    private fun listenToMyStageRequest(sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        myStageRequestListener?.remove()
        myStageRequestSessionId = sessionId
        myStageRequestListener = db.collection(FirestoreCollection.JOIN_REQUESTS)
            .document("${sessionId}_$uid")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Own stage request listener failed", error)
                    return@addSnapshotListener
                }
                val status = snapshot?.takeIf { it.exists() }?.getString("status")
                _uiState.update { it.copy(myStageRequestStatus = status) }
                // A new acceptance is worth one more publisher-token attempt.
                if (status.equals(LiveJoinRequestStatus.ACCEPTED.raw, ignoreCase = true)) {
                    stageDeniedSessionId = null
                }
                refreshStageMembership()
            }
    }

    /** Stage guest: step down to the audience (local; the host's accepted grant stays until they remove you). */
    fun leaveStage() {
        val session = _uiState.value.session ?: return
        val uid = auth.currentUser?.uid ?: return
        if (_uiState.value.isHost || !_uiState.value.isOnStage) return
        viewModelScope.launch {
            try {
                agoraEngine?.muteLocalAudioStream(true)
                agoraEngine?.muteLocalVideoStream(true)
                // Shared rules don't let a guest edit the stage list or an accepted request, so
                // stepping down rejoins as audience locally; "Join stage" brings them back.
                leftStageSessionId = session.sessionId
                leftStageSessionsInProcess += session.sessionId
                refreshStageMembership()
                _uiState.update { it.copy(statusMessage = "You left the stage.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave stage", e)
                agoraEngine?.muteLocalAudioStream(_uiState.value.isAudioMuted)
                agoraEngine?.muteLocalVideoStream(_uiState.value.isVideoMuted)
                _uiState.update { it.copy(statusMessage = userMessageForLivePermissionFailure(e, "leave the stage")) }
            }
        }
    }

    /** Host-only: revoke a guest's stage access so the server stops issuing publisher tokens. */
    fun removeFromStage(userId: String) {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost || userId.isBlank() || userId == session.hostId) return
        viewModelScope.launch {
            try {
                // The accepted request is the stage grant under shared rules; rejecting it revokes it.
                setStageRequestStatus(session.sessionId, userId, LiveJoinRequestStatus.REJECTED.raw)
                _uiState.update { it.copy(statusMessage = "Removed from stage.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove guest from stage", e)
                _uiState.update { it.copy(statusMessage = "Could not remove this guest from the stage.") }
            }
        }
    }

    private fun listenToLiveEngagement(streamId: String) {
        likesListener?.remove()
        commentsListener?.remove()

        val likesRef = db.collection(FirestoreCollection.LIVE_SESSIONS).document(streamId).collection(FirestoreSubcollection.LIKES)
        likesListener = likesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to live likes", error)
                return@addSnapshotListener
            }

            val likeDocs = snapshot?.documents ?: emptyList()
            val currentUserId = auth.currentUser?.uid
            val likedByCurrentUser = if (currentUserId.isNullOrBlank()) {
                false
            } else {
                likeDocs.any { it.id == currentUserId || it.getString("userId") == currentUserId }
            }

            _uiState.update {
                it.copy(
                    liveLikesCount = likeDocs.size.toLong(),
                    hasLikedLive = likedByCurrentUser
                )
            }
        }

        val commentsRef = db.collection(FirestoreCollection.LIVE_SESSIONS).document(streamId).collection(FirestoreSubcollection.COMMENTS)
        commentsListener = commentsRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(LIVE_CHAT_WINDOW)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to live comments", error)
                    return@addSnapshotListener
                }

                val comments = snapshot?.documents?.map { doc ->
                    LiveRoomComment(
                        id = doc.id,
                        authorId = doc.getString("authorId").orEmpty(),
                        authorName = doc.getString("authorName").orEmpty().ifBlank { "Anonymous" },
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt"),
                        replyToCommentId = doc.getString("replyToCommentId")?.trim()?.ifBlank { null },
                        replyToAuthorName = doc.getString("replyToAuthorName")?.trim()?.ifBlank { null },
                        replyToText = doc.getString("replyToText")?.trim()?.ifBlank { null },
                        authorPhotoUrl = doc.getString("authorPhotoUrl")?.trim()?.ifBlank { null },
                    )
                }?.filterNot { it.id in hostHiddenCommentIds }
                    // createdAt isn't pinned to request.time by the rules; future-dated rows would sit pinned at the bottom.
                    ?.filterNot { comment ->
                        val created = comment.createdAt?.toDate()?.time ?: return@filterNot false
                        created - System.currentTimeMillis() > CLIENT_CLOCK_SKEW_MS
                    }
                    ?: emptyList()

                _uiState.update {
                    it.copy(
                        liveCommentsCount = maxOf(comments.size.toLong(), if (comments.size >= LIVE_CHAT_WINDOW) it.liveCommentsCount else 0L),
                        liveComments = comments
                    )
                }
                if (comments.size >= LIVE_CHAT_WINDOW) {
                    refreshTotalCommentCount(commentsRef)
                }
            }
    }

    /** The chat listener only keeps the latest window, so busy rooms read the true total from an aggregate. */
    private fun refreshTotalCommentCount(commentsRef: com.google.firebase.firestore.CollectionReference) {
        if (commentCountJob?.isActive == true) return
        commentCountJob = viewModelScope.launch {
            runCatching {
                commentsRef.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            }.onSuccess { total ->
                _uiState.update { it.copy(liveCommentsCount = maxOf(total, it.liveComments.size.toLong())) }
            }.onFailure {
                Log.w(TAG, "Failed to count live comments", it)
            }
            delay(COMMENT_COUNT_REFRESH_MS)
        }
    }

    private fun listenToViewers(sessionId: String) {
        viewersListener?.remove()
        viewersListener = db.collection(FirestoreCollection.LIVE_SESSIONS)
            .document(sessionId)
            .collection(FirestoreSubcollection.VIEWERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to live viewers", error)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val activeViewers = snapshot?.documents?.mapNotNull { doc ->
                    val lastSeen = doc.getTimestamp("lastSeenAt")?.toDate()?.time ?: return@mapNotNull null
                    if (now - lastSeen > VIEWER_PRESENCE_STALE_MS) return@mapNotNull null
                    // lastSeenAt isn't pinned to request.time by the rules; a future value would never go stale.
                    if (lastSeen - now > CLIENT_CLOCK_SKEW_MS) return@mapNotNull null
                    LiveRoomViewer(
                        userId = doc.id,
                        displayName = doc.getString("displayName").orEmpty().ifBlank { "Viewer" },
                        role = doc.getString("role").orEmpty().ifBlank { "viewer" },
                    )
                } ?: emptyList()
                val watchingCount = activeViewers.count { !it.role.equals("host", ignoreCase = true) }.toLong()
                _uiState.update { state ->
                    state.copy(
                        session = state.session?.copy(viewerCount = activeViewers.size.toLong()),
                        roomViewers = activeViewers,
                        liveViewerCount = watchingCount,
                        peakViewerCount = maxOf(state.peakViewerCount, watchingCount),
                    )
                }
            }
    }

    private fun listenToBlockedUsers(sessionId: String) {
        blockedUsersListener?.remove()
        val blockedUsers = db.collection(FirestoreCollection.LIVE_SESSIONS)
            .document(sessionId)
            .collection(FirestoreSubcollection.BLOCKED_USERS)
        val applyBlockedState: (Set<String>) -> Unit = { blockedIds ->
            val currentUid = auth.currentUser?.uid
            if (!currentUid.isNullOrBlank() && blockedIds.contains(currentUid)) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "You cannot join this stream.",
                        session = null,
                        blockedViewerIds = blockedIds,
                    )
                }
                leaveStream()
            } else {
                _uiState.update { it.copy(blockedViewerIds = blockedIds) }
            }
        }
        blockedUsersListener = if (_uiState.value.isHost) {
            blockedUsers.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to blocked viewers", error)
                    return@addSnapshotListener
                }
                val blockedIds = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                applyBlockedState(blockedIds)
            }
        } else {
            val currentUid = auth.currentUser?.uid ?: return
            blockedUsers.document(currentUid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error checking blocked viewer state", error)
                    return@addSnapshotListener
                }
                applyBlockedState(if (snapshot?.exists() == true) setOf(currentUid) else emptySet())
            }
        }
    }

    // NEW: Accept a volunteer's request to join
    fun acceptJoinRequest(request: JoinLiveStreamRequest) {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost || _uiState.value.sessionEnded || request.streamId != session.sessionId) return
        if (request.volunteerId in _uiState.value.blockedViewerIds) {
            _uiState.update { it.copy(statusMessage = "This viewer is blocked. Unblock them before bringing them on stage.") }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(requestsLoading = true) }

                // Shared rules: the accepted request alone puts the guest on stage (no stage-list write).
                db.collection(FirestoreCollection.JOIN_REQUESTS).document(request.requestId)
                    .update(mapOf(
                        "status" to LiveJoinRequestStatus.ACCEPTED.raw,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                    .await()

                Log.d(TAG, "Accepted join request from ${request.volunteerId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting join request", e)
                _uiState.update { it.copy(statusMessage = userMessageForLivePermissionFailure(e, "accept this stage request")) }
            } finally {
                _uiState.update { it.copy(requestsLoading = false) }
            }
        }
    }

    // NEW: Reject a volunteer's request to join
    fun rejectJoinRequest(request: JoinLiveStreamRequest) {
        if (!_uiState.value.isHost) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(requestsLoading = true) }

                db.collection(FirestoreCollection.JOIN_REQUESTS).document(request.requestId)
                    .update(mapOf(
                        "status" to LiveJoinRequestStatus.REJECTED.raw,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                    .await()

                Log.d(TAG, "Rejected join request from ${request.volunteerId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting join request", e)
                _uiState.update { it.copy(statusMessage = userMessageForLivePermissionFailure(e, "reject this stage request")) }
            } finally {
                _uiState.update { it.copy(requestsLoading = false) }
            }
        }
    }

    private fun shouldPublishToStage(session: LiveSession, uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false
        if (uid == session.hostId) return true
        if (leftStageSessionId == session.sessionId) return false
        if (stageDeniedSessionId == session.sessionId) return false
        return when (session.stageAccessMode) {
            LiveStageAccessMode.HOST_ONLY -> false
            LiveStageAccessMode.REQUEST_TO_JOIN,
            LiveStageAccessMode.APPROVED_VOLUNTEERS,
            LiveStageAccessMode.OPEN_TO_ACCEPTED_VOLUNTEERS ->
                session.acceptedVolunteerIds.contains(uid) ||
                    (session.stageAccessMode == LiveStageAccessMode.OPEN_TO_ACCEPTED_VOLUNTEERS &&
                        session.linkedEventId?.trim()?.takeIf { it.isNotBlank() } in acceptedEventIds)
        }
    }

    private suspend fun isLinkedEventOrganizer(session: LiveSession, currentUid: String): Boolean {
        val eventId = session.linkedEventId?.trim().orEmpty().ifBlank {
            session.sourceId?.trim().orEmpty()
        }
        if (eventId.isBlank()) return false
        return runCatching {
            val event = db.collection(FirestoreCollection.EVENTS).document(eventId).get().await()
            event.getString("organizerId") == currentUid || event.getString("organizerUid") == currentUid
        }.onFailure { Log.w(TAG, "Linked event organizer check failed", it) }
            .getOrDefault(false)
    }

    private suspend fun isAcceptedEventVolunteer(session: LiveSession, currentUid: String): Boolean {
        val eventId = session.linkedEventId?.trim().orEmpty().ifBlank {
            session.sourceId?.trim().orEmpty()
        }
        if (eventId.isBlank()) return false
        // Applications only have a collection-group index on volunteerId (none on eventId),
        // so filter the event locally instead of a two-field collection-group query.
        val snapshots = runCatching {
            db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                .whereEqualTo("volunteerId", currentUid)
                .get()
                .await()
        }.onFailure { Log.w(TAG, "Accepted event volunteer check failed", it) }
            .getOrNull() ?: return false
        return snapshots.documents.any { doc ->
            val status = doc.getString("status").orEmpty()
            // Same statuses the shared rules accept for event-volunteer rooms.
            doc.getString("eventId")?.trim() == eventId &&
                status.lowercase() in setOf("accepted", "approved", "attended", "completed")
        }
    }

    private fun joinAgoraChannel(session: LiveSession, forceRejoin: Boolean = false) {
        val channelName = session.resolvedChannelName
        val publish = shouldPublishToStage(session, auth.currentUser?.uid)
        if (publish) {
            val precheck = applicationContext?.let(LiveBroadcastPrecheck::evaluate)
            if (precheck != null && (!precheck.cameraGranted || !precheck.microphoneGranted)) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        needsBroadcastPermissions = true,
                        error = null,
                    )
                }
                return
            }
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        channelJoinJob?.cancel()
        channelJoinJob = viewModelScope.launch {
            try {
                val firebaseUid = auth.currentUser?.uid
                    ?: throw IllegalStateException("You must be logged in to join a stream.")
                val rtcRole = LiveRtcJoinRole.publisherIf(publish)
                val credentials = LiveRepository.fetchAgoraRtcCredentials(
                    sessionId = session.sessionId,
                    channelName = channelName,
                    role = rtcRole,
                    uid = stableAgoraUid(firebaseUid),
                    shareAccessToken = currentShareAccessToken,
                )
                val token = credentials.token
                // The token is bound to the uid the server returned; joining with any other uid fails.
                val localUid = credentials.uid
                joinedAgoraUid = localUid
                Log.d(TAG, "Successfully fetched production token.")

                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    clientRoleType = if (publish) {
                        Constants.CLIENT_ROLE_BROADCASTER
                    } else {
                        Constants.CLIENT_ROLE_AUDIENCE
                    }
                    publishCameraTrack = publish
                    publishMicrophoneTrack = publish
                }

                if (publish) {
                    agoraEngine?.startPreview()
                } else {
                    // A guest removed from stage must stop capturing before rejoining as audience.
                    agoraEngine?.stopPreview()
                }

                if (forceRejoin) {
                    agoraEngine?.leaveChannel()
                }
                val result = agoraEngine?.joinChannel(token, channelName, localUid, options)

                if (result != 0) {
                    Log.e(TAG, "Could not join channel, error code: $result")
                    val message = "We couldn't connect to the live stream. Please try again."
                    _uiState.update { it.copy(isLoading = false, error = message) }
                } else {
                    Log.d(TAG, "Join channel command sent successfully.")
                    _uiState.update { it.copy(isLoading = false, needsBroadcastPermissions = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get production token", e)
                // The server is the authority on stage access (accepted request). If it refuses a
                // publisher token, keep the person in the room as a viewer instead of failing the join.
                if (publish &&
                    e is FirebaseFunctionsException &&
                    e.code == FirebaseFunctionsException.Code.PERMISSION_DENIED &&
                    auth.currentUser?.uid != session.hostId
                ) {
                    stageDeniedSessionId = session.sessionId
                    _uiState.update {
                        it.copy(
                            isOnStage = false,
                            statusMessage = "You're watching as a viewer. Request the stage to join the host.",
                        )
                    }
                    joinAgoraChannel(session, forceRejoin = forceRejoin)
                    return@launch
                }
                val message = userMessageForLivePermissionFailure(e, "join this stream")
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }

    fun onBroadcastPermissionsResult(context: Context) {
        applicationContext = context.applicationContext
        val precheck = LiveBroadcastPrecheck.evaluate(context)
        if (!precheck.cameraGranted || !precheck.microphoneGranted) {
            _uiState.update {
                it.copy(
                    needsBroadcastPermissions = false,
                    error = "Camera and microphone access are required to join the stage. Enable both in Android Settings and try again.",
                )
            }
            return
        }
        _uiState.update { it.copy(needsBroadcastPermissions = false, error = null) }
        _uiState.value.session?.let { session -> joinAgoraChannel(session, forceRejoin = true) }
    }

    fun retryStageJoin(context: Context) {
        applicationContext = context.applicationContext
        val session = _uiState.value.session ?: return
        val precheck = LiveBroadcastPrecheck.evaluate(context)
        if (!precheck.cameraGranted || !precheck.microphoneGranted) {
            _uiState.update {
                it.copy(error = "Camera and microphone access are required to join the stage. Enable both in Android Settings and try again.")
            }
            return
        }
        _uiState.update { it.copy(error = null) }
        joinAgoraChannel(session, forceRejoin = true)
    }

    fun retryJoin(context: Context) {
        applicationContext = context.applicationContext
        val session = _uiState.value.session ?: return
        if (agoraEngine == null) {
            runCatching { initializeAgora(context.applicationContext) }.onFailure { e ->
                _uiState.update { it.copy(error = userMessageForLivePermissionFailure(e, "join this stream")) }
                return
            }
        }
        suppressReconnect = false
        reconnectAttempts = 0
        _uiState.update { it.copy(error = null) }
        joinAgoraChannel(session, forceRejoin = true)
    }

    private suspend fun renewRtcToken(session: LiveSession) {
        val firebaseUid = auth.currentUser?.uid ?: return
        // A renewed token must be for the uid already in the channel.
        val localUid = joinedAgoraUid ?: stableAgoraUid(firebaseUid)
        val publish = shouldPublishToStage(session, firebaseUid)
        val rtcRole = LiveRtcJoinRole.publisherIf(publish)
        runCatching {
            LiveRepository.fetchAgoraRtcCredentials(
                sessionId = session.sessionId,
                channelName = session.resolvedChannelName,
                role = rtcRole,
                uid = localUid,
                shareAccessToken = currentShareAccessToken,
            )
        }.onSuccess { credentials ->
            if (credentials.uid != localUid) {
                // The server moved this account to a new uid; a renew can't change uid, so rejoin.
                Log.w(TAG, "Token uid changed on renew; rejoining channel")
                joinAgoraChannel(session, forceRejoin = true)
            } else if (credentials.token.isNotBlank()) {
                agoraEngine?.renewToken(credentials.token)
            }
        }.onFailure {
            Log.e(TAG, "Failed to renew Agora token", it)
        }
    }

    fun toggleChromeVisibility() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun setHostToolsVisible(visible: Boolean) {
        _uiState.update { it.copy(showHostTools = visible) }
    }

    fun shareLiveLink(onReady: (String) -> Unit) {
        val session = _uiState.value.session ?: return
        val sessionId = session.sessionId
        val isHost = _uiState.value.isHost
        viewModelScope.launch {
            _uiState.update { it.copy(shareInProgress = true, shareError = null) }
            try {
                val url = when {
                    isHost -> {
                        runCatching {
                            LiveRepository.createLiveShareAccessLink(sessionId)
                        }.getOrElse { error ->
                            if (session.viewAccessMode == LiveViewAccessMode.PUBLIC) {
                                Log.w(TAG, "Share callable unavailable; using app deep link fallback", error)
                                LiveShareConstants.appDeepLink(
                                    sessionId = sessionId,
                                    hostId = session.hostId,
                                )
                            } else {
                                throw error
                            }
                        }
                    }
                    session.viewAccessMode == LiveViewAccessMode.PUBLIC -> {
                        LiveShareConstants.appDeepLink(
                            sessionId = sessionId,
                            hostId = session.hostId,
                        )
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                shareError = "Only the host can share invite links for this private stream."
                            )
                        }
                        return@launch
                    }
                }
                onReady(url)
            } catch (e: Exception) {
                Log.e(TAG, "Share link failed", e)
                _uiState.update {
                    it.copy(shareError = userMessageForLivePermissionFailure(e, "create a share link"))
                }
            } finally {
                _uiState.update { it.copy(shareInProgress = false) }
            }
        }
    }

    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }

    fun clearShareError() = _uiState.update { it.copy(shareError = null) }

    /** Back to the still-live room after a failed end, so the host can retry instead of abandoning it. */
    fun dismissEndStreamError() = _uiState.update { it.copy(error = null) }

    /** Host leaves via Back/Close without ending accidentally — callers should confirm before [endStream]. */
    fun leaveRoom() {
        if (_uiState.value.isHost && !_uiState.value.sessionEnded) {
            // Prefer ending so the room does not stay LIVE for viewers.
            endStream()
        } else {
            leaveStream()
        }
    }

    fun toggleChatEnabled() {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost) return
        viewModelScope.launch {
            try {
                val next = !session.chatEnabled
                db.collection(FirestoreCollection.LIVE_SESSIONS).document(session.sessionId)
                    .update(
                        mapOf(
                            "chatEnabled" to next,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle chat", e)
                _uiState.update { it.copy(statusMessage = "Could not update chat setting") }
            }
        }
    }

    fun blockViewer(userId: String) {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost || userId.isBlank() || userId == session.hostId) return
        viewModelScope.launch {
            try {
                val blockedRef = db.collection(FirestoreCollection.LIVE_SESSIONS)
                    .document(session.sessionId)
                    .collection(FirestoreSubcollection.BLOCKED_USERS)
                    .document(userId)
                val hostUid = auth.currentUser?.uid ?: return@launch
                val blockedName = (
                    _uiState.value.roomViewers.firstOrNull { it.userId == userId }?.displayName
                        ?: _uiState.value.liveComments.lastOrNull { it.authorId == userId }?.authorName
                    )?.takeIf { it.isNotBlank() }?.take(120) ?: "Viewer"
                // Shared rules: exact key set; comments can't be deleted and the stage list is
                // server-owned, so blocking also rejects their stage request (revokes publishing).
                blockedRef.set(
                    mapOf(
                        "userId" to userId,
                        "displayName" to blockedName,
                        "blockedByUid" to hostUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                runCatching {
                    setStageRequestStatus(session.sessionId, userId, LiveJoinRequestStatus.REJECTED.raw)
                }.onFailure { Log.w(TAG, "No stage request to revoke for blocked viewer", it) }
                _uiState.update { it.copy(statusMessage = "Viewer blocked.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to block viewer", e)
                _uiState.update { it.copy(statusMessage = "Could not block viewer") }
            }
        }
    }

    fun unblockViewer(userId: String) {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost || userId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection(FirestoreCollection.LIVE_SESSIONS)
                    .document(session.sessionId)
                    .collection(FirestoreSubcollection.BLOCKED_USERS)
                    .document(userId)
                .delete()
                .await()
                _uiState.update { it.copy(statusMessage = "Viewer unblocked.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unblock viewer", e)
                _uiState.update { it.copy(statusMessage = "Could not unblock viewer") }
            }
        }
    }

    fun shareToMindLoom() {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost) {
            _uiState.update { it.copy(shareError = "Only the host can share this stream to MindLoom.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(mindLoomShareInProgress = true, shareError = null) }
            try {
                val shareUrl = MindLoomLivePostWriter.resolveLiveShareUrl(session)
                MindLoomLivePostWriter.postLiveSession(session, shareUrl).getOrThrow()
                _uiState.update { it.copy(statusMessage = "Shared to MindLoom.") }
            } catch (e: Exception) {
                Log.e(TAG, "MindLoom share failed", e)
                _uiState.update {
                    it.copy(shareError = userMessageForLivePermissionFailure(e, "share to MindLoom"))
                }
            } finally {
                _uiState.update { it.copy(mindLoomShareInProgress = false) }
            }
        }
    }

    fun shareReplayToMindLoom() {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost) {
            _uiState.update { it.copy(shareError = "Only the host can post the replay to MindLoom.") }
            return
        }
        if (!session.isArchiveReady) {
            _uiState.update { it.copy(shareError = "Replay is still processing. Try again shortly.") }
            return
        }
        if (session.replayVisibility == LiveReplayVisibility.OWNER_ONLY) {
            _uiState.update {
                it.copy(shareError = "Change replay visibility before posting it to MindLoom.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(mindLoomReplayShareInProgress = true, shareError = null) }
            try {
                val replayUrl = LiveRepository.createLiveReplayAccessLink(
                    sessionId = session.sessionId,
                    purpose = if (session.replayVisibility == LiveReplayVisibility.SHARED_LINK) "share" else null,
                )
                MindLoomLivePostWriter.postLiveReplay(session, replayUrl).getOrThrow()
                _uiState.update { it.copy(statusMessage = "Replay posted to MindLoom.") }
            } catch (e: Exception) {
                Log.e(TAG, "MindLoom replay post failed", e)
                _uiState.update {
                    it.copy(shareError = userMessageForLivePermissionFailure(e, "post replay to MindLoom"))
                }
            } finally {
                _uiState.update { it.copy(mindLoomReplayShareInProgress = false) }
            }
        }
    }

    fun shareReplayLink(onReady: (String) -> Unit) {
        val sessionId = _uiState.value.session?.sessionId ?: return
        if (!_uiState.value.isHost) {
            _uiState.update { it.copy(shareError = "Only the host can share the replay link.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(shareInProgress = true, shareError = null) }
            try {
                val url = LiveRepository.createLiveReplayAccessLink(sessionId, purpose = "share")
                onReady(url)
            } catch (e: Exception) {
                Log.e(TAG, "Replay share failed", e)
                _uiState.update {
                    it.copy(shareError = userMessageForLivePermissionFailure(e, "create a replay link"))
                }
            } finally {
                _uiState.update { it.copy(shareInProgress = false) }
            }
        }
    }

    fun updateReplayVisibility(mode: LiveReplayVisibility) {
        val session = _uiState.value.session ?: return
        if (!_uiState.value.isHost) return
        viewModelScope.launch {
            try {
                db.collection(FirestoreCollection.LIVE_SESSIONS).document(session.sessionId)
                    .update("replayVisibility", mode.raw)
                    .await()
                _uiState.update { it.copy(statusMessage = "Replay visibility: ${mode.label}") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update replay visibility", e)
                _uiState.update { it.copy(statusMessage = "Could not update replay visibility") }
            }
        }
    }

    fun requestJoinStage() {
        val session = _uiState.value.session ?: return
        // Shared rules (allowsLiveStageJoinRequest) accept requests in every mode except host-only.
        if (session.stageAccessMode == LiveStageAccessMode.HOST_ONLY) return
        val currentUser = auth.currentUser ?: return
        if (_uiState.value.myStageRequestStatus.equals(LiveJoinRequestStatus.PENDING.raw, ignoreCase = true)) {
            _uiState.update { it.copy(statusMessage = "Your request is waiting for the host.") }
            return
        }
        // Shared rules don't let a rejected requester flip back to pending and re-notify the host.
        if (_uiState.value.myStageRequestStatus.equals(LiveJoinRequestStatus.REJECTED.raw, ignoreCase = true)) {
            _uiState.update { it.copy(statusMessage = "The host declined your stage request for this live.") }
            return
        }
        // Still accepted after stepping down: rejoin the stage without a new request.
        // Also covers an earlier refused publisher token; rewriting a decided request is denied by the rules.
        if (_uiState.value.myStageRequestStatus.equals(LiveJoinRequestStatus.ACCEPTED.raw, ignoreCase = true)) {
            stageDeniedSessionId = null
            leftStageSessionId = null
            leftStageSessionsInProcess -= session.sessionId
            refreshStageMembership()
            _uiState.update { it.copy(statusMessage = "You're back on stage.") }
            return
        }
        viewModelScope.launch {
            try {
                val requestId = "${session.sessionId}_${currentUser.uid}"
                db.collection(FirestoreCollection.JOIN_REQUESTS).document(requestId)
                    .set(liveJoinRequestPayload(db, currentUser, session.sessionId, session.hostId))
                    .await()
                // A fresh request is an explicit opt-in, so the next acceptance should put them on stage.
                leftStageSessionId = null
                leftStageSessionsInProcess -= session.sessionId
                _uiState.update { it.copy(statusMessage = "Request sent. The host will bring you on stage.") }
            } catch (e: Exception) {
                Log.e(TAG, "Stage request failed", e)
                _uiState.update { it.copy(statusMessage = "Could not request to join stage") }
            }
        }
    }

    fun toggleSpeakerMute() {
        val muted = !_uiState.value.isSpeakerMuted
        agoraEngine?.muteAllRemoteAudioStreams(muted)
        _uiState.update { it.copy(isSpeakerMuted = muted) }
    }

    private fun stableAgoraUid(firebaseUid: String): Int {
        val hash = firebaseUid.hashCode()
        if (hash == Int.MIN_VALUE) return 1
        return abs(hash).coerceAtLeast(1)
    }

    /**
     * Marks the session ended before the owning screen is allowed to close. This prevents a
     * host navigation event from cancelling the ENDED write and leaving a stale LIVE session.
     */
    fun endStream(onComplete: (() -> Unit)? = null, keepRoomForSummary: Boolean = false) {
        val state = _uiState.value
        if (!state.isHost || state.session == null) return

        if (keepRoomForSummary) {
            viewModelScope.launch {
                _uiState.update { it.copy(error = null) }
                try {
                    // An unacknowledged write would leave the host stuck on a live room with no feedback.
                    withTimeout(END_STREAM_TIMEOUT_MS) {
                        db.collection(FirestoreCollection.LIVE_SESSIONS).document(state.session.sessionId)
                            .update(
                                // Shared rules allow only status/endedAt/updatedAt here; peak viewers stay in the summary.
                                mapOf(
                                    "status" to "ENDED",
                                    "endedAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                )
                            )
                            .await()
                    }
                    val latest = _uiState.value
                    val startedAtMs = (state.session.startTime ?: state.session.createdAt)?.time
                    _uiState.update {
                        it.copy(
                            sessionEnded = true,
                            endSummary = LiveEndSummary(
                                durationMs = startedAtMs?.let { start ->
                                    (System.currentTimeMillis() - start).coerceAtLeast(0L)
                                } ?: 0L,
                                peakViewers = latest.peakViewerCount,
                                likes = latest.liveLikesCount,
                                comments = latest.liveCommentsCount,
                            ),
                        )
                    }
                    // Stay on the session listener so the replay status updates on the summary.
                    stopViewerHeartbeat()
                    suppressReconnect = true
                    agoraEngine?.stopPreview()
                    agoraEngine?.leaveChannel()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update stream status to ended", e)
                    _uiState.update {
                        it.copy(error = "We couldn't end the stream. Check your connection and try again.")
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            var ended = false
            try {
                withTimeout(END_STREAM_TIMEOUT_MS) {
                    db.collection(FirestoreCollection.LIVE_SESSIONS).document(state.session.sessionId)
                        .update(
                            mapOf(
                                "status" to "ENDED",
                                "endedAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                        .await()
                }
                ended = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update stream status to ended", e)
                _uiState.update {
                    it.copy(error = "We couldn't end the stream. Check your connection and try again.")
                }
            } finally {
                if (ended) {
                    if (onComplete == null) {
                        leaveStream()
                    } else {
                        onComplete()
                    }
                }
            }
        }
    }

    // --- NEWLY UPDATED `leaveStream` FUNCTION ---
    fun leaveStream() {
        Log.d(TAG, "Leaving stream and cleaning up resources...")

        sessionListener?.remove()
        sessionListener = null
        sessionSnapshotSeq++

        requestsListener?.remove()  // NEW: Remove requests listener
        requestsListener = null
        acceptedRequestsListener?.remove()
        acceptedRequestsListener = null
        acceptedStageGuestIds = emptySet()
        rawSession = null
        leftStageSessionId = null
        stageDeniedSessionId = null

        likesListener?.remove()
        likesListener = null

        commentsListener?.remove()
        commentsListener = null

        viewersListener?.remove()
        viewersListener = null

        blockedUsersListener?.remove()
        blockedUsersListener = null

        myStageRequestListener?.remove()
        myStageRequestListener = null
        myStageRequestSessionId = null
        joinedAgoraUid = null
        channelJoinJob?.cancel()
        channelJoinJob = null
        commentCountJob?.cancel()
        commentCountJob = null

        stopViewerHeartbeat()
        engagementSessionId = null

        agoraEngine?.stopPreview()
        suppressReconnect = true
        agoraEngine?.leaveChannel()

        RtcEngine.destroy()
        agoraEngine = null
        currentSessionId = null
        currentShareAccessToken = null
        shareTokenValidated = false

        Log.d(TAG, "Cleanup complete.")
    }

    fun toggleAudio() {
        val muted = !_uiState.value.isAudioMuted
        agoraEngine?.muteLocalAudioStream(muted)
        _uiState.update { it.copy(isAudioMuted = muted) }
    }

    fun toggleVideo() {
        val muted = !_uiState.value.isVideoMuted
        agoraEngine?.muteLocalVideoStream(muted)
        _uiState.update { it.copy(isVideoMuted = muted) }
    }

    fun switchCamera() {
        agoraEngine?.switchCamera()
    }

    fun toggleLiveLike() {
        val streamId = _uiState.value.session?.sessionId ?: return
        val currentUserId = auth.currentUser?.uid ?: return
        val likeDocRef = db.collection(FirestoreCollection.LIVE_SESSIONS)
            .document(streamId)
            .collection(FirestoreSubcollection.LIKES)
            .document(currentUserId)
        if (likeInFlight || _uiState.value.sessionEnded) return
        likeInFlight = true

        viewModelScope.launch {
            try {
                if (_uiState.value.hasLikedLive) {
                    likeDocRef.delete().await()
                } else {
                    likeDocRef
                        .set(
                            mapOf(
                                "userId" to currentUserId,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle live like", e)
                _uiState.update {
                    it.copy(statusMessage = userMessageForLivePermissionFailure(e, "update your reaction"))
                }
            } finally {
                likeInFlight = false
            }
        }
    }

    fun postLiveComment(text: String, replyTo: LiveRoomComment? = null) {
        if (_uiState.value.session?.chatEnabled == false) {
            _uiState.update { it.copy(statusMessage = "Chat is paused by the host.") }
            return
        }
        val message = text.trim()
        if (message.isBlank()) return
        if (message.length > 500) {
            _uiState.update { it.copy(statusMessage = "Comment is too long (max 500).") }
            return
        }

        val streamId = _uiState.value.session?.sessionId ?: return
        val user = auth.currentUser ?: return
        if (_uiState.value.sessionEnded) {
            _uiState.update { it.copy(statusMessage = "Chat closed when the stream ended.") }
            return
        }
        val nowMs = System.currentTimeMillis()
        if (!_uiState.value.isHost && nowMs - lastCommentSentAtMs < COMMENT_SLOW_MODE_MS) {
            _uiState.update { it.copy(statusMessage = "Slow down — you can send another message in a moment.") }
            return
        }
        lastCommentSentAtMs = nowMs

        viewModelScope.launch {
            try {
                // Shared rules accept exactly authorId/authorName/text/createdAt, with authorName
                // matching the profile; replies are expressed YouTube-style as an @mention.
                val authorName = LiveIdentity.displayName(db, user)
                val text = replyTo?.takeIf { it.id.isNotBlank() }?.let { parent ->
                    "@${parent.authorName.ifBlank { "Anonymous" }} $message"
                }?.take(500) ?: message
                val payload = mutableMapOf<String, Any>(
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "text" to text,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                db.collection(FirestoreCollection.LIVE_SESSIONS)
                    .document(streamId)
                    .collection(FirestoreSubcollection.COMMENTS)
                    .add(payload)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post live comment", e)
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    LiveIdentity.invalidate(user.uid)
                }
                _uiState.update {
                    it.copy(statusMessage = userMessageForLivePermissionFailure(e, "send this comment"))
                }
            }
        }
    }

    private fun userMessageForLivePermissionFailure(error: Throwable, action: String): String {
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            return "We couldn't $action because access to this stream changed. Refresh and try again."
        }
        if (error is FirebaseFunctionsException &&
            error.code == FirebaseFunctionsException.Code.PERMISSION_DENIED
        ) {
            return error.message?.takeIf { it.isNotBlank() }
                ?: "We couldn't $action because this stream is restricted."
        }
        return "We couldn't $action. Check your connection and try again."
    }

    /**
     * Keeps the host's session fresh so the stale-session reconciler never ends a healthy stream,
     * and publishes the live audience size for discovery cards.
     */
    private fun startHostHeartbeat(session: LiveSession) {
        hostHeartbeatJob?.cancel()
        val sessionId = session.sessionId
        hostHeartbeatJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                if (state.sessionEnded) break
                // Shared rules only let the host touch updatedAt mid-stream (no heartbeat/counter
                // fields); the server stale-session reconciler keys off status + updatedAt.
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                runCatching {
                    db.collection(FirestoreCollection.LIVE_SESSIONS)
                        .document(sessionId)
                        .update(updates)
                        .await()
                }.onFailure {
                    Log.w(TAG, "Failed to write host heartbeat", it)
                }
                delay(HOST_HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun startViewerHeartbeat(session: LiveSession) {
        if (_uiState.value.isHost) {
            startHostHeartbeat(session)
            return
        }
        val user = auth.currentUser ?: return
        viewerHeartbeatJob?.cancel()
        val sessionId = session.sessionId
        viewerHeartbeatJob = viewModelScope.launch {
            var consecutiveDenied = 0
            while (true) {
                val displayName = LiveIdentity.displayName(db, user)
                runCatching {
                    // Shared rules: fixed key set, role HOST/VIEWER, displayName must match the profile.
                    db.collection(FirestoreCollection.LIVE_SESSIONS)
                        .document(sessionId)
                        .collection(FirestoreSubcollection.VIEWERS)
                        .document(user.uid)
                        .set(
                            mapOf(
                                "userId" to user.uid,
                                "displayName" to displayName,
                                "role" to "VIEWER",
                                "lastSeenAt" to FieldValue.serverTimestamp(),
                                "isVisibleToAudience" to true,
                            )
                        )
                        .await()
                }.onSuccess {
                    consecutiveDenied = 0
                }.onFailure {
                    Log.w(TAG, "Failed to write viewer heartbeat", it)
                    if (it is FirebaseFirestoreException &&
                        it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ) {
                        // Usually a renamed profile; refresh the name, but don't retry a denied write forever.
                        LiveIdentity.invalidate(user.uid)
                        consecutiveDenied += 1
                        if (consecutiveDenied >= 3) return@launch
                    }
                }
                delay(VIEWER_HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopViewerHeartbeat() {
        val userId = auth.currentUser?.uid
        val sessionId = currentSessionId
        viewerHeartbeatJob?.cancel()
        viewerHeartbeatJob = null
        hostHeartbeatJob?.cancel()
        hostHeartbeatJob = null
        if (!sessionId.isNullOrBlank() && !userId.isNullOrBlank()) {
            // Fire-and-forget: this also runs from onCleared, after viewModelScope is cancelled.
            db.collection(FirestoreCollection.LIVE_SESSIONS)
                .document(sessionId)
                .collection(FirestoreSubcollection.VIEWERS)
                .document(userId)
                .delete()
                .addOnFailureListener { Log.w(TAG, "Failed to clear viewer presence", it) }
        }
    }

    /**
     * Host moderation. Shared rules make live comments immutable (no client delete), so the message
     * is hidden on the host's screen; Hide user (blocked_users) is what removes someone for everyone.
     */
    fun deleteLiveComment(comment: LiveRoomComment) {
        if (!_uiState.value.isHost || comment.id.isBlank()) return
        hostHiddenCommentIds += comment.id
        _uiState.update { state ->
            state.copy(
                liveComments = state.liveComments.filterNot { it.id == comment.id },
                statusMessage = "Comment hidden on your screen. Use Hide user to remove them from chat.",
            )
        }
    }

    @Suppress("unused")
    private fun deleteLiveCommentForEveryone(comment: LiveRoomComment) {
        val sessionId = _uiState.value.session?.sessionId ?: return
        if (!_uiState.value.isHost || comment.id.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection(FirestoreCollection.LIVE_SESSIONS)
                    .document(sessionId)
                    .collection(FirestoreSubcollection.COMMENTS)
                    .document(comment.id)
                    .delete()
                    .await()
                _uiState.update { it.copy(statusMessage = "Comment removed.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete live comment", e)
                _uiState.update {
                    it.copy(shareError = userMessageForLivePermissionFailure(e, "remove this comment"))
                }
            }
        }
    }

    private fun preferredMainRemoteUid(uids: List<Int>, hostAgoraUid: Int?): Int? {
        if (hostAgoraUid != null && uids.contains(hostAgoraUid)) return hostAgoraUid
        return uids.firstOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure cleanup is always called when the ViewModel is destroyed
        leaveStream()
    }

    companion object {
        private const val VIEWER_HEARTBEAT_INTERVAL_MS = 30_000L
        private const val VIEWER_PRESENCE_STALE_MS = 90_000L
        private const val MAX_RECONNECT_ATTEMPTS = 2
        private const val RECONNECT_BACKOFF_MS = 1_500L
        private const val HOST_HEARTBEAT_INTERVAL_MS = 60_000L
        private const val END_STREAM_TIMEOUT_MS = 15_000L
        private const val LIVE_CHAT_WINDOW = 200L
        private const val COMMENT_COUNT_REFRESH_MS = 15_000L
        private const val COMMENT_SLOW_MODE_MS = 2_000L
        private const val CLIENT_CLOCK_SKEW_MS = 120_000L
        /** Survives leaving and reopening the room in this app process. */
        private val leftStageSessionsInProcess = mutableSetOf<String>()
    }
}
