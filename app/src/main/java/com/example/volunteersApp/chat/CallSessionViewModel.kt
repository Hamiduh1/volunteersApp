package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Incoming call signaling:
 * 1. Primary: `users/{uid}/incoming_call_sessions` (per-user mirror)
 * 2. Legacy fallback: `call_sessions` queries are used only when the server-owned mirror is
 *    unavailable. Keeping both listeners active causes avoidable permission-denied noise on
 *    rules that intentionally expose only the recipient mirror.
 */
class CallSessionViewModel : ViewModel() {
    private enum class IncomingMirrorAccess {
        AVAILABLE,
        PERMISSION_DENIED,
        UNAVAILABLE,
    }

    private val db = Firebase.firestore
    private var incomingMirrorListener: ListenerRegistration? = null
    private var legacyDirectListener: ListenerRegistration? = null
    private var legacyGroupListener: ListenerRegistration? = null
    private var preflightRetryJob: Job? = null
    private var preflightRetryCount = 0
    private val mirrorSessions = mutableMapOf<String, CallSession>()
    private val legacyDirectSessions = mutableMapOf<String, CallSession>()
    private val legacyGroupSessions = mutableMapOf<String, CallSession>()
    private var boundUid: String? = null

    /** Session ids hidden from the incoming UI until this time (ms since epoch). */
    private val suppressedIncomingUntil = mutableMapOf<String, Long>()

    private val _incomingSession = MutableStateFlow<CallSession?>(null)
    val incomingSession = _incomingSession.asStateFlow()

    private val _listenerWarning = MutableStateFlow<String?>(null)
    val listenerWarning = _listenerWarning.asStateFlow()

    fun clearListenerWarning() {
        _listenerWarning.value = null
    }

    /** Lets the inbox retry call alerts without requiring a sign-out or app restart. */
    fun refreshIncomingCallAlerts() {
        val uid = boundUid ?: Firebase.auth.currentUser?.uid ?: return
        preflightRetryCount = 0
        bindToUid(uid, forceRebind = true)
    }

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        bindToUid(auth.currentUser?.uid)
    }

    init {
        Firebase.auth.addAuthStateListener(authListener)
        bindToUid(Firebase.auth.currentUser?.uid)
    }

    private fun bindToUid(uid: String?, forceRebind: Boolean = false) {
        if (uid.isNullOrBlank()) {
            tearDownListeners()
            boundUid = null
            preflightRetryCount = 0
            preflightRetryJob?.cancel()
            suppressedIncomingUntil.clear()
            _incomingSession.value = null
            _listenerWarning.value = null
            return
        }
        if (!forceRebind &&
            uid == boundUid &&
            hasActiveIncomingListeners()
        ) {
            return
        }
        val previousUid = boundUid
        tearDownListeners()
        boundUid = uid
        if (previousUid != uid) {
            suppressedIncomingUntil.clear()
        }
        _listenerWarning.value = null
        startIncomingCallSignaling(uid)
    }

    private fun hasActiveIncomingListeners(): Boolean {
        return incomingMirrorListener != null ||
            legacyDirectListener != null ||
            legacyGroupListener != null
    }

    private fun tearDownListeners() {
        incomingMirrorListener?.remove()
        incomingMirrorListener = null
        legacyDirectListener?.remove()
        legacyDirectListener = null
        legacyGroupListener?.remove()
        legacyGroupListener = null
        mirrorSessions.clear()
        legacyDirectSessions.clear()
        legacyGroupSessions.clear()
        _listenerWarning.value = null
    }

    private fun startIncomingCallSignaling(userId: String) {
        preflightRetryJob?.cancel()
        viewModelScope.launch {
            val projectId = runCatching { Firebase.app.options.projectId }.getOrNull().orEmpty()
            Log.i(TAG, "Starting incoming call signaling (uid=$userId, projectId=$projectId)")
            if (!ensureFirestoreListenerPreflight(TAG)) {
                Log.w(TAG, "Incoming call preflight not ready (uid=$userId); scheduling one retry")
                schedulePreflightRetry(userId)
                return@launch
            }
            preflightRetryCount = 0
            when (probeIncomingCallMirrorAccess(userId)) {
                IncomingMirrorAccess.AVAILABLE -> {
                    attachIncomingMirrorListener(userId)
                }
                IncomingMirrorAccess.PERMISSION_DENIED -> {
                    Log.w(TAG, "incoming_call_sessions denied for uid=$userId; relying on call_sessions fallback")
                    attachLegacyCallSessionListeners(userId)
                }
                IncomingMirrorAccess.UNAVAILABLE -> {
                    Log.w(TAG, "incoming_call_sessions unavailable for uid=$userId; using legacy call_sessions listeners")
                    attachLegacyCallSessionListeners(userId)
                }
            }
        }
    }

    private fun schedulePreflightRetry(userId: String) {
        if (preflightRetryCount >= MAX_PREFLIGHT_RETRIES) {
            _listenerWarning.value =
                "Incoming call alerts are temporarily unavailable. Refresh to try again. " +
                    "Your conversations and call history are still available."
            return
        }
        preflightRetryCount += 1
        preflightRetryJob = viewModelScope.launch {
            delay(PREFLIGHT_RETRY_DELAY_MS)
            if (Firebase.auth.currentUser?.uid != userId) return@launch
            startIncomingCallSignaling(userId)
        }
    }

    /** One-shot probe avoids attaching a realtime listener that immediately errors and retries. */
    private suspend fun probeIncomingCallMirrorAccess(userId: String): IncomingMirrorAccess {
        return try {
            db.collection(FirestoreCollection.USERS)
                .document(userId)
                .collection(FirestoreSubcollection.INCOMING_CALL_SESSIONS)
                .limit(1)
                .get()
                .await()
            IncomingMirrorAccess.AVAILABLE
        } catch (e: FirebaseFirestoreException) {
            if (isFirestorePermissionDenied(e)) {
                logFirestorePermissionDeniedDiagnostics(
                    TAG,
                    "users/{uid}/incoming_call_sessions (probe)",
                    quiet = true,
                )
                IncomingMirrorAccess.PERMISSION_DENIED
            } else {
                Log.w(TAG, "incoming_call_sessions probe failed", e)
                IncomingMirrorAccess.UNAVAILABLE
            }
        }
    }

    private fun attachIncomingMirrorListener(userId: String) {
        incomingMirrorListener = db.collection(FirestoreCollection.USERS)
            .document(userId)
            .collection(FirestoreSubcollection.INCOMING_CALL_SESSIONS)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    if (error is FirebaseFirestoreException && isFirestorePermissionDenied(error)) {
                        Log.w(TAG, "incoming_call_sessions listener permission denied; disabling Firestore incoming-call listener")
                    } else {
                        Log.e(TAG, "incoming_call_sessions listener failed", error)
                    }
                    incomingMirrorListener?.remove()
                    incomingMirrorListener = null
                    mirrorSessions.clear()
                    if (legacyDirectListener == null && legacyGroupListener == null) {
                        attachLegacyCallSessionListeners(userId)
                    }
                    if (error is FirebaseFirestoreException && isFirestorePermissionDenied(error)) {
                        viewModelScope.launch {
                            logFirestorePermissionDeniedDiagnostics(
                                TAG,
                                "users/{uid}/incoming_call_sessions (listener)",
                                quiet = true,
                            )
                        }
                        if (legacyDirectListener == null && legacyGroupListener == null) {
                            _listenerWarning.value = permissionDeniedIncomingCallWarning()
                        }
                    } else {
                        if (legacyDirectListener == null && legacyGroupListener == null) {
                            _listenerWarning.value = userMessageForIncomingCallListenFailure(error)
                        }
                    }
                    return@addSnapshotListener
                }
                if (legacyDirectListener != null || legacyGroupListener != null) {
                    _listenerWarning.value = null
                }
                replaceSessions(
                    target = mirrorSessions,
                    sessions = snapshots?.documents.orEmpty().map { it.toCallSession() },
                )
                emitIncomingSession(userId)
            }
    }

    private fun attachLegacyCallSessionListeners(userId: String) {
        if (legacyDirectListener != null || legacyGroupListener != null) return
        _listenerWarning.value = null

        legacyDirectListener = db.collection(FirestoreCollection.CALL_SESSIONS)
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    if (error is FirebaseFirestoreException && isFirestorePermissionDenied(error)) {
                        Log.w(TAG, "Legacy direct call_sessions listener permission denied; disabling legacy direct listener")
                    } else {
                        Log.e(TAG, "Legacy direct call_sessions listener failed", error)
                    }
                    handleLegacyListenerError(error, "receiverId+status")
                    return@addSnapshotListener
                }
                _listenerWarning.value = null
                replaceSessions(
                    target = legacyDirectSessions,
                    sessions = snapshots?.documents.orEmpty().map { it.toCallSession() },
                )
                emitIncomingSession(userId)
            }

        legacyGroupListener = db.collection(FirestoreCollection.CALL_SESSIONS)
            .whereArrayContains("participantIds", userId)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    if (error is FirebaseFirestoreException && isFirestorePermissionDenied(error)) {
                        Log.w(TAG, "Legacy group call_sessions listener permission denied; disabling legacy group listener")
                    } else {
                        Log.e(TAG, "Legacy group call_sessions listener failed", error)
                    }
                    handleLegacyListenerError(error, "participantIds+status")
                    return@addSnapshotListener
                }
                _listenerWarning.value = null
                replaceSessions(
                    target = legacyGroupSessions,
                    sessions = snapshots?.documents.orEmpty().map { it.toCallSession() },
                )
                emitIncomingSession(userId)
            }
    }

    private fun handleLegacyListenerError(
        error: Exception,
        queryLabel: String,
    ) {
        when (queryLabel) {
            "receiverId+status" -> {
                legacyDirectListener?.remove()
                legacyDirectListener = null
                legacyDirectSessions.clear()
            }
            "participantIds+status" -> {
                legacyGroupListener?.remove()
                legacyGroupListener = null
                legacyGroupSessions.clear()
            }
        }
        // Either legacy query can surface a direct call. Keep call alerts available when its
        // companion listener is still healthy, such as while a Firestore index propagates.
        if (legacyDirectListener != null || legacyGroupListener != null) {
            Log.w(TAG, "Legacy call listener unavailable ($queryLabel); retaining alternate listener", error)
            return
        }
        if (error is FirebaseFirestoreException && isFirestorePermissionDenied(error)) {
            viewModelScope.launch {
                logFirestorePermissionDeniedDiagnostics(TAG, "call_sessions $queryLabel")
            }
            _listenerWarning.value = userMessageForIncomingCallListenFailure(error)
        } else {
            _listenerWarning.value = userMessageForIncomingCallListenFailure(error)
        }
    }

    private fun replaceSessions(
        target: MutableMap<String, CallSession>,
        sessions: List<CallSession>,
    ) {
        target.clear()
        sessions.forEach { target[it.id] = it }
    }

    private fun removeSession(sessionId: String) {
        mirrorSessions.remove(sessionId)
        legacyDirectSessions.remove(sessionId)
        legacyGroupSessions.remove(sessionId)
    }

    private fun combinedSessions(): Sequence<CallSession> {
        val merged = LinkedHashMap<String, CallSession>()
        legacyGroupSessions.values.forEach { merged[it.id] = it }
        legacyDirectSessions.values.forEach { merged[it.id] = it }
        mirrorSessions.values.forEach { merged[it.id] = it }
        return merged.values.asSequence()
    }

    private fun pruneSuppressedIncoming() {
        val now = System.currentTimeMillis()
        suppressedIncomingUntil.entries.removeAll { it.value <= now }
    }

    private fun isIncomingSuppressed(sessionId: String): Boolean {
        pruneSuppressedIncoming()
        val until = suppressedIncomingUntil[sessionId] ?: return false
        return System.currentTimeMillis() < until
    }

    private fun suppressIncoming(sessionId: String, durationMs: Long) {
        suppressedIncomingUntil[sessionId] = System.currentTimeMillis() + durationMs
    }

    /** True if ringing is recent enough that we should show the incoming UI (avoids stale ghost rings). */
    private fun isRingingFresh(session: CallSession): Boolean {
        if (!session.status.equals("ringing", ignoreCase = true)) return false
        val refMs = session.createdAt?.time ?: session.updatedAt?.time
        // Server timestamps may be null on the first mirror snapshot; still ring immediately.
        if (refMs == null) return true
        return System.currentTimeMillis() - refMs <= RINGING_UI_MAX_AGE_MS
    }

    /** iOS parity: rebind signaling and resurface fresh ringing sessions after app resume. */
    fun refreshOnForeground() {
        val uid = boundUid ?: Firebase.auth.currentUser?.uid ?: return
        refreshIncomingCallAlerts()
        viewModelScope.launch {
            probeFreshRingingSessions(uid)
        }
    }

    private suspend fun probeFreshRingingSessions(userId: String) {
        if (!ensureFirestoreListenerPreflight(TAG)) return
        val sessions = runCatching {
            when (probeIncomingCallMirrorAccess(userId)) {
                IncomingMirrorAccess.AVAILABLE -> {
                    val sessions = db.collection(FirestoreCollection.USERS)
                        .document(userId)
                        .collection(FirestoreSubcollection.INCOMING_CALL_SESSIONS)
                        .get()
                        .await()
                        .documents
                        .map { it.toCallSession() }
                    replaceSessions(mirrorSessions, sessions)
                    sessions
                }
                IncomingMirrorAccess.PERMISSION_DENIED -> {
                    val direct = db.collection(FirestoreCollection.CALL_SESSIONS)
                        .whereEqualTo("receiverId", userId)
                        .whereEqualTo("status", "ringing")
                        .get()
                        .await()
                    val group = db.collection(FirestoreCollection.CALL_SESSIONS)
                        .whereArrayContains("participantIds", userId)
                        .whereEqualTo("status", "ringing")
                        .get()
                        .await()
                    val sessions = (direct.documents + group.documents)
                        .distinctBy { it.id }
                        .map { it.toCallSession() }
                    replaceSessions(legacyDirectSessions, sessions)
                    replaceSessions(legacyGroupSessions, emptyList())
                    sessions
                }
                IncomingMirrorAccess.UNAVAILABLE -> {
                    val direct = db.collection(FirestoreCollection.CALL_SESSIONS)
                        .whereEqualTo("receiverId", userId)
                        .whereEqualTo("status", "ringing")
                        .get()
                        .await()
                    val group = db.collection(FirestoreCollection.CALL_SESSIONS)
                        .whereArrayContains("participantIds", userId)
                        .whereEqualTo("status", "ringing")
                        .get()
                        .await()
                    val sessions = (direct.documents + group.documents)
                        .distinctBy { it.id }
                        .map { it.toCallSession() }
                    replaceSessions(legacyDirectSessions, sessions)
                    replaceSessions(legacyGroupSessions, emptyList())
                    sessions
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Foreground ringing probe failed", error)
            emptyList()
        }
        if (sessions.isEmpty()) {
            mirrorSessions.clear()
            legacyDirectSessions.clear()
            legacyGroupSessions.clear()
        }
        emitIncomingSession(userId)
    }

    fun dismissIncomingBanner(session: CallSession, notifyServer: Boolean = true) {
        if (notifyServer) {
            declineSession(session)
        } else {
            suppressIncoming(session.id, SUPPRESS_AFTER_DISMISS_MS)
            boundUid?.let { emitIncomingSession(it) }
        }
    }

    private fun emitIncomingSession(userId: String) {
        val candidate = combinedSessions()
            .filter { it.status.equals("ringing", ignoreCase = true) }
            .filter { isRingingFresh(it) }
            .filter { it.callerId != userId }
            .filter { userId !in it.endedParticipantIds }
            .filter { userId !in it.declinedParticipantIds }
            .filter { userId !in it.acceptedParticipantIds }
            .filterNot { isIncomingSuppressed(it.id) }
            .sortedByDescending { it.createdAt?.time ?: 0L }
            .firstOrNull()
        _incomingSession.value = candidate
    }

    fun acceptSession(session: CallSession, onComplete: (Boolean, String?) -> Unit) {
        val userId = Firebase.auth.currentUser?.uid ?: run {
            onComplete(false, "You must be signed in to answer this call.")
            return
        }
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "acceptedParticipantIds" to FieldValue.arrayUnion(userId),
                    "declinedParticipantIds" to FieldValue.arrayRemove(userId),
                    "endedParticipantIds" to FieldValue.arrayRemove(userId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!session.isGroupCall()) {
                    updates["status"] = "accepted"
                }

                db.collection(FirestoreCollection.CALL_SESSIONS).document(session.id)
                    .update(updates)
                    .await()
                removeSession(session.id)
                _incomingSession.value = null
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept call session (chatId=${session.chatId}, sessionId=${session.id})", e)
                suppressIncoming(session.id, SUPPRESS_AFTER_DISMISS_MS)
                _incomingSession.value = null
                val msg = userMessageForFirestoreWriteFailure(e, "answer this call")
                onComplete(false, msg)
            }
        }
    }

    fun declineSession(session: CallSession) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        suppressIncoming(session.id, SUPPRESS_AFTER_DISMISS_MS)
        emitIncomingSession(userId)
        viewModelScope.launch {
            try {
                if (session.isGroupCall()) {
                    db.collection(FirestoreCollection.CALL_SESSIONS).document(session.id)
                        .update(
                            mapOf(
                                "declinedParticipantIds" to FieldValue.arrayUnion(userId),
                                "acceptedParticipantIds" to FieldValue.arrayRemove(userId),
                                "endedParticipantIds" to FieldValue.arrayRemove(userId),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                    removeSession(session.id)
                    emitIncomingSession(userId)
                    return@launch
                }

                val sessionRef = db.collection(FirestoreCollection.CALL_SESSIONS).document(session.id)
                val logRef = db.collection(FirestoreCollection.CHATS).document(session.chatId)
                    .collection(FirestoreSubcollection.CALL_LOGS).document(session.id)
                val chatRef = db.collection(FirestoreCollection.CHATS).document(session.chatId)

                db.runBatch { batch ->
                    batch.update(
                        sessionRef,
                        mapOf(
                            "status" to "declined",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        logRef,
                        mapOf(
                            "status" to "missed",
                            "endedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    batch.update(
                        chatRef,
                        mapOf(
                            "lastCallStatus" to "missed",
                            "lastCallTimestamp" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
                removeSession(session.id)
                emitIncomingSession(userId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decline call session chatId=${session.chatId}", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Firebase.auth.removeAuthStateListener(authListener)
        preflightRetryJob?.cancel()
        tearDownListeners()
    }

    companion object {
        private const val TAG = "CallSessionVM"
        // A ringing session is no longer a valid incoming call after the server's five-minute window.
        private const val RINGING_UI_MAX_AGE_MS = 5 * 60 * 1000L
        private const val SUPPRESS_AFTER_DISMISS_MS = 5 * 60 * 1000L
        private const val MAX_PREFLIGHT_RETRIES = 1
        private const val PREFLIGHT_RETRY_DELAY_MS = 1_500L
    }
}

private fun permissionDeniedIncomingCallWarning(): String =
    "Incoming call alerts are temporarily unavailable. Refresh to try again. " +
        "Your conversations and call history are still available."
