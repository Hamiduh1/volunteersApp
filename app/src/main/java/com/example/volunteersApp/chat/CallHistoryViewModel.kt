package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

sealed class CallHistoryState {
    data object Loading : CallHistoryState()
    data class Success(val items: List<CallHistoryItem>) : CallHistoryState()
    data class Error(val message: String) : CallHistoryState()
}

enum class CallDirection {
    DIALED,
    RECEIVED,
    MISSED
}

data class CallHistoryItem(
    val call: CallLog,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserPhotoUrl: String?,
    val direction: CallDirection,
    val isVideo: Boolean,
    val isGroup: Boolean
)

/**
 * Social Inbox Calls tab — merge every known call-log path used across Android/iOS:
 * user-scoped `call_history`, legacy user-scoped `call_logs`, and per-chat `call_logs`.
 */
class CallHistoryViewModel : ViewModel() {
    private val db = Firebase.firestore

    private var fetchJob: Job? = null
    private var preflightRetryJob: Job? = null
    private var preflightRetryCount = 0
    private val userProfileCache = mutableMapOf<String, Pair<String, String?>>()
    private val groupTitleCache = mutableMapOf<String, String>()
    private var blockedUserIds: Set<String> = emptySet()

    private val _state = MutableStateFlow<CallHistoryState>(CallHistoryState.Loading)
    val state = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var boundCallHistoryUid: String? = null

    private val authListener = FirebaseAuth.AuthStateListener {
        onAuthUidChanged()
    }

    init {
        Firebase.auth.addAuthStateListener(authListener)
        onAuthUidChanged()
    }

    private fun onAuthUidChanged() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            boundCallHistoryUid = null
            cancelFetchJobs()
            blockedUserIds = emptySet()
            _state.value = CallHistoryState.Error("You must be logged in.")
            return
        }
        if (uid == boundCallHistoryUid && fetchJob?.isActive == true) {
            return
        }
        boundCallHistoryUid = uid
        refresh()
    }

    fun refresh() {
        _statusMessage.value = null
        cancelFetchJobs()
        preflightRetryCount = 0
        blockedUserIds = emptySet()
        _state.value = CallHistoryState.Loading
        loadCallHistory()
    }

    fun clearError() {
        if (_state.value is CallHistoryState.Error) {
            refresh()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun hideCallHistoryItem(item: CallHistoryItem) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        val key = uniqueCallKey(item.call)
        viewModelScope.launch {
            try {
                db.collection(FirestoreCollection.USERS).document(userId)
                    .collection(FirestoreSubcollection.CALL_HISTORY_HIDDEN)
                    .document(callHistoryHiddenDocId(key))
                    .set(
                        mapOf(
                            "key" to key,
                            "chatId" to item.call.chatId,
                            "callId" to item.call.id,
                            "hiddenAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                val current = _state.value
                if (current is CallHistoryState.Success) {
                    _state.value = CallHistoryState.Success(
                        current.items.filter { uniqueCallKey(it.call) != key }
                    )
                }
                _statusMessage.value = "Call removed from history."
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide call history item key=$key", e)
                _statusMessage.value = "Couldn't remove this call from history."
            }
        }
    }

    private fun loadCallHistory() {
        val userId = Firebase.auth.currentUser?.uid ?: run {
            _state.value = CallHistoryState.Error("You must be logged in.")
            return
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = CallHistoryState.Loading
            if (!ensureFirestoreListenerPreflight(TAG)) {
                Log.w(TAG, "Call history preflight not ready (uid=$userId); scheduling one auth retry")
                schedulePreflightRetry(userId)
                return@launch
            }
            try {
                val items = loadMergedCallHistoryItems(userId)
                publishCallHistorySuccess(items)
            } catch (e: Exception) {
                Log.e(TAG, "Call history load failed (uid=$userId)", e)
                _state.value = CallHistoryState.Error(
                    userMessageForFirestoreListenFailure(e, "call history")
                )
            }
        }
    }

    private suspend fun loadMergedCallHistoryItems(userId: String): List<CallHistoryItem> {
        blockedUserIds = try {
            db.fetchBlockedUserIds(userId)
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Block list unavailable.", e)
            emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "Block list unavailable.", e)
            emptySet()
        }
        val hiddenKeys = fetchHiddenCallHistoryKeys(userId)
        val userScopedLogs = fetchUserCallHistoryFeed(userId)
        val fromChats = fetchCallLogsFromChats(userId)
        val merged = dedupeAndSort(userScopedLogs + fromChats)
            .filter { uniqueCallKey(it) !in hiddenKeys }
        return buildCallHistoryItems(merged, userId)
    }

    private suspend fun fetchHiddenCallHistoryKeys(uid: String): Set<String> {
        return try {
            db.collection(FirestoreCollection.USERS).document(uid)
                .collection(FirestoreSubcollection.CALL_HISTORY_HIDDEN)
                .limit(500)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.getString("key")?.trim()?.takeIf { it.isNotBlank() } ?: doc.id.trim().takeIf { it.isNotBlank() }
                }
                .toSet()
        } catch (e: FirebaseFirestoreException) {
            if (isFirestorePermissionDenied(e)) {
                Log.w(TAG, "users/$uid/call_history_hidden denied; showing unfiltered history", e)
                return emptySet()
            }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load hidden call history keys for uid=$uid", e)
            emptySet()
        }
    }

    private suspend fun fetchUserCallHistoryFeed(uid: String): List<CallLog> {
        val logs = buildList {
            addAll(fetchUserScopedCallLogs(uid, FirestoreSubcollection.CALL_HISTORY))
            addAll(fetchUserScopedCallLogs(uid, FirestoreSubcollection.CALL_LOGS))
        }
        return dedupeAndSort(logs)
    }

    private suspend fun fetchUserScopedCallLogs(
        uid: String,
        subcollectionPath: String,
    ): List<CallLog> {
        return try {
            db.collection(FirestoreCollection.USERS)
                .document(uid)
                .collection(subcollectionPath)
                .limit(SOCIAL_INBOX_CALL_HISTORY_LIMIT)
                .get()
                .await()
                .documents
                .map { it.toCallLog() }
        } catch (e: FirebaseFirestoreException) {
            if (isFirestorePermissionDenied(e)) {
                Log.w(TAG, "users/$uid/$subcollectionPath denied; skipping this feed path", e)
                return emptyList()
            }
            throw e
        }
    }

    private suspend fun fetchCallLogsFromChats(uid: String): List<CallLog> {
        val chatDocs = try {
            db.collection(FirestoreCollection.CHATS)
                .whereArrayContains("participants", uid)
                .limit(LEGACY_CHAT_SCAN_LIMIT)
                .get()
                .await()
                .documents
        } catch (e: FirebaseFirestoreException) {
            if (isFirestorePermissionDenied(e)) {
                logFirestorePermissionDeniedDiagnostics(
                    TAG,
                    "chats participants array-contains (legacy call history scan)",
                    quiet = true,
                )
                Log.w(TAG, "Legacy chat scan for call history denied; using feed only", e)
                return emptyList()
            }
            throw e
        }

        val logs = mutableListOf<CallLog>()
        for (doc in chatDocs) {
            try {
                val chatLogs = db.collection(FirestoreCollection.CHATS)
                    .document(doc.id)
                    .collection(FirestoreSubcollection.CALL_LOGS)
                    .limit(LEGACY_CALL_LOGS_PER_CHAT_LIMIT)
                    .get()
                    .await()
                    .documents
                    .map { it.toCallLog(fallbackChatId = doc.id) }
                logs.addAll(chatLogs)
            } catch (e: FirebaseFirestoreException) {
                if (isFirestorePermissionDenied(e)) {
                    Log.w(TAG, "call_logs denied for chat ${doc.id}; skipping", e)
                    continue
                }
                throw e
            }
        }
        return logs
    }

    private fun dedupeAndSort(logs: List<CallLog>): List<CallLog> {
        return logs
            .distinctBy { uniqueCallKey(it) }
            .sortedByDescending { it.startedAt?.time ?: it.endedAt?.time ?: 0L }
    }

    private suspend fun buildCallHistoryItems(logs: List<CallLog>, userId: String): List<CallHistoryItem> {
        return logs.mapNotNull { log ->
            val isGroupCall = log.isGroupCall || log.participantIds.size > 2
            val otherUserId = when {
                log.callerId == userId -> log.receiverId.ifBlank {
                    log.peerUid.ifBlank {
                        log.participantIds.firstOrNull { it != userId }.orEmpty()
                    }
                }
                log.callerId.isNotBlank() -> log.callerId
                else -> log.peerUid
            }
            if (!isGroupCall && otherUserId in blockedUserIds) {
                return@mapNotNull null
            }
            val (otherUserName, otherUserPhotoUrl) = if (isGroupCall) {
                val title = resolveGroupTitle(log.chatId, log.groupName)
                title to null
            } else {
                val resolvedProfile = if (otherUserId.isNotBlank()) {
                    resolveUserProfile(otherUserId)
                } else {
                    "Unknown" to null
                }
                val fallbackName = log.peerName.takeIf { it.isNotBlank() }
                if (resolvedProfile.first == "Unknown" && fallbackName != null) {
                    fallbackName to resolvedProfile.second
                } else {
                    resolvedProfile
                }
            }

            val direction = when {
                log.status.equals("missed", ignoreCase = true) &&
                    (log.receiverId == userId || log.direction.equals("missed", ignoreCase = true)) -> CallDirection.MISSED
                log.direction.equals("outgoing", ignoreCase = true) ||
                    log.direction.equals("dialed", ignoreCase = true) ||
                    log.callerId == userId -> CallDirection.DIALED
                else -> CallDirection.RECEIVED
            }

            CallHistoryItem(
                call = log,
                otherUserId = otherUserId,
                otherUserName = otherUserName,
                otherUserPhotoUrl = otherUserPhotoUrl,
                direction = direction,
                isVideo = log.callType.equals("video", ignoreCase = true) ||
                    log.type.equals("video", ignoreCase = true),
                isGroup = isGroupCall
            )
        }
    }

    private fun publishCallHistorySuccess(items: List<CallHistoryItem>) {
        _state.value = CallHistoryState.Success(items)
    }

    private fun schedulePreflightRetry(userId: String) {
        if (preflightRetryCount >= MAX_PREFLIGHT_RETRIES) {
            _state.value = CallHistoryState.Error(
                "Can't load call history yet. Sign in again, register your App Check debug token, then pull to refresh."
            )
            return
        }
        preflightRetryCount += 1
        preflightRetryJob?.cancel()
        preflightRetryJob = viewModelScope.launch {
            delay(PREFLIGHT_RETRY_DELAY_MS)
            if (Firebase.auth.currentUser?.uid != userId) return@launch
            if (!ensureFirestoreListenerPreflight(TAG)) {
                schedulePreflightRetry(userId)
                return@launch
            }
            preflightRetryCount = 0
            try {
                val items = loadMergedCallHistoryItems(userId)
                publishCallHistorySuccess(items)
            } catch (e: Exception) {
                Log.e(TAG, "Call history load failed after preflight retry (uid=$userId)", e)
                _state.value = CallHistoryState.Error(
                    userMessageForFirestoreListenFailure(e, "call history")
                )
            }
        }
    }

    private suspend fun resolveUserProfile(userId: String): Pair<String, String?> {
        if (userId.isBlank()) return "Unknown" to null

        userProfileCache[userId]?.let { return it }

        return try {
            val userDoc = db.collection(FirestoreCollection.USERS).document(userId).get().await()
            val name = userDoc.getString("name").orEmpty().trim()
            val username = userDoc.getString("username").orEmpty().trim()
            val email = userDoc.getString("email").orEmpty().trim()

            val displayName = when {
                name.isNotBlank() && !name.contains("@") -> name
                username.isNotBlank() -> username.removePrefix("@")
                email.isNotBlank() -> email.substringBefore("@")
                name.isNotBlank() -> name
                else -> "Unknown"
            }
            val photo = listOf(
                userDoc.getString("profileImageUrl"),
                userDoc.getString("profilePictureUrl"),
                userDoc.getString("profilePicUrl"),
                userDoc.getString("avatarUrl")
            ).firstOrNull { !it.isNullOrBlank() }?.trim()
            (displayName to photo).also { userProfileCache[userId] = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve user profile for $userId", e)
            "Unknown" to null
        }
    }

    private suspend fun resolveGroupTitle(chatId: String, fallbackName: String): String {
        if (fallbackName.isNotBlank()) return fallbackName
        groupTitleCache[chatId]?.let { return it }

        return try {
            val chatDoc = db.collection(FirestoreCollection.CHATS).document(chatId).get().await()
            val title = chatDoc.getString("groupName").orEmpty().ifBlank { "Group call" }
            groupTitleCache[chatId] = title
            title
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve group title for chat $chatId", e)
            "Group call"
        }
    }

    private fun uniqueCallKey(call: CallLog): String {
        if (call.id.isNotBlank()) return "${call.chatId}:${call.id}"
        val peerId = call.receiverId.ifBlank { call.peerUid }
        val anchorTime = call.startedAt?.time ?: call.endedAt?.time ?: 0L
        return "${call.chatId}:${call.callerId}:${peerId}:${anchorTime}"
    }

    private fun callHistoryHiddenDocId(key: String): String {
        return key.replace("/", "_").take(1200)
    }

    private fun cancelFetchJobs() {
        fetchJob?.cancel()
        fetchJob = null
        preflightRetryJob?.cancel()
        preflightRetryJob = null
    }

    override fun onCleared() {
        super.onCleared()
        Firebase.auth.removeAuthStateListener(authListener)
        cancelFetchJobs()
    }

    companion object {
        private const val TAG = "CallHistoryVM"
        private const val MAX_PREFLIGHT_RETRIES = 1
        private const val PREFLIGHT_RETRY_DELAY_MS = 1_500L
        private const val LEGACY_CHAT_SCAN_LIMIT = 50L
        private const val LEGACY_CALL_LOGS_PER_CHAT_LIMIT = 30L
    }
}
