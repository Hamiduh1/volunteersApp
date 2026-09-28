package com.example.volunteersApp.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.BuildConfig
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.notifications.ActiveCallService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoEncoderConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.abs
import com.example.volunteersApp.notifications.IncomingCallNotifications
import com.example.volunteersApp.notifications.OutgoingCallRingbackHelper
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

sealed class CallUiEvent {
    data class ShowError(val message: String) : CallUiEvent()
    data object EndCall : CallUiEvent()
}

data class CallUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isSpeakerEnabled: Boolean = true,
    val isUsingFrontCamera: Boolean = true,
    val remoteUid: Int? = null,
    val statusLabel: String = "Connecting",
    val isAwaitingAnswer: Boolean = false
)

class CallViewModel(
    private val chatId: String,
    private val otherUserId: String?,
    private val callType: CallType,
    private val isCaller: Boolean,
    private val callId: String?
) : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val currentUser = auth.currentUser
    private val isResumingExistingSession = !callId.isNullOrBlank()
    private var agoraEngine: RtcEngine? = null
    private var remoteJoined = false
    private val remoteUids = linkedSetOf<Int>()
    private var activeCallId: String = callId ?: UUID.randomUUID().toString()
    private var callStartAtMs: Long = 0L
    private var resolvedOtherUserId: String? = otherUserId
    private var isGroupChat: Boolean = false
    private var groupName: String = ""
    private var sessionParticipants: List<String> = emptyList()
    private var callSessionListener: ListenerRegistration? = null
    private var callActivityHeartbeatJob: Job? = null
    private var appContext: Context? = null
    private var callHasFinished = false

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableStateFlow<CallUiEvent?>(null)
    val events = _events.asStateFlow()

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            if (callStartAtMs == 0L) {
                callStartAtMs = System.currentTimeMillis()
            }
            val label = when {
                !isCaller -> "Connecting"
                else -> "Calling…"
            }
            _uiState.update { it.copy(isLoading = false, statusLabel = label) }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            remoteJoined = true
            remoteUids += uid
            startCallActivityHeartbeat()
            OutgoingCallRingbackHelper.stop()
            _uiState.update {
                it.copy(
                    remoteUid = it.remoteUid ?: uid,
                    statusLabel = "Connected",
                )
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            remoteUids -= uid
            val nextRemoteUid = remoteUids.firstOrNull()
            if (isGroupChat) {
                _uiState.update {
                    it.copy(
                        remoteUid = nextRemoteUid,
                        statusLabel = if (nextRemoteUid == null) "Waiting for others" else "Connected",
                    )
                }
                return
            }

            if (reason == AGORA_USER_OFFLINE_REASON_QUIT) {
                _uiState.update { it.copy(remoteUid = null, statusLabel = "Call ended") }
                endCall()
            } else {
                // A temporary network loss reports an offline peer too. Keep the session alive so
                // Agora can reconnect instead of ending an otherwise recoverable one-to-one call.
                _uiState.update { it.copy(remoteUid = null, statusLabel = "Reconnecting") }
            }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            viewModelScope.launch {
                renewAgoraToken()
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            when (state) {
                Constants.CONNECTION_STATE_RECONNECTING -> {
                    _uiState.update { it.copy(statusLabel = "Reconnecting") }
                }
                Constants.CONNECTION_STATE_CONNECTED -> {
                    _uiState.update { current ->
                        if (current.remoteUid != null) current.copy(statusLabel = "Connected") else current
                    }
                }
            }
        }
    }

    fun startCall(context: Context) {
        if (currentUser == null) {
            _events.value = CallUiEvent.ShowError("You must be logged in.")
            return
        }

        if (!isCaller) {
            attachCallSessionMetadataListener()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    statusLabel = "Incoming call",
                    isAwaitingAnswer = true
                )
            }
            return
        }

        launchCallSetup(context = context, acceptIncomingFirst = false)
    }

    fun acceptIncomingCall(context: Context) {
        if (currentUser == null) {
            _events.value = CallUiEvent.ShowError("You must be logged in.")
            return
        }
        if (!isCaller) {
            IncomingCallNotifications.dismiss(context.applicationContext, activeCallId, chatId)
        }
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                statusLabel = "Connecting",
                isAwaitingAnswer = false
            )
        }
        launchCallSetup(context = context, acceptIncomingFirst = true)
    }

    fun declineIncomingCall(context: Context) {
        if (isCaller) {
            endCall()
            return
        }
        if (callHasFinished) return
        callHasFinished = true
        IncomingCallNotifications.dismiss(context.applicationContext, activeCallId, chatId)
        viewModelScope.launch {
            try {
                updateCallSessionStatus("declined")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decline incoming call", e)
            } finally {
                leaveChannel()
                _events.value = CallUiEvent.EndCall
            }
        }
    }

    private fun launchCallSetup(context: Context, acceptIncomingFirst: Boolean) {
        appContext = context.applicationContext
        viewModelScope.launch {
            var step = "prepareCallContext"
            var createdSessionThisAttempt = false
            try {
                prepareCallContext()
                if (acceptIncomingFirst) {
                    step = "acceptCallSession"
                    acceptIncomingCallSessionIfNeeded()
                }
                step = "initializeAgora"
                initializeAgora(context.applicationContext)
                if (isCaller) {
                    step = "createCallSession"
                    createCallSession()
                    createdSessionThisAttempt = true
                    OutgoingCallRingbackHelper.start(context.applicationContext)
                }
                step = "listenSessionMeta"
                attachCallSessionMetadataListener()
                step = "joinChannel"
                joinChannel()
            } catch (e: FirebaseFirestoreException) {
                closeFailedOutgoingSession(createdSessionThisAttempt)
                leaveChannel()
                Log.e(TAG, "Failed to start call at step=$step (Firestore ${e.code})", e)
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    logFirestorePermissionDeniedDiagnostics(TAG, "call_sessions create ($step)")
                }
                val msg = when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "We couldn't start this call because Firestore denied access when creating the call session. " +
                            "Sign out and back in, register your App Check debug token (debug builds), and confirm " +
                            "you're still a participant in this chat."
                    else -> e.message ?: "Failed to start call"
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusLabel = "Call not started",
                        error = msg,
                    )
                }
                _events.value = CallUiEvent.ShowError(msg)
            } catch (e: FirebaseFunctionsException) {
                closeFailedOutgoingSession(createdSessionThisAttempt)
                leaveChannel()
                Log.e(TAG, "Failed to start call at step=$step (Cloud Function ${e.code})", e)
                val msg = when (e.code) {
                    FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                        "Permission denied when requesting the Agora call token ($step). " +
                            "Confirm App Check is enabled for this build and that getAgoraRtcToken is deployed " +
                            "in us-central1 for this Firebase project."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        "Not authenticated for the call token. Sign in again and retry."
                    else -> e.message ?: "Failed to start call"
                }
                _uiState.update { it.copy(isLoading = false, error = msg) }
                _events.value = CallUiEvent.ShowError(msg)
            } catch (e: Exception) {
                closeFailedOutgoingSession(createdSessionThisAttempt)
                leaveChannel()
                Log.e(TAG, "Failed to start call at step=$step", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to start call") }
                _events.value = CallUiEvent.ShowError(e.message ?: "Failed to start call")
            }
        }
    }

    /**
     * When a callee opens [CallActivity] from notification/foreground fallback,
     * ensure Firestore session state is marked accepted before channel join.
     */
    private suspend fun acceptIncomingCallSessionIfNeeded() {
        val selfId = currentUser?.uid ?: return
        if (isCaller || activeCallId.isBlank()) return

        val sessionRef = db.collection(FirestoreCollection.CALL_SESSIONS).document(activeCallId)
        val snapshot = sessionRef.get().await()
        if (!snapshot.exists()) return

        val status = snapshot.getString("status").orEmpty()
        if (status.equals("ended", ignoreCase = true) || status.equals("declined", ignoreCase = true)) {
            return
        }

        val participantIds = (snapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val isGroupSession = snapshot.getBoolean("isGroup") == true || participantIds.size > 2
        val callerId = snapshot.getString("callerId").orEmpty()
        val sessionChatId = snapshot.getString("chatId").orEmpty().ifBlank { chatId }

        val updates = mutableMapOf<String, Any>(
            "acceptedParticipantIds" to FieldValue.arrayUnion(selfId),
            "declinedParticipantIds" to FieldValue.arrayRemove(selfId),
            "endedParticipantIds" to FieldValue.arrayRemove(selfId),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!isGroupSession) {
            updates["status"] = "accepted"
        }

        sessionRef.update(updates).await()

        if (isGroupSession && sessionChatId.isNotBlank()) {
            db.collection(FirestoreCollection.CHATS).document(sessionChatId)
                .update(
                    mapOf(
                        "lastCallType" to callType.name.lowercase(),
                        "lastCallStatus" to "accepted",
                        "lastCallTimestamp" to FieldValue.serverTimestamp(),
                        "lastCallInitiatorId" to callerId,
                        "lastCallReceiverId" to selfId
                    )
                )
                .await()
        }
    }

    private suspend fun prepareCallContext() {
        ensureCallFirestoreWritePreflight()
        val selfId = currentUser?.uid ?: return
        val chatDoc = db.collection(FirestoreCollection.CHATS).document(chatId).get().await()
        val participants = (chatDoc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val exitedParticipants = (chatDoc.get("exitedParticipantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val activeParticipants = participants.filterNot { it in exitedParticipants }
        if (activeParticipants.size < 2) {
            throw IllegalStateException("This conversation no longer has enough active members for a call.")
        }
        isGroupChat = chatDoc.getString("chatType") == "group" || activeParticipants.size > 2
        groupName = chatDoc.getString("groupName").orEmpty()
        val otherActiveParticipants = activeParticipants
            .filter { it != selfId }
            .distinct()
        if (otherActiveParticipants.isEmpty()) {
            throw IllegalStateException("This conversation does not have another participant to call.")
        }

        if (isGroupChat) {
            if (!isResumingExistingSession) {
                val groupAvailability = ConversationAvailabilityRepository.fetchCallAvailability(
                    chatId = chatId,
                    receiverId = null,
                )
                if (groupAvailability.usedCallable && !groupAvailability.available) {
                    throw IllegalStateException(
                        groupAvailability.resolvedCallMessage("No reachable members are available for this group call right now.")
                    )
                }
            }

            sessionParticipants = if (isCaller) {
                val myBlockedUserIds = db.fetchBlockedUserIds(selfId)
                otherActiveParticipants.filter { candidateId ->
                    runCatching {
                        isCallableRecipient(
                            selfId = selfId,
                            candidateId = candidateId,
                            myBlockedUserIds = myBlockedUserIds
                        )
                    }.getOrElse { error ->
                        Log.w(TAG, "Skipping non-callable group participant uid=$candidateId", error)
                        false
                    }
                }
            } else {
                otherActiveParticipants
            }

            if (sessionParticipants.isEmpty()) {
                throw IllegalStateException("No reachable members are available for this group call right now.")
            }
            if (groupName.isBlank()) {
                groupName = "Group call"
            }
            resolvedOtherUserId = sessionParticipants.firstOrNull()
            return
        }

        val myBlockedUserIds = db.fetchBlockedUserIds(selfId)
        val target = resolvedOtherUserId
            ?.takeIf { it in otherActiveParticipants }
            ?: otherActiveParticipants.first()

        if (!isResumingExistingSession) {
            val callAvailability = ConversationAvailabilityRepository.fetchCallAvailability(
                chatId = chatId,
                receiverId = target,
            )
            if (callAvailability.usedCallable) {
                if (!callAvailability.available) {
                    throw IllegalStateException(
                        callAvailability.resolvedCallMessage("Calls are unavailable with this person right now.")
                    )
                }
            } else {
                ensureDirectRecipientReady(
                    selfId = selfId,
                    candidateId = target,
                    myBlockedUserIds = myBlockedUserIds
                )
            }
        }
        resolvedOtherUserId = target
        sessionParticipants = listOf(target)
    }

    private suspend fun ensureDirectRecipientReady(
        selfId: String,
        candidateId: String,
        myBlockedUserIds: Set<String>
    ) {
        val available = isCallableRecipient(
            selfId = selfId,
            candidateId = candidateId,
            myBlockedUserIds = myBlockedUserIds
        )
        if (!available) {
            throw IllegalStateException("Calls are unavailable with this person right now.")
        }
    }

    private suspend fun isCallableRecipient(
        selfId: String,
        candidateId: String,
        myBlockedUserIds: Set<String>
    ): Boolean {
        val cleanCandidateId = candidateId.trim()
        if (cleanCandidateId.isBlank() || cleanCandidateId == selfId || cleanCandidateId in myBlockedUserIds) {
            return false
        }

        val candidateBlockedIds = db.fetchBlockedUserIds(cleanCandidateId)
        if (selfId in candidateBlockedIds) {
            return false
        }

        if (!isCaller) {
            return true
        }

        val candidateDoc = db.collection(FirestoreCollection.USERS).document(cleanCandidateId).get().await()
        val fcm = candidateDoc.getString("fcmToken").orEmpty().trim()
        if (fcm.isBlank()) {
            // Do not block 1:1 calls: iOS (or web) may not mirror Android's onNewToken → users.fcmToken yet.
            // Ringing still works via Firestore call_sessions while the app is foregrounded; push uses token when present.
            Log.w(
                TAG,
                "Callee uid=$cleanCandidateId has no users.fcmToken — call session will still be created; " +
                    "ensure iOS writes the same field for background incoming_call notifications."
            )
        }
        return true
    }

    private fun initializeAgora(context: Context) {
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
        try {
            agoraEngine = RtcEngine.create(config)
        } catch (error: LinkageError) {
            // A missing/stripped native Agora dependency must show a recoverable call error,
            // not terminate the release app process.
            throw IllegalStateException(
                "Call components could not start on this installation. Update the app and try again.",
                error,
            )
        }
        if (agoraEngine == null) {
            throw IllegalStateException("Call components could not start. Please try again.")
        }
        agoraEngine?.enableAudio()
        agoraEngine?.enableLocalAudio(true)
        agoraEngine?.muteLocalAudioStream(false)
        // Keep calls audible by default while still allowing the person to select earpiece/headset.
        agoraEngine?.setDefaultAudioRoutetoSpeakerphone(true)
        agoraEngine?.setEnableSpeakerphone(true)
        if (callType == CallType.VIDEO) {
            agoraEngine?.enableVideo()
            agoraEngine?.enableLocalVideo(true)
            agoraEngine?.setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_1280x720,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE,
                ),
            )
            agoraEngine?.enableDualStreamMode(true)
            agoraEngine?.startPreview()
            _uiState.update {
                it.copy(
                    isVideoMuted = false,
                    isSpeakerEnabled = true,
                    isUsingFrontCamera = true,
                )
            }
        } else {
            agoraEngine?.disableVideo()
            _uiState.update { it.copy(isVideoMuted = true, isSpeakerEnabled = true) }
        }
    }

    /**
     * Caller UI should reflect "answered" from Firestore even if RTC `onUserJoined` is delayed.
     */
    private fun attachCallSessionMetadataListener() {
        callSessionListener?.remove()
        if (activeCallId.isBlank()) return
        callSessionListener = db.collection(FirestoreCollection.CALL_SESSIONS).document(activeCallId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status").orEmpty()
                if (status.equals("declined", ignoreCase = true)) {
                    finishCallFromRemote("Call declined")
                    return@addSnapshotListener
                }
                if (status.equals("ended", ignoreCase = true)) {
                    finishCallFromRemote("Call ended")
                    return@addSnapshotListener
                }
                val acceptedIds = (snapshot.get("acceptedParticipantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val otherAccepted = acceptedIds.any { it.isNotBlank() && it != currentUser?.uid }
                if (isCaller && !remoteJoined && (status == "accepted" || otherAccepted)) {
                    OutgoingCallRingbackHelper.stop()
                    _uiState.update { prev ->
                        if (prev.statusLabel == "Connected") prev
                        else prev.copy(statusLabel = "Answered, connecting…")
                    }
                }
            }
    }

    private suspend fun joinChannel() {
        // Must stay ≤ 64 chars for getAgoraRtcToken Cloud Function. Using the Firestore call session id
        // (caller's UUID or callee's session.id) keeps a unique channel per ring and matches Accept flow.
        val channelName = "call_$activeCallId"
        val firebaseUid = currentUser?.uid ?: throw IllegalStateException("You must be logged in.")
        val localUid = stableAgoraUid(firebaseUid)
        val token = getProductionToken(channelName, "publisher", localUid)

        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            autoSubscribeAudio = true
            autoSubscribeVideo = callType == CallType.VIDEO
            publishMicrophoneTrack = true
            publishCameraTrack = callType == CallType.VIDEO
        }

        val engine = agoraEngine
            ?: throw IllegalStateException("Call components are not ready. Please try again.")
        val result = engine.joinChannel(token, channelName, localUid, options)
        if (result != 0) {
            val message = if (token.isBlank()) {
                "Failed to join call. Token is empty. Verify Agora token function configuration. Code: $result"
            } else {
                "Failed to join call. Code: $result"
            }
            throw IllegalStateException(message)
        }
        ActiveCallService.start(
            context = appContext ?: return,
            chatId = chatId,
            callId = activeCallId,
            otherUserId = resolvedOtherUserId,
            callType = callType,
            isCaller = isCaller,
        )
    }

    private suspend fun getProductionToken(channelName: String, role: String, uid: Int): String {
        // Deployed getAgoraRtcToken requires sessionId (Firestore call_sessions doc id). Channel is call_<sessionId>.
        val data = hashMapOf<String, Any>(
            "sessionId" to activeCallId,
            "channelName" to channelName,
            "role" to role,
            "uid" to uid
        )
        val resultMap = FunctionsClient.callMap(CallableFunction.GET_AGORA_RTC_TOKEN, data)
        val token = resultMap?.get("token") as? String
        val tokenRequired = resultMap?.get("tokenRequired") as? Boolean ?: true

        if (tokenRequired && token.isNullOrBlank()) {
            throw IllegalStateException("Failed to parse Agora token from response.")
        }

        return token.orEmpty()
    }

    private suspend fun renewAgoraToken() {
        val firebaseUid = currentUser?.uid ?: return
        val channelName = "call_$activeCallId"
        runCatching {
            getProductionToken(channelName, "publisher", stableAgoraUid(firebaseUid))
        }.onSuccess { token ->
            if (token.isNotBlank()) {
                agoraEngine?.renewToken(token)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to renew Agora call token", error)
        }
    }

    private fun stableAgoraUid(firebaseUid: String): Int {
        val hash = firebaseUid.hashCode()
        if (hash == Int.MIN_VALUE) return 1
        return abs(hash).coerceAtLeast(1)
    }

    private suspend fun createCallSession() {
        val callerId = currentUser?.uid ?: return
        val receiverId = resolvedOtherUserId.orEmpty()
        if (!isGroupChat && receiverId.isBlank()) {
            throw IllegalStateException("A valid recipient is required before placing a call.")
        }
        val participantIdsForSession = if (isGroupChat) {
            (listOf(callerId) + sessionParticipants).distinct()
        } else {
            listOf(callerId, receiverId).filter { it.isNotBlank() }.distinct()
        }
        val response = FunctionsClient.callMap(
            CallableFunction.CREATE_CALL_SESSION,
            hashMapOf(
                "chatId" to chatId,
                "sessionId" to activeCallId,
                "receiverId" to receiverId,
                "participantIds" to participantIdsForSession,
                "callType" to callType.name.lowercase(),
            )
        ) ?: throw IllegalStateException("The call service returned no session.")
        val returnedSessionId = response["sessionId"] as? String
        if (returnedSessionId != activeCallId) {
            throw IllegalStateException("The call service returned an unexpected session.")
        }
    }

    private suspend fun ensureCallFirestoreWritePreflight() {
        val uid = currentUser?.uid
            ?: throw IllegalStateException("You must be signed in to place a call.")
        try {
            auth.currentUser?.getIdToken(true)?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Call write ID token refresh failed (uid=$uid)", e)
            throw IllegalStateException("Sign in again before placing a call.")
        }
        if (!ensureFirestoreListenerPreflight(TAG)) {
            throw IllegalStateException(
                "We couldn't verify this device for calls. Check your connection, register your App Check " +
                    "debug token for this build in Firebase Console, then restart the app."
            )
        }
    }

    private suspend fun createCallLog() {
        val callerId = currentUser?.uid ?: return
        val receiverId = resolvedOtherUserId.orEmpty()
        val logRef = db.collection(FirestoreCollection.CHATS).document(chatId)
            .collection(FirestoreSubcollection.CALL_LOGS).document(activeCallId)
        callStartAtMs = System.currentTimeMillis()

        val logData = hashMapOf(
            "chatId" to chatId,
            "callerId" to callerId,
            "receiverId" to receiverId,
            "participantIds" to (listOf(callerId) + sessionParticipants).distinct(),
            "isGroupCall" to isGroupChat,
            "groupName" to groupName,
            "callType" to callType.name.lowercase(),
            "status" to "ringing",
            "startedAt" to FieldValue.serverTimestamp()
        )

        db.runBatch { batch ->
            batch.set(logRef, logData)
            batch.update(
                db.collection(FirestoreCollection.CHATS).document(chatId),
                mapOf(
                    "lastCallType" to callType.name.lowercase(),
                    "lastCallStatus" to "ringing",
                    "lastCallTimestamp" to FieldValue.serverTimestamp(),
                    "lastCallInitiatorId" to callerId,
                    "lastCallReceiverId" to receiverId
                )
            )
        }.await()
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

    fun toggleSpeaker() {
        val enabled = !_uiState.value.isSpeakerEnabled
        agoraEngine?.setEnableSpeakerphone(enabled)
        _uiState.update { it.copy(isSpeakerEnabled = enabled) }
    }

    fun switchCamera() {
        agoraEngine?.switchCamera()
        _uiState.update { it.copy(isUsingFrontCamera = !it.isUsingFrontCamera) }
    }

    fun endCall() {
        if (callHasFinished) return
        callHasFinished = true
        viewModelScope.launch {
            try {
                updateCallSessionStatus("ended")
                if (!(isGroupChat && !isCaller)) {
                    updateCallLog()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update call log", e)
            } finally {
                leaveChannel()
                _events.value = CallUiEvent.EndCall
            }
        }
    }

    private suspend fun updateCallLog() {
        val selfId = currentUser?.uid ?: return
        val callerId = if (isCaller) selfId else (resolvedOtherUserId ?: "")
        val receiverId = if (isCaller) (resolvedOtherUserId ?: "") else selfId
        val status = if (remoteJoined) "completed" else "missed"
        val duration = if (callStartAtMs > 0) (System.currentTimeMillis() - callStartAtMs) / 1000 else 0L
        val logRef = db.collection(FirestoreCollection.CHATS).document(chatId)
            .collection(FirestoreSubcollection.CALL_LOGS).document(activeCallId)

        db.runBatch { batch ->
            batch.update(
                logRef,
                mapOf(
                    "status" to status,
                    "endedAt" to FieldValue.serverTimestamp(),
                    "durationSeconds" to duration,
                    "participantIds" to (listOf(selfId) + sessionParticipants).distinct(),
                    "isGroupCall" to isGroupChat,
                    "groupName" to groupName
                )
            )
            batch.update(
                db.collection(FirestoreCollection.CHATS).document(chatId),
                mapOf(
                    "lastCallType" to callType.name.lowercase(),
                    "lastCallStatus" to status,
                    "lastCallTimestamp" to FieldValue.serverTimestamp(),
                    "lastCallInitiatorId" to callerId,
                    "lastCallReceiverId" to receiverId
                )
            )
        }.await()
    }

    private suspend fun updateCallSessionStatus(status: String) {
        val selfId = currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "endedParticipantIds" to FieldValue.arrayUnion(selfId)
        )

        if (!isGroupChat || isCaller) {
            updates["status"] = status
        }

        db.collection(FirestoreCollection.CALL_SESSIONS).document(activeCallId).update(updates).await()
        // onCallSessionUpdated clears recipient mirrors after the terminal status is committed.
    }

    private fun startCallActivityHeartbeat() {
        if (callActivityHeartbeatJob?.isActive == true || activeCallId.isBlank()) return
        callActivityHeartbeatJob = viewModelScope.launch {
            while (isActive && !callHasFinished) {
                delay(CALL_ACTIVITY_HEARTBEAT_MS)
                runCatching {
                    db.collection(FirestoreCollection.CALL_SESSIONS).document(activeCallId)
                        .update("updatedAt", FieldValue.serverTimestamp())
                        .await()
                }.onFailure { error ->
                    Log.w(TAG, "Call activity heartbeat failed", error)
                }
            }
        }
    }

    /**
     * Only tear down a call session that this launch created and then failed to join.
     * A resumed call may have a valid caller-owned session, so it must remain untouched.
     */
    private suspend fun closeFailedOutgoingSession(createdSessionThisAttempt: Boolean) {
        if (!createdSessionThisAttempt || activeCallId.isBlank()) return
        runCatching {
            db.collection(FirestoreCollection.CALL_SESSIONS).document(activeCallId).update(
                mapOf(
                    "status" to "ended",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "endedBy" to (currentUser?.uid ?: ""),
                    "endReason" to "caller_setup_failed",
                )
            ).await()
        }.onFailure { error ->
            Log.w(TAG, "Unable to close failed call session $activeCallId", error)
        }
    }

    private fun leaveChannel() {
        callActivityHeartbeatJob?.cancel()
        callActivityHeartbeatJob = null
        OutgoingCallRingbackHelper.stop()
        appContext?.let { context ->
            runCatching { ActiveCallService.stop(context, activeCallId) }
                .onFailure { Log.w(TAG, "Unable to stop active-call service", it) }
        }
        callSessionListener?.remove()
        callSessionListener = null
        val engine = agoraEngine
        agoraEngine = null
        if (engine != null) {
            if (callType == CallType.VIDEO) {
                runCatching { engine.stopPreview() }
                    .onFailure { Log.w(TAG, "Unable to stop local video preview", it) }
            }
            runCatching { engine.leaveChannel() }
                .onFailure { Log.w(TAG, "Unable to leave Agora call channel", it) }
            runCatching { RtcEngine.destroy() }
                .onFailure { Log.w(TAG, "Unable to destroy Agora call engine", it) }
        }
        remoteUids.clear()
    }

    private fun finishCallFromRemote(statusLabel: String) {
        if (callHasFinished) return
        callHasFinished = true
        OutgoingCallRingbackHelper.stop()
        _uiState.update {
            it.copy(
                isLoading = false,
                remoteUid = null,
                statusLabel = statusLabel,
                isAwaitingAnswer = false,
            )
        }
        leaveChannel()
        _events.value = CallUiEvent.EndCall
    }

    override fun onCleared() {
        super.onCleared()
        leaveChannel()
    }

    fun getEngine(): RtcEngine? = agoraEngine

    companion object {
        private const val TAG = "CallViewModel"
        private const val CALL_ACTIVITY_HEARTBEAT_MS = 60_000L
        // Agora's documented USER_OFFLINE_QUIT reason. Other reasons can be a transient drop.
        private const val AGORA_USER_OFFLINE_REASON_QUIT = 0

        fun provideFactory(
            chatId: String,
            otherUserId: String?,
            callType: CallType,
            isCaller: Boolean,
            callId: String?
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CallViewModel::class.java)) {
                        return CallViewModel(chatId, otherUserId, callType, isCaller, callId) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
