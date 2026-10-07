package com.example.volunteersApp.streams

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.chat.ensureFirestoreListenerPreflight
import com.example.volunteersApp.chat.logFirestorePermissionDeniedDiagnostics
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class StartStreamUiState(
    val title: String = "",
    val description: String = "",
    val viewAccessMode: LiveViewAccessMode = LiveViewAccessMode.PUBLIC,
    val stageAccessMode: LiveStageAccessMode = LiveStageAccessMode.REQUEST_TO_JOIN,
    val replayVisibility: LiveReplayVisibility = LiveReplayVisibility.FOLLOWERS_ONLY,
    val notifyFollowersOnStart: Boolean = true,
    val postToMindLoomOnStart: Boolean = true,
    val chatEnabled: Boolean = true,
    val launchSourceType: String = "standalone",
    val linkedEventId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** A broadcast this host never ended that is still heartbeating; offer Resume / End instead of a dead end. */
    val activeSessionId: String? = null,
    val activeSessionTitle: String? = null,
)

sealed class StartStreamEvent {
    data class Success(
        val sessionId: String,
        val shareLink: String,
        val mindLoomMessage: String? = null,
    ) : StartStreamEvent()
}

class StartStreamViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "StartStreamVM"

    private val _uiState = MutableStateFlow(StartStreamUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StartStreamEvent>()
    val events = _events.asSharedFlow()

    fun onTitleChange(newTitle: String) = _uiState.update { it.copy(title = newTitle.take(120)) }
    fun onDescriptionChange(newDescription: String) = _uiState.update { it.copy(description = newDescription.take(2_000)) }
    // Shared rules: an invite-only room admits viewers through stage requests, so its stage mode
    // must be "Request to join" or "Approved volunteers".
    fun onViewAccessModeChange(mode: LiveViewAccessMode) = _uiState.update {
        val stage = if (mode == LiveViewAccessMode.INVITE_ONLY && it.stageAccessMode !in INVITE_ONLY_STAGE_MODES) {
            LiveStageAccessMode.REQUEST_TO_JOIN
        } else {
            it.stageAccessMode
        }
        it.copy(viewAccessMode = mode, stageAccessMode = stage)
    }
    fun onStageAccessModeChange(mode: LiveStageAccessMode) = _uiState.update {
        val view = if (it.viewAccessMode == LiveViewAccessMode.INVITE_ONLY && mode !in INVITE_ONLY_STAGE_MODES) {
            LiveViewAccessMode.FOLLOWERS_ONLY
        } else {
            it.viewAccessMode
        }
        it.copy(stageAccessMode = mode, viewAccessMode = view)
    }
    fun onReplayVisibilityChange(mode: LiveReplayVisibility) = _uiState.update { it.copy(replayVisibility = mode) }
    fun onNotifyFollowersChange(enabled: Boolean) = _uiState.update { it.copy(notifyFollowersOnStart = enabled) }
    fun onPostToMindLoomChange(enabled: Boolean) = _uiState.update { it.copy(postToMindLoomOnStart = enabled) }
    fun onChatEnabledChange(enabled: Boolean) = _uiState.update { it.copy(chatEnabled = enabled) }

    fun setLaunchSourceType(sourceType: String) =
        _uiState.update { it.copy(launchSourceType = sourceType.ifBlank { "standalone" }) }

    fun setLaunchContext(sourceType: String, linkedEventId: String?) {
        val eventId = linkedEventId?.trim()?.takeIf { it.isNotBlank() }
        _uiState.update {
            it.copy(
                launchSourceType = if (eventId != null) SOURCE_ORGANIZER_EVENT else sourceType.ifBlank { "standalone" },
                linkedEventId = eventId,
            )
        }
    }

    fun resetError() = _uiState.update { it.copy(error = null) }

    fun startStream() {
        val state = _uiState.value
        val currentUser = auth.currentUser ?: run {
            _uiState.update { it.copy(error = "User not logged in") }
            return
        }
        // A double tap must not create two rooms.
        if (state.isLoading) return
        val eventOnlyAudience = state.viewAccessMode == LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS ||
            state.stageAccessMode == LiveStageAccessMode.OPEN_TO_ACCEPTED_VOLUNTEERS
        if (eventOnlyAudience && state.linkedEventId.isNullOrBlank()) {
            _uiState.update {
                it.copy(error = "Event-volunteer audiences need a linked event. Start this stream from Hosted events → Go live for this event.")
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, activeSessionId = null, activeSessionTitle = null) }

        viewModelScope.launch {
            try {
                // Auth + App Check must succeed before the create write. Firestore often surfaces
                // App Check / auth failures as PERMISSION_DENIED on live_sessions.
                ensureLiveCreatePreflight(currentUser.uid)

                val activeSession = runCatching {
                    db.collection(FirestoreCollection.LIVE_SESSIONS)
                        .whereEqualTo("hostId", currentUser.uid)
                        .whereIn("status", listOf("LIVE", "live", "active"))
                        .limit(1)
                        .get()
                        .await()
                }.onFailure { error ->
                    Log.w(TAG, "Active-session precheck skipped", error)
                }.getOrNull()

                val existing = activeSession?.documents?.firstOrNull()
                if (existing != null) {
                    val nowMs = System.currentTimeMillis()
                    val heartbeatMs = existing.getTimestamp("hostHeartbeatAt")?.toDate()?.time
                    val updatedMs = existing.getTimestamp("updatedAt")?.toDate()?.time
                    // iOS hosts don't heartbeat, so without one only a long-idle room counts as abandoned.
                    val stale = if (heartbeatMs != null) {
                        nowMs - heartbeatMs > LIVE_HOST_STALE_AFTER_MS
                    } else {
                        updatedMs != null && nowMs - updatedMs > ABANDONED_WITHOUT_HEARTBEAT_MS
                    }
                    if (stale) {
                        // The previous broadcast died with the app; close it so viewers aren't left in a ghost room.
                        runCatching { markSessionEnded(existing.id) }
                            .onFailure { Log.w(TAG, "Could not close stale live session ${existing.id}", it) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                activeSessionId = existing.id,
                                activeSessionTitle = existing.getString("title")?.ifBlank { null } ?: "Live session",
                            )
                        }
                        return@launch
                    }
                }

                val sessionId = UUID.randomUUID().toString()
                val channelName = "live-${currentUser.uid}-${System.currentTimeMillis()}".take(64)
                // Shared rules require hostName to match the host's own profile/auth identity
                // (liveIdentityMatches); "Anonymous Host" is only accepted for identity-less accounts.
                val hostName = LiveIdentity.displayName(db, currentUser)
                    .takeIf { it.isNotBlank() && it != "Viewer" }
                    ?: currentUser.displayName.takeIf { !it.isNullOrBlank() }
                    ?: "Anonymous Host"
                val safeTitle = state.title.trim().ifBlank { "Live Session" }.take(120)
                val normalizedUsername = currentUser.displayName
                    ?.trim()
                    ?.removePrefix("@")
                    ?.takeIf { it.isNotBlank() }
                val sharePath = "${LiveShareConstants.WEB_LIVE_PATH}?sessionId=$sessionId&hostId=${currentUser.uid}"
                val shareUrl = "${LiveShareConstants.WEB_SHARE_HOST}$sharePath"

                // Shared (iOS-owned) rules allow only these keys on create (liveSessionAllowedKeys);
                // description, counters, stage lists and avatar URLs are rejected, and chat must start on.
                val sourceType = when {
                    !state.linkedEventId.isNullOrBlank() -> SOURCE_ORGANIZER_EVENT
                    state.launchSourceType in setOf("standalone", "mindloom") -> state.launchSourceType
                    else -> "standalone"
                }
                val liveSessionData = hashMapOf<String, Any>(
                    "agoraChannelName" to channelName,
                    "channelName" to channelName,
                    "title" to safeTitle,
                    "hostId" to currentUser.uid,
                    "hostUid" to currentUser.uid,
                    "hostName" to hostName.take(120),
                    "status" to "LIVE",
                    "sourceType" to sourceType,
                    "sharePath" to sharePath,
                    "shareUrl" to shareUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "startTime" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "viewAccessMode" to state.viewAccessMode.raw,
                    "stageAccessMode" to state.stageAccessMode.raw,
                    "replayVisibility" to state.replayVisibility.raw,
                    "notifyFollowers" to state.notifyFollowersOnStart,
                    "chatEnabled" to true,
                )
                if (!normalizedUsername.isNullOrBlank()) {
                    liveSessionData["hostUsername"] = normalizedUsername.take(120)
                }
                state.linkedEventId?.let { eventId ->
                    liveSessionData["sourceId"] = eventId
                }

                db.collection(FirestoreCollection.LIVE_SESSIONS).document(sessionId).set(liveSessionData).await()
                if (!state.chatEnabled) {
                    runCatching {
                        db.collection(FirestoreCollection.LIVE_SESSIONS).document(sessionId)
                            .update(mapOf("chatEnabled" to false, "updatedAt" to FieldValue.serverTimestamp()))
                            .await()
                    }.onFailure { Log.w(TAG, "Could not turn chat off after start", it) }
                }

                val shareLink = try {
                    LiveRepository.createLiveShareAccessLink(sessionId)
                } catch (e: Exception) {
                    Log.w(TAG, "Signed share link unavailable; using deep link fallback", e)
                    LiveShareConstants.appDeepLink(
                        sessionId = sessionId,
                        hostId = currentUser.uid,
                    )
                }

                val mindLoomMessage = if (state.postToMindLoomOnStart) {
                    val sessionSnapshot = LiveSession(
                        sessionId = sessionId,
                        title = safeTitle,
                        description = state.description.trim(),
                        hostId = currentUser.uid,
                        hostName = hostName,
                        status = "LIVE",
                        viewAccessMode = state.viewAccessMode,
                        replayVisibility = state.replayVisibility,
                    )
                    MindLoomLivePostWriter.postLiveSession(sessionSnapshot, shareLink)
                        .fold(
                            onSuccess = { "Shared to MindLoom." },
                            onFailure = { error ->
                                Log.w(TAG, "Auto MindLoom post skipped", error)
                                "Live started, but MindLoom could not be updated. You can retry from Host tools."
                            },
                        )
                } else {
                    null
                }

                _uiState.update { it.copy(isLoading = false) }
                _events.emit(StartStreamEvent.Success(sessionId, shareLink, mindLoomMessage))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create live session", e)
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    logFirestorePermissionDeniedDiagnostics(TAG, "live_sessions create")
                }
                _uiState.update {
                    it.copy(isLoading = false, error = userMessageForLiveCreateFailure(e))
                }
            }
        }
    }

    /** Ends the host's other running broadcast so they can start a new one. */
    fun endActiveSession() {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                markSessionEnded(sessionId)
                _uiState.update { it.copy(isLoading = false, activeSessionId = null, activeSessionTitle = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to end previous live session", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "We couldn't end your other stream. Check your connection and try again.")
                }
            }
        }
    }

    fun dismissActiveSession() = _uiState.update { it.copy(activeSessionId = null, activeSessionTitle = null) }

    private suspend fun markSessionEnded(sessionId: String) {
        db.collection(FirestoreCollection.LIVE_SESSIONS).document(sessionId)
            .update(
                mapOf(
                    "status" to "ENDED",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            .await()
    }

    private suspend fun ensureLiveCreatePreflight(uid: String) {
        try {
            auth.currentUser?.getIdToken(true)?.await()
                ?: throw IllegalStateException("Sign in again before going live.")
        } catch (e: Exception) {
            Log.w(TAG, "Live create ID token refresh failed (uid=$uid)", e)
            throw IllegalStateException("Sign in again before going live.")
        }

        if (!ensureFirestoreListenerPreflight(TAG)) {
            val projectId = runCatching { Firebase.app.options.projectId }.getOrNull().orEmpty()
            Log.w(TAG, "Live create preflight failed (uid=$uid, projectId=$projectId)")
            throw IllegalStateException(
                "We couldn't verify this device for l" +
                        "" +
                        "" +
                        "ive streaming. Register your App Check debug token " +
                    "in Firebase Console (Logcat tag: FirebaseAppCheck), restart the app, then try again."
            )
        }
    }

    private fun userMessageForLiveCreateFailure(error: Throwable): String {
        if (error is IllegalStateException && !error.message.isNullOrBlank()) {
            return error.message.orEmpty()
        }
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            return "We couldn't start the broadcast — Firestore denied the write. Sign out and back in, " +
                "register your App Check debug token (debug builds), deploy firestore.rules to this Firebase " +
                "project, then try again."
        }
        return "We couldn't start the broadcast. Check your connection and try again."
    }

    private companion object {
        const val ABANDONED_WITHOUT_HEARTBEAT_MS = 30 * 60_000L
        const val SOURCE_ORGANIZER_EVENT = "organizer_event"
        val INVITE_ONLY_STAGE_MODES = setOf(LiveStageAccessMode.REQUEST_TO_JOIN, LiveStageAccessMode.APPROVED_VOLUNTEERS)
    }
}
