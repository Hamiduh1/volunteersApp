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
    private var currentSessionId: String? = null
    private var currentShareAccessToken: String? = null
    private var shareTokenValidated: Boolean = false
    private var suppressReconnect = false
    private var reconnectAttempts = 0
    private val acceptedEventIds = mutableSetOf<String>()

    // --- DELETED: Removed redundant properties (`rtcEngine`, `_remoteUsers`, `appId`, `token`) ---
    // --- DELETED: Removed unused `initStream` method ---

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d(TAG, "Successfully joined Agora channel: $channel with uid: $uid")
            // This is the local user's UID, you can store it if needed.
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "Remote user joined with uid: $uid")
            // If supporting multiple remote users, you'd add to a list.
            // For a 1-on-1 stream, this is sufficient.
            _uiState.update { it.copy(remoteUid = uid) }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Remote user left with uid: $uid, reason: $reason")
            // Clear the remote user from the UI state
            _uiState.update { it.copy(remoteUid = null) }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            val session = _uiState.value.session ?: return
            viewModelScope.launch {
                renewRtcToken(session)
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            if (suppressReconnect) return
            if (state == Constants.CONNECTION_STATE_RECONNECTING && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
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
                    val message = if (error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ) {
                        "This stream is unavailable or access has changed. Refresh and try again."
                    } else {
                        "We couldn't load this stream. Check your connection and try again."
                    }
                    _uiState.update { it.copy(isLoading = false, error = message) }
                    return@addSnapshotListener
                }
                val session = snapshot?.toLiveSession()
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
                    if (!canCurrentUserWatchSession(session, currentUid)) {
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
                    _uiState.update {
                        it.copy(
                            session = session,
                            isHost = isHost,
                            isOnStage = isOnStage,
                            isLoading = false,
                            sessionEnded = false
                        )
                    }

                    if (agoraEngine?.connectionState != Constants.CONNECTION_STATE_CONNECTED || (isOnStage && !wasOnStage)) {
                        joinAgoraChannel(session)
                    }

                    if (isHost) {
                        listenToIncomingRequests(session.sessionId, session.hostId)
                    }

                    listenToLiveEngagement(session.sessionId)
                    listenToViewers(session.sessionId)
                    listenToBlockedUsers(session.sessionId)
                    startViewerHeartbeat(session)
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
            LiveViewAccessMode.FOLLOWERS_ONLY -> {
                db.collection(FirestoreCollection.USERS)
                    .document(currentUid)
                    .collection(FirestoreSubcollection.FOLLOWING)
                    .document(session.hostId)
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
            LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS -> {
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
                "This stream must be opened from the linked event flow."
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
                    )
                } ?: emptyList()

                _uiState.update {
                    it.copy(
                        liveCommentsCount = comments.size.toLong(),
                        liveComments = comments
                    )
                }
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
                    LiveRoomViewer(
                        userId = doc.id,
                        displayName = doc.getString("displayName").orEmpty().ifBlank { "Viewer" },
                        role = doc.getString("role").orEmpty().ifBlank { "viewer" },
                    )
                } ?: emptyList()
                _uiState.update { state ->
                    state.copy(
                        session = state.session?.copy(viewerCount = activeViewers.size.toLong()),
                        roomViewers = activeViewers,
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
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(requestsLoading = true) }

                // Update request status
                db.collection(FirestoreCollection.JOIN_REQUESTS).document(request.requestId)
                    .update(mapOf(
                        "status" to "accepted",
                        "respondedAt" to FieldValue.serverTimestamp()
                    ))
                    .await()

                // Add volunteer to accepted list in the stream
                _uiState.value.session?.let { session ->
                    val updatedList = session.acceptedVolunteerIds.toMutableList()
                    if (!updatedList.contains(request.volunteerId)) {
                        updatedList.add(request.volunteerId)
                        db.collection(FirestoreCollection.LIVE_SESSIONS).document(session.sessionId)
                            .update("acceptedVolunteerIds", updatedList)
                            .await()
                    }
                }

                Log.d(TAG, "Accepted join request from ${request.volunteerId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting join request", e)
                _uiState.update { it.copy(error = userMessageForLivePermissionFailure(e, "accept this stage request")) }
            } finally {
                _uiState.update { it.copy(requestsLoading = false) }
            }
        }
    }

    // NEW: Reject a volunteer's request to join
    fun rejectJoinRequest(request: JoinLiveStreamRequest) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(requestsLoading = true) }

                db.collection(FirestoreCollection.JOIN_REQUESTS).document(request.requestId)
                    .update(mapOf(
                        "status" to LiveJoinRequestStatus.REJECTED.raw,
                        "respondedAt" to FieldValue.serverTimestamp()
                    ))
                    .await()

                Log.d(TAG, "Rejected join request from ${request.volunteerId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting join request", e)
                _uiState.update { it.copy(error = userMessageForLivePermissionFailure(e, "reject this stage request")) }
            } finally {
                _uiState.update { it.copy(requestsLoading = false) }
            }
        }
    }

    private fun shouldPublishToStage(session: LiveSession, uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false
        if (uid == session.hostId) return true
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

    private suspend fun isAcceptedEventVolunteer(session: LiveSession, currentUid: String): Boolean {
        val eventId = session.linkedEventId?.trim().orEmpty().ifBlank {
            session.sourceId?.trim().orEmpty()
        }
        if (eventId.isBlank()) return false
        val snapshots = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
            .whereEqualTo("eventId", eventId)
            .whereEqualTo("volunteerId", currentUid)
            .get()
            .await()
        return snapshots.documents.any { doc ->
            val status = doc.getString("status").orEmpty()
            status.equals("accepted", ignoreCase = true) ||
                status.equals("approved", ignoreCase = true)
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
        viewModelScope.launch {
            try {
                val firebaseUid = auth.currentUser?.uid
                    ?: throw IllegalStateException("You must be logged in to join a stream.")
                val localUid = stableAgoraUid(firebaseUid)
                val rtcRole = LiveRtcJoinRole.publisherIf(publish)
                val token = LiveRepository.fetchAgoraRtcToken(
                    sessionId = session.sessionId,
                    channelName = channelName,
                    role = rtcRole,
                    uid = localUid,
                    shareAccessToken = currentShareAccessToken,
                )
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

    private suspend fun renewRtcToken(session: LiveSession) {
        val firebaseUid = auth.currentUser?.uid ?: return
        val localUid = stableAgoraUid(firebaseUid)
        val publish = shouldPublishToStage(session, firebaseUid)
        val rtcRole = LiveRtcJoinRole.publisherIf(publish)
        runCatching {
            LiveRepository.fetchAgoraRtcToken(
                sessionId = session.sessionId,
                channelName = session.resolvedChannelName,
                role = rtcRole,
                uid = localUid,
                shareAccessToken = currentShareAccessToken,
            )
        }.onSuccess { token ->
            if (token.isNotBlank()) {
                agoraEngine?.renewToken(token)
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
                _uiState.update { it.copy(error = "Could not update chat setting") }
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
                blockedRef.set(
                    mapOf(
                        "userId" to userId,
                        "blockedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                _uiState.update { it.copy(statusMessage = "Viewer blocked.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to block viewer", e)
                _uiState.update { it.copy(error = "Could not block viewer") }
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
                _uiState.update { it.copy(error = "Could not unblock viewer") }
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update replay visibility", e)
                _uiState.update { it.copy(error = "Could not update replay visibility") }
            }
        }
    }

    fun requestJoinStage() {
        val session = _uiState.value.session ?: return
        if (session.stageAccessMode != LiveStageAccessMode.REQUEST_TO_JOIN) return
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val request = JoinLiveStreamRequest(
                    volunteerId = currentUser.uid,
                    volunteerName = currentUser.displayName ?: "Anonymous",
                    volunteerProfilePicUrl = currentUser.photoUrl?.toString(),
                    streamId = session.sessionId,
                    hostId = session.hostId,
                    status = LiveJoinRequestStatus.PENDING.raw
                )
                val requestId = "${session.sessionId}_${currentUser.uid}"
                db.collection(FirestoreCollection.JOIN_REQUESTS).document(requestId).set(request).await()
            } catch (e: Exception) {
                Log.e(TAG, "Stage request failed", e)
                _uiState.update { it.copy(error = "Could not request to join stage") }
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
    fun endStream(onComplete: (() -> Unit)? = null) {
        val state = _uiState.value
        if (!state.isHost || state.session == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            var ended = false
            try {
                db.collection(FirestoreCollection.LIVE_SESSIONS).document(state.session.sessionId)
                    .update(
                        mapOf(
                            "status" to "ENDED",
                            "endTime" to FieldValue.serverTimestamp(),
                            "endedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    .await()
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

        requestsListener?.remove()  // NEW: Remove requests listener
        requestsListener = null

        likesListener?.remove()
        likesListener = null

        commentsListener?.remove()
        commentsListener = null

        viewersListener?.remove()
        viewersListener = null

        blockedUsersListener?.remove()
        blockedUsersListener = null

        stopViewerHeartbeat()

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
                    it.copy(error = userMessageForLivePermissionFailure(e, "update your reaction"))
                }
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
            _uiState.update { it.copy(error = "Comment is too long (max 500).") }
            return
        }

        val streamId = _uiState.value.session?.sessionId ?: return
        val user = auth.currentUser ?: return
        val authorName = user.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"

        viewModelScope.launch {
            try {
                val payload = mutableMapOf<String, Any>(
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "text" to message,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                replyTo?.takeIf { it.id.isNotBlank() }?.let { parent ->
                    payload["replyToCommentId"] = parent.id
                    payload["replyToAuthorName"] = parent.authorName.ifBlank { "Anonymous" }
                    payload["replyToText"] = parent.text.take(120)
                }
                db.collection(FirestoreCollection.LIVE_SESSIONS)
                    .document(streamId)
                    .collection(FirestoreSubcollection.COMMENTS)
                    .add(payload)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post live comment", e)
                _uiState.update {
                    it.copy(error = userMessageForLivePermissionFailure(e, "send this comment"))
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

    private fun startViewerHeartbeat(session: LiveSession) {
        if (_uiState.value.isHost) return
        val user = auth.currentUser ?: return
        viewerHeartbeatJob?.cancel()
        val sessionId = session.sessionId
        viewerHeartbeatJob = viewModelScope.launch {
            while (true) {
                runCatching {
                    db.collection(FirestoreCollection.LIVE_SESSIONS)
                        .document(sessionId)
                        .collection(FirestoreSubcollection.VIEWERS)
                        .document(user.uid)
                        .set(
                            mapOf(
                                "userId" to user.uid,
                                "displayName" to (user.displayName ?: "Viewer"),
                                "role" to "viewer",
                                "lastSeenAt" to FieldValue.serverTimestamp(),
                                "isVisible" to true,
                            )
                        )
                        .await()
                }.onFailure {
                    Log.w(TAG, "Failed to write viewer heartbeat", it)
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
        if (!sessionId.isNullOrBlank() && !userId.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching {
                    db.collection(FirestoreCollection.LIVE_SESSIONS)
                        .document(sessionId)
                        .collection(FirestoreSubcollection.VIEWERS)
                        .document(userId)
                        .delete()
                        .await()
                }
            }
        }
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
    }
}
