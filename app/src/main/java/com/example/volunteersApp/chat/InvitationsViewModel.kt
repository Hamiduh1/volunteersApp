package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

/**
 * Represents a single chat invitation.
 * The default values are crucial for Firebase's toObject() deserialization.
 */


/**
 * Represents the different states of the Invitations screen UI.
 */
sealed class InvitationsUiState {
    data object Loading : InvitationsUiState()
    data class Success(val invitations: List<Invitation>) : InvitationsUiState()
    data class Error(val message: String) : InvitationsUiState()
}

/**
 * Represents one-time events that the UI should react to, like toasts or navigation.
 */
sealed class InvitationEvent {
    data class ShowToast(val message: String) : InvitationEvent()
    /**
     * @param successMessage Optional copy shown as toast/snackbar before navigation (StateFlow cannot emit two events in one turn).
     */
    data class NavigateToChat(
        val chatId: String,
        val otherUserId: String,
        val successMessage: String? = null
    ) : InvitationEvent()
}

class InvitationsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private var invitationListener: ListenerRegistration? = null
    private var blindDateListener: ListenerRegistration? = null
    private var legacyListener: ListenerRegistration? = null

    private var standardInvitations: List<Invitation> = emptyList()
    private var blindDateInvitations: List<Invitation> = emptyList()
    private var legacyInvitations: List<Invitation> = emptyList()
    private var blockedUserIds: Set<String> = emptySet()

    private val _uiState = MutableStateFlow<InvitationsUiState>(InvitationsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableStateFlow<InvitationEvent?>(null)
    val events = _events.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private var boundInviteUid: String? = null

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
            boundInviteUid = null
            invitationListener?.remove()
            blindDateListener?.remove()
            legacyListener?.remove()
            invitationListener = null
            blindDateListener = null
            legacyListener = null
            standardInvitations = emptyList()
            blindDateInvitations = emptyList()
            legacyInvitations = emptyList()
            blockedUserIds = emptySet()
            _uiState.value = InvitationsUiState.Error("You must be logged in.")
            return
        }
        if (uid == boundInviteUid && invitationListener != null) {
            return
        }
        boundInviteUid = uid
        refresh()
    }

    fun clearError() {
        if (_uiState.value is InvitationsUiState.Error) {
            refresh()
        }
    }

    fun refresh() {
        invitationListener?.remove()
        blindDateListener?.remove()
        legacyListener?.remove()
        invitationListener = null
        blindDateListener = null
        legacyListener = null
        standardInvitations = emptyList()
        blindDateInvitations = emptyList()
        legacyInvitations = emptyList()
        blockedUserIds = emptySet()
        _statusMessage.value = null
        _uiState.value = InvitationsUiState.Loading
        listenForInvitations()
    }

    private fun listenForInvitations() {
        val user = Firebase.auth.currentUser
        if (user == null) {
            _uiState.value = InvitationsUiState.Error("You must be logged in.")
            return
        }
        if (invitationListener != null || blindDateListener != null || legacyListener != null) return
        viewModelScope.launch {
            blockedUserIds = try {
                db.fetchBlockedUserIds(user.uid)
            } catch (ex: FirebaseFirestoreException) {
                Log.w(TAG, "Block list unavailable.", ex)
                emptySet()
            } catch (ex: Exception) {
                Log.w(TAG, "Block list unavailable.", ex)
                emptySet()
            }
        }

        // Match iOS `ChatRepository.fetchInvitations`: read up to [INVITE_QUERY_LIMIT] docs per source (all statuses).
        invitationListener = db.collection(FirestoreCollection.USERS).document(user.uid)
            .collection(FirestoreSubcollection.INVITATIONS)
            .limit(INVITE_QUERY_LIMIT.toLong())
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Failed to load invitations.", e)
                    detachInvitationListenersOnFatalError(e)
                    _uiState.value = InvitationsUiState.Error(
                        userMessageForFirestoreListenFailure(e, "invitations")
                    )
                    return@addSnapshotListener
                }
                standardInvitations = snapshots?.mapNotNull { doc ->
                    val senderId = doc.getString("senderId") ?: doc.id
                    val senderName = doc.getString("senderName") ?: "Someone"
                    val profileUrl = doc.getString("senderProfilePicUrl")
                        ?: doc.getString("senderProfileImageUrl")
                    Invitation(
                        documentId = doc.id,
                        senderId = senderId,
                        senderName = senderName,
                        inviterName = senderName,
                        senderEmail = doc.getString("senderEmail").orEmpty(),
                        senderProfileImageUrl = profileUrl,
                        status = doc.getString("status") ?: "pending",
                        context = doc.getString("context") ?: "Chat",
                        source = "chat",
                        matchedChatId = doc.getString("matchedChatId"),
                        timestamp = doc.get("timestamp")
                    )
                } ?: emptyList()
                emitMergedInvitations()
            }

        blindDateListener = db.collection(FirestoreCollection.USERS).document(user.uid)
            .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS)
            .limit(INVITE_QUERY_LIMIT.toLong())
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Failed to load blind date invitations.", e)
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        blindDateListener?.remove()
                        blindDateListener = null
                    }
                    return@addSnapshotListener
                }
                blindDateInvitations = snapshots?.mapNotNull { doc ->
                    val senderId = doc.getString("senderId") ?: doc.id
                    val senderName = doc.getString("senderName") ?: "Someone"
                    val profileUrl = doc.getString("senderProfilePictureUrl")
                        ?: doc.getString("senderProfileImageUrl")
                    Invitation(
                        documentId = doc.id,
                        senderId = senderId,
                        senderName = senderName,
                        inviterName = senderName,
                        senderEmail = doc.getString("senderEmail").orEmpty(),
                        senderProfileImageUrl = profileUrl,
                        status = doc.getString("status") ?: "pending",
                        context = "Blind Date",
                        source = "blind_date",
                        matchedChatId = doc.getString("matchedChatId"),
                        timestamp = doc.get("sentAt") ?: doc.get("timestamp")
                    )
                } ?: emptyList()
                emitMergedInvitations()
            }

        legacyListener = db.collection(FirestoreCollection.USERS).document(user.uid)
            .collection(FirestoreSubcollection.CHAT_INVITATIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(INVITE_QUERY_LIMIT.toLong())
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Failed to load legacy invitations.", e)
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        legacyListener?.remove()
                        legacyListener = null
                    }
                    return@addSnapshotListener
                }
                legacyInvitations = snapshots?.mapNotNull { doc ->
                    val senderId = doc.id
                    val senderName = doc.getString("inviterName") ?: "Someone"
                    Invitation(
                        documentId = doc.id,
                        senderId = senderId,
                        senderName = senderName,
                        inviterName = senderName,
                        senderEmail = doc.getString("senderEmail").orEmpty(),
                        senderProfileImageUrl = null,
                        status = doc.getString("status") ?: "pending",
                        context = "Volunteer Directory",
                        source = "legacy_chat",
                        timestamp = doc.get("timestamp")
                    )
                } ?: emptyList()
                emitMergedInvitations()
            }
    }

    private fun detachInvitationListenersOnFatalError(e: Exception) {
        if (e !is FirebaseFirestoreException || e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return
        invitationListener?.remove()
        blindDateListener?.remove()
        legacyListener?.remove()
        invitationListener = null
        blindDateListener = null
        legacyListener = null
    }

    private fun emitMergedInvitations() {
        val merged = (standardInvitations + blindDateInvitations + legacyInvitations)
            .filter { it.senderId !in blockedUserIds }
            .distinctBy { "${it.source}:${it.senderId}" }
            .sortedByDescending { resolveTimestamp(it) }
        _uiState.value = InvitationsUiState.Success(merged)
        val pendingCount = merged.count { normalizeInvitationStatus(it.status) == "pending" }
        _statusMessage.value = null
    }

    private fun resolveTimestamp(invitation: Invitation): Long {
        return resolveTimestampValue(invitation.timestamp)
    }

    private fun resolveTimestampValue(value: Any?): Long {
        return when (value) {
            is com.google.firebase.Timestamp -> value.toDate().time
            is Date -> value.time
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            else -> 0L
        }
    }

    fun acceptInvitation(invitation: Invitation) {
        viewModelScope.launch {
            val currentUserId = Firebase.auth.currentUser?.uid
            if (currentUserId == null) {
                _events.value = InvitationEvent.ShowToast("Authentication Error.")
                return@launch
            }

            // The sender of the invitation is the "other user"
            val otherUserId = invitation.senderId
            val invitationDocId = invitation.documentId.ifBlank { otherUserId }
            if (otherUserId.isBlank()) {
                _events.value = InvitationEvent.ShowToast("Invalid sender information.")
                return@launch
            }
            if (otherUserId == currentUserId) {
                _events.value = InvitationEvent.ShowToast("You cannot accept your own invitation.")
                return@launch
            }

            if (invitation.source == "blind_date") {
                acceptBlindDateInvitation(invitation)
                return@launch
            }

            try {
                val existingChatId = resolveLinkedChatId(
                    currentUserId = currentUserId,
                    otherUserId = otherUserId,
                    invitation = invitation,
                )
                val targetChatRef = existingChatId?.let {
                    db.collection(FirestoreCollection.CHATS).document(it)
                } ?: db.collection(FirestoreCollection.CHATS).document()

                db.runBatch { batch ->
                    if (existingChatId == null) {
                        val chatData = mapOf(
                            "chatId" to targetChatRef.id,
                            "participants" to listOf(currentUserId, otherUserId),
                            "lastMessageText" to "Chat started! Say hi.",
                            "lastMessage" to "Chat started! Say hi.",
                            "lastMessageTimestamp" to FieldValue.serverTimestamp()
                        )
                        batch.set(targetChatRef, chatData)
                    }

                    addInvitationAcceptMutation(
                        batch = batch,
                        currentUserId = currentUserId,
                        invitation = invitation,
                        invitationDocId = invitationDocId,
                        matchedChatId = targetChatRef.id,
                    )
                }.await()

                _events.value = InvitationEvent.NavigateToChat(
                    chatId = targetChatRef.id,
                    otherUserId = otherUserId,
                    successMessage = "Invitation accepted!"
                )
                _statusMessage.value = "Invitation accepted."

            } catch (e: Exception) {
                Log.e(TAG, "Error accepting invitation", e)
                val msg = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    "Couldn't accept — Firestore denied this action. Sign in again or check your account can create chats."
                } else {
                    "Failed to accept invitation."
                }
                _events.value = InvitationEvent.ShowToast(msg)
            }
        }
    }

    private suspend fun resolveLinkedChatId(
        currentUserId: String,
        otherUserId: String,
        invitation: Invitation,
    ): String? {
        invitation.matchedChatId?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return findExistingDirectChat(currentUserId, otherUserId)
    }

    private suspend fun findExistingDirectChat(currentUserId: String, otherUserId: String): String? {
        return db.collection(FirestoreCollection.CHATS)
            .whereArrayContains("participants", currentUserId)
            .get()
            .await()
            .documents
            .asSequence()
            .mapNotNull { doc ->
                val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>()
                    ?.distinct()
                    .orEmpty()
                val isGroup = doc.getString("chatType") == "group" || participants.size > 2
                if (isGroup || participants.toSet() != setOf(currentUserId, otherUserId)) {
                    return@mapNotNull null
                }

                doc.id to resolveTimestampValue(
                    doc.get("lastMessageTimestamp")
                        ?: doc.get("updatedAt")
                        ?: doc.get("createdAt")
                )
            }
            .maxByOrNull { it.second }
            ?.first
    }

    fun declineInvitation(invitation: Invitation) {
        viewModelScope.launch {
            val currentUserId = Firebase.auth.currentUser?.uid
            if (currentUserId == null) {
                _events.value = InvitationEvent.ShowToast("Authentication Error.")
                return@launch
            }

            val otherUserId = invitation.senderId
            val invitationDocId = invitation.documentId.ifBlank { otherUserId }
            if (otherUserId.isBlank()) {
                _events.value = InvitationEvent.ShowToast("Invalid sender information.")
                return@launch
            }

            try {
                when (invitation.source) {
                    "blind_date" -> declineBlindDateInvitation(
                        currentUserId = currentUserId,
                        senderId = otherUserId,
                        invitationDocId = invitationDocId,
                    )
                    "legacy_chat" -> {
                        db.collection(FirestoreCollection.USERS).document(currentUserId)
                            .collection(FirestoreSubcollection.CHAT_INVITATIONS).document(invitationDocId)
                            .delete().await()
                    }
                    else -> {
                        // To decline, we delete the invitation document from our own subcollection.
                        // Use the actual invitation document ID when available.
                        db.collection(FirestoreCollection.USERS).document(currentUserId)
                            .collection(FirestoreSubcollection.INVITATIONS).document(invitationDocId)
                            .delete().await()
                    }
                }

                _events.value = InvitationEvent.ShowToast("Invitation declined.")
                _statusMessage.value = "Invitation declined."
            } catch (e: Exception) {
                Log.e(TAG, "Error declining invitation", e)
                val msg = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    "Couldn't update invitation — access denied."
                } else {
                    "Failed to decline invitation."
                }
                _events.value = InvitationEvent.ShowToast(msg)
            }
        }
    }

    private suspend fun declineBlindDateInvitation(
        currentUserId: String,
        senderId: String,
        invitationDocId: String,
    ) {
        val declinedViaCallable = runCatching {
            FunctionsClient.callData(
                CallableFunction.DECLINE_BLIND_DATE_INVITATION,
                mapOf("senderId" to senderId)
            )
            true
        }.getOrElse { error ->
            if (error is FirebaseFunctionsException &&
                error.code == FirebaseFunctionsException.Code.NOT_FOUND
            ) {
                Log.w(TAG, "declineBlindDateInvitation not deployed; using Firestore fallback")
            } else {
                Log.w(TAG, "declineBlindDateInvitation failed; using Firestore fallback", error)
            }
            false
        }

        if (!declinedViaCallable) {
            db.collection(FirestoreCollection.USERS).document(currentUserId)
                .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS).document(invitationDocId)
                .delete()
                .await()
        }
    }

    private suspend fun acceptBlindDateInvitation(invitation: Invitation) {
        val currentUserId = Firebase.auth.currentUser?.uid ?: return
        val otherUserId = invitation.senderId.trim()
        val invitationDocId = invitation.documentId.ifBlank { otherUserId }

        invitation.matchedChatId?.trim()?.takeIf { it.isNotBlank() }?.let { linkedChatId ->
            runCatching {
                markInvitationAccepted(currentUserId, invitation, invitationDocId, linkedChatId)
            }.onFailure { e ->
                Log.w(TAG, "Failed to mark blind date invitation accepted for linked chat", e)
            }
            _events.value = InvitationEvent.NavigateToChat(
                chatId = linkedChatId,
                otherUserId = otherUserId,
                successMessage = "Invitation accepted!"
            )
            _statusMessage.value = "Invitation accepted."
            return
        }

        try {
            val result = FunctionsClient.callData(
                CallableFunction.ACCEPT_BLIND_DATE_INVITATION,
                mapOf("senderId" to otherUserId)
            )
            val chatId = parseChatIdFromCallableResult(result)
                ?: resolveLinkedChatId(currentUserId, otherUserId, invitation)
            if (chatId.isNullOrBlank()) {
                _events.value = InvitationEvent.ShowToast("Failed to start chat.")
                return
            }
            runCatching {
                markInvitationAccepted(currentUserId, invitation, invitationDocId, chatId)
            }.onFailure { e ->
                Log.w(TAG, "Failed to mark blind date invitation accepted after callable", e)
            }
            _events.value = InvitationEvent.NavigateToChat(
                chatId = chatId,
                otherUserId = otherUserId,
                successMessage = "Invitation accepted!"
            )
            _statusMessage.value = "Invitation accepted."
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting blind date invitation", e)
            _events.value = InvitationEvent.ShowToast(e.message ?: "Failed to accept invitation.")
        }
    }

    private fun addInvitationAcceptMutation(
        batch: com.google.firebase.firestore.WriteBatch,
        currentUserId: String,
        invitation: Invitation,
        invitationDocId: String,
        matchedChatId: String,
    ) {
        when (invitation.source) {
            "legacy_chat" -> {
                val legacyRef = db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.CHAT_INVITATIONS).document(invitationDocId)
                batch.delete(legacyRef)
            }
            "blind_date" -> {
                val inviteRef = db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS).document(invitationDocId)
                batch.set(
                    inviteRef,
                    mapOf(
                        "status" to "accepted",
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "respondedAt" to FieldValue.serverTimestamp(),
                        "matchedChatId" to matchedChatId,
                    ),
                    SetOptions.merge()
                )
            }
            else -> {
                val inviteRef = db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.INVITATIONS).document(invitationDocId)
                batch.set(
                    inviteRef,
                    mapOf(
                        "status" to "accepted",
                        "lastUpdatedAt" to FieldValue.serverTimestamp(),
                        "matchedChatId" to matchedChatId,
                    ),
                    SetOptions.merge()
                )
            }
        }
    }

    private suspend fun markInvitationAccepted(
        currentUserId: String,
        invitation: Invitation,
        invitationDocId: String,
        matchedChatId: String? = null,
    ) {
        when (invitation.source) {
            "legacy_chat" -> {
                db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.CHAT_INVITATIONS).document(invitationDocId)
                    .delete()
                    .await()
            }
            "blind_date" -> {
                val payload = mutableMapOf<String, Any>(
                    "status" to "accepted",
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "respondedAt" to FieldValue.serverTimestamp(),
                )
                matchedChatId?.trim()?.takeIf { it.isNotBlank() }?.let { payload["matchedChatId"] = it }
                db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS).document(invitationDocId)
                    .set(payload, SetOptions.merge())
                    .await()
            }
            else -> {
                val payload = mutableMapOf<String, Any>(
                    "status" to "accepted",
                    "lastUpdatedAt" to FieldValue.serverTimestamp(),
                )
                matchedChatId?.trim()?.takeIf { it.isNotBlank() }?.let { payload["matchedChatId"] = it }
                db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.INVITATIONS).document(invitationDocId)
                    .set(payload, SetOptions.merge())
                    .await()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseChatIdFromCallableResult(result: Any?): String? {
        val map = result as? Map<String, Any?> ?: return null
        return (map["chatId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun onEventHandled() {
        _events.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Firebase.auth.removeAuthStateListener(authListener)
        invitationListener?.remove()
        blindDateListener?.remove()
        legacyListener?.remove()
    }

    companion object {
        private const val TAG = "InvitationsViewModel"
        private const val INVITE_QUERY_LIMIT = 100
    }
}
