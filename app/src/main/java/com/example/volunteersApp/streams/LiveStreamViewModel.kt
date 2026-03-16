package com.example.volunteersApp.streams

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.android.gms.tasks.Task
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- UI State and ChatMessage data classes remain the same ---
data class LiveStreamUiState(
    val session: LiveSession? = null,
    val isHost: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val remoteUid: Int? = null,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false
)

// You should define ChatMessage if it's not defined elsewhere
//data class ChatMessage(val userId: String, val authorName: String, val message: String)

class LiveStreamViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val functions = Firebase.functions
    private val TAG = "LiveStreamVM"

    private val _uiState = MutableStateFlow(LiveStreamUiState())
    val uiState = _uiState.asStateFlow()

    val messages = mutableStateListOf<ChatMessage>()

    // --- REFACTORED: Use only ONE engine instance ---
    private var agoraEngine: RtcEngine? = null
    private var sessionListener: ListenerRegistration? = null

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
    }

    fun getEngine(): RtcEngine? = agoraEngine

    fun joinStream(sessionId: String, context: Context) {
        if (agoraEngine != null) return // Already initialized

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
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
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = BuildConfig.AGORA_APP_ID
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
        sessionListener = db.collection("live_sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load session.") }
                    return@addSnapshotListener
                }
                val session = snapshot?.toObject(LiveSession::class.java)
                if (session == null || session.status == "ended") {
                    _uiState.update { it.copy(isLoading = false, error = "This stream has ended.", session = null) }
                    leaveStream()
                    return@addSnapshotListener
                }

                val isHost = auth.currentUser?.uid == session.hostId
                val wasHost = _uiState.value.isHost // Check previous state
                _uiState.update { it.copy(session = session, isHost = isHost, isLoading = false) }

                // Join channel only once, or if role changes from audience to host.
                if (agoraEngine?.connectionState != Constants.CONNECTION_STATE_CONNECTED || (isHost && !wasHost)) {
                    joinAgoraChannel(session.agoraChannelName, isHost)
                }
            }
    }

    private fun joinAgoraChannel(channelName: String, isHost: Boolean) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        getProductionToken(channelName).addOnSuccessListener { token ->
            Log.d(TAG, "Successfully fetched production token.")

            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = if (isHost) {
                    Constants.CLIENT_ROLE_BROADCASTER
                } else {
                    Constants.CLIENT_ROLE_AUDIENCE
                }
                // Automatically publish tracks if joining as a host
                publishCameraTrack = isHost
                publishMicrophoneTrack = isHost
            }

            // Start preview for the host before joining
            if (isHost) {
                agoraEngine?.startPreview()
            }

            val result = agoraEngine?.joinChannel(token, channelName, 0, options)

            if (result != 0) {
                Log.e(TAG, "Could not join channel, error code: $result")
                _uiState.update { it.copy(isLoading = false, error = "Failed to join stream. Code: $result") }
            } else {
                Log.d(TAG, "Join channel command sent successfully.")
                _uiState.update { it.copy(isLoading = false) }
            }

        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to get production token", exception)
            _uiState.update { it.copy(isLoading = false, error = "Authentication failed: ${exception.message}") }
        }
    }

    private fun getProductionToken(channelName: String): Task<String> {
        val data = hashMapOf("channelName" to channelName)
        return functions.getHttpsCallable("getAgoraRtcToken").call(data).continueWith { task ->
            val result = task.result?.data as? Map<String, Any>
            result?.get("token") as? String ?: throw Exception("Failed to parse token from response.")
        }
    }

    fun endStream() {
        val state = _uiState.value
        if (!state.isHost || state.session == null) return

        viewModelScope.launch {
            try {
                db.collection("live_sessions").document(state.session.agoraChannelName)
                    .update(mapOf("status" to "ended", "endTime" to FieldValue.serverTimestamp()))
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update stream status to ended", e)
            } finally {
                leaveStream()
            }
        }
    }

    // --- NEWLY UPDATED `leaveStream` FUNCTION ---
    fun leaveStream() {
        Log.d(TAG, "Leaving stream and cleaning up resources...")

        sessionListener?.remove()
        sessionListener = null

        agoraEngine?.stopPreview()
        agoraEngine?.leaveChannel()

        RtcEngine.destroy()
        agoraEngine = null

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

    fun sendMessage(message: String) {
        val user = auth.currentUser
        messages.add(
            ChatMessage(
                userId = user?.uid ?: "",
                authorName = user?.displayName ?: "Anonymous",
                message = message
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure cleanup is always called when the ViewModel is destroyed
        leaveStream()
    }
}
