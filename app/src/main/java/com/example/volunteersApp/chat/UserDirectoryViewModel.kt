package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserDirectoryViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(UserDirectoryUiState())
    val uiState: StateFlow<UserDirectoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val currentUid = auth.currentUser?.uid?.trim().orEmpty()
        if (currentUid.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Authentication required.",
                    statusMessage = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val blockedUsers = runCatching { db.fetchBlockedUserIds(currentUid) }
                    .getOrElse {
                        Log.w(TAG, "Blocked list unavailable; continuing with empty blocks", it)
                        emptySet()
                    }
                val users = fetchDirectoryUsers(currentUid)
                val sentInvitations = fetchSentInvitationRecipientIds()
                val visibleCount = users.count { user -> user.uid !in blockedUsers }
                val status = if (blockedUsers.isEmpty()) {
                    "Loaded $visibleCount users."
                } else {
                    "Loaded $visibleCount users. ${blockedUsers.size} blocked hidden."
                }
                _uiState.update {
                    it.copy(
                        allUsers = users,
                        blockedUsers = blockedUsers,
                        sentInvitationUserIds = sentInvitations,
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = status
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to refresh user directory", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = directoryErrorMessage(error, "refresh the directory")
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun sendInvitation(recipient: DirectoryUser) {
        val sender = auth.currentUser
        if (sender == null) {
            _uiState.update { it.copy(errorMessage = "Authentication required.") }
            return
        }

        val recipientUid = recipient.uid.trim()
        val currentState = _uiState.value
        if (recipientUid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Invalid user selection.") }
            return
        }
        if (recipientUid == sender.uid) {
            _uiState.update { it.copy(errorMessage = "You cannot invite yourself.") }
            return
        }
        val inviteBlockedReason = when {
            recipientUid in currentState.sentInvitationUserIds -> "Invite already sent."
            recipientUid in currentState.sendingInvitationUserIds -> "Invite is still sending."
            recipientUid in currentState.blockingUserIds ||
                recipientUid in currentState.blockedUsers ->
                "Invites are not available for blocked users."
            else -> null
        }
        if (inviteBlockedReason != null) {
            _uiState.update { it.copy(statusMessage = inviteBlockedReason) }
            return
        }

        _uiState.update {
            it.copy(
                sendingInvitationUserIds = it.sendingInvitationUserIds + recipientUid,
                statusMessage = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                sendDirectoryChatInvitation(sender, recipient)
                _uiState.update {
                    it.copy(
                        sendingInvitationUserIds = it.sendingInvitationUserIds - recipientUid,
                        sentInvitationUserIds = it.sentInvitationUserIds + recipientUid,
                        statusMessage = "Chat invitation sent!",
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to send directory invitation", error)
                val friendly = mapDirectoryInviteError(error)
                _uiState.update {
                    it.copy(
                        sendingInvitationUserIds = it.sendingInvitationUserIds - recipientUid,
                        errorMessage = friendly
                    )
                }
            }
        }
    }

    fun blockUser(recipient: DirectoryUser) {
        val currentUid = auth.currentUser?.uid?.trim().orEmpty()
        val recipientUid = recipient.uid.trim()
        if (currentUid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Authentication required.") }
            return
        }
        if (recipientUid.isBlank() || recipientUid == currentUid) {
            _uiState.update { it.copy(errorMessage = "Invalid user selection.") }
            return
        }
        if (recipientUid in _uiState.value.blockingUserIds || recipientUid in _uiState.value.blockedUsers) {
            return
        }

        _uiState.update {
            it.copy(
                blockingUserIds = it.blockingUserIds + recipientUid,
                statusMessage = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val updatedBlockedUsers = (_uiState.value.blockedUsers + recipientUid).toSet()
                writeBlockedUsersToSafetyStore(
                    currentUid = currentUid,
                    blockedUsers = updatedBlockedUsers
                )

                _uiState.update {
                    it.copy(
                        blockedUsers = updatedBlockedUsers,
                        blockingUserIds = it.blockingUserIds - recipientUid,
                        sendingInvitationUserIds = it.sendingInvitationUserIds - recipientUid,
                        sentInvitationUserIds = it.sentInvitationUserIds - recipientUid,
                        statusMessage = "${recipient.name} blocked. They are now hidden from your directory and group picks.",
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to block directory user", error)
                _uiState.update {
                    it.copy(
                        blockingUserIds = it.blockingUserIds - recipientUid,
                        errorMessage = directoryErrorMessage(error, "block this user")
                    )
                }
            }
        }
    }

    fun unblockUser(recipient: DirectoryUser) {
        val currentUid = auth.currentUser?.uid?.trim().orEmpty()
        val recipientUid = recipient.uid.trim()
        if (currentUid.isBlank() || recipientUid.isBlank()) return
        if (recipientUid !in _uiState.value.blockedUsers) return
        if (recipientUid in _uiState.value.unblockingUserIds) return

        _uiState.update {
            it.copy(
                unblockingUserIds = it.unblockingUserIds + recipientUid,
                statusMessage = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val updatedBlockedUsers = _uiState.value.blockedUsers - recipientUid
                writeBlockedUsersToSafetyStore(
                    currentUid = currentUid,
                    blockedUsers = updatedBlockedUsers,
                )
                _uiState.update {
                    it.copy(
                        blockedUsers = updatedBlockedUsers,
                        unblockingUserIds = it.unblockingUserIds - recipientUid,
                        statusMessage = "${recipient.name} unblocked.",
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to unblock directory user", error)
                _uiState.update {
                    it.copy(
                        unblockingUserIds = it.unblockingUserIds - recipientUid,
                        errorMessage = directoryErrorMessage(error, "unblock this user"),
                    )
                }
            }
        }
    }

    private suspend fun fetchDirectoryUsers(currentUid: String): List<DirectoryUser> {
        val snapshot = db.collection(FirestoreCollection.USERS)
            .limit(500)
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { document -> document.toDirectoryUser(currentUid) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private suspend fun fetchSentInvitationRecipientIds(): Set<String> {
        // Invitations live under recipients. A broad collection-group query would violate the
        // recipient-scoped Firestore rules, so direct document checks remain the source of truth.
        return emptySet()
    }

    private suspend fun sendDirectoryChatInvitation(sender: FirebaseUser, recipient: DirectoryUser) {
        val recipientUid = recipient.uid.trim()
        if (recipientUid.isBlank()) {
            throw IllegalStateException("Invalid user selection.")
        }

        val invitationRef = db.collection(FirestoreCollection.USERS)
            .document(recipientUid)
            .collection(FirestoreSubcollection.INVITATIONS)
            .document(sender.uid)

        val existing = invitationRef.get().await()
        if (existing.exists()) {
            throw IllegalStateException("Invitation already sent to ${recipient.name}.")
        }

        val senderSummary = fetchSenderSummary(
            uid = sender.uid,
            authDisplayName = sender.displayName,
            authPhotoUrl = sender.photoUrl?.toString()
        )

        invitationRef.set(
            hashMapOf(
                "senderId" to sender.uid,
                "senderName" to senderSummary.name,
                "senderProfilePicUrl" to senderSummary.photoUrl,
                "senderProfileImageUrl" to senderSummary.photoUrl,
                "status" to "pending",
                "timestamp" to FieldValue.serverTimestamp(),
                "context" to "User Directory"
            ),
            SetOptions.merge()
        ).await()
    }

    private suspend fun writeBlockedUsersToSafetyStore(
        currentUid: String,
        blockedUsers: Set<String>
    ) {
        db.collection(FirestoreCollection.USERS)
            .document(currentUid)
            .collection(FirestoreSubcollection.SETTINGS)
            .document(CHAT_PRIVACY_SETTINGS_DOC)
            .set(
                mapOf(
                    "blockedUserIds" to blockedUsers.toList().sorted(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    private data class SenderSummary(
        val name: String,
        val photoUrl: String,
    )

    private suspend fun fetchSenderSummary(
        uid: String,
        authDisplayName: String?,
        authPhotoUrl: String?
    ): SenderSummary {
        val snapshot = runCatching {
            db.collection(FirestoreCollection.USERS).document(uid).get().await()
        }.getOrNull()

        val data = snapshot?.data.orEmpty()
        val name = listOf(
            data["name"] as? String,
            data["displayName"] as? String,
            authDisplayName
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty().ifBlank { "A User" }

        val photoUrl = listOf(
            data["profileImageUrl"] as? String,
            data["profilePictureUrl"] as? String,
            data["profilePicUrl"] as? String,
            data["avatarUrl"] as? String,
            authPhotoUrl
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        return SenderSummary(
            name = name,
            photoUrl = photoUrl
        )
    }

    private fun DocumentSnapshot.toDirectoryUser(currentUid: String): DirectoryUser? {
        val data = data ?: return null
        val uid = listOf(
            data["uid"] as? String,
            id
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        if (uid.isBlank() || uid == currentUid) return null

        val name = listOf(
            data["name"] as? String,
            data["displayName"] as? String,
            data["username"] as? String
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty().ifBlank { "Anonymous" }

        val username = (data["username"] as? String).orEmpty().trim()
        val profileImageUrl = listOf(
            data["profileImageUrl"] as? String,
            data["profilePictureUrl"] as? String,
            data["profilePicUrl"] as? String,
            data["avatarUrl"] as? String
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        return DirectoryUser(
            id = id,
            uid = uid,
            name = name,
            username = username,
            profileImageUrl = profileImageUrl
        )
    }

    private fun mapDirectoryInviteError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            recipientSelfMessage(raw) -> "You can’t invite yourself."
            raw.contains("block", ignoreCase = true) ||
                raw.contains("unblock", ignoreCase = true) ->
                "This person isn’t available to invite. Unblock them first if you previously blocked them."

            raw.contains("Invitation already", ignoreCase = true) ||
                raw.contains("already sent", ignoreCase = true) ->
                "An invitation was already sent to this user."

            raw.contains("not available", ignoreCase = true) ||
                raw.contains("PERMISSION_DENIED", ignoreCase = true) ||
                raw.contains("permission", ignoreCase = true) ->
                "This user isn’t available to invite right now."

            else -> "We couldn't send the invitation. Check your connection and try again."
        }
    }

    private fun directoryErrorMessage(error: Throwable, action: String): String {
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            return "We couldn't $action because directory access changed. Refresh and try again."
        }
        return "We couldn't $action. Check your connection and try again."
    }

    private fun recipientSelfMessage(raw: String): Boolean =
        raw.contains("yourself", ignoreCase = true) ||
            raw.contains("cannot invite yourself", ignoreCase = true)

    companion object {
        private const val TAG = "UserDirectoryVM"
    }
}
