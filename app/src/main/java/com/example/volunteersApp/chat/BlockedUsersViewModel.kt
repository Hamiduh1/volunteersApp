package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class BlockedUserEntry(
    val uid: String,
    val displayName: String,
    val profileImageUrl: String? = null,
)

data class BlockedUsersUiState(
    val blockedUsers: List<BlockedUserEntry> = emptyList(),
    val unblockingUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

class BlockedUsersViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(BlockedUsersUiState())
    val uiState: StateFlow<BlockedUsersUiState> = _uiState.asStateFlow()

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
                    statusMessage = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(isLoading = true, errorMessage = null, statusMessage = null)
        }

        viewModelScope.launch {
            try {
                val blockedIds = db.fetchBlockedUserIds(currentUid)
                val entries = blockedIds.mapNotNull { uid ->
                    runCatching { loadBlockedUserEntry(uid) }.getOrNull()
                }.sortedBy { it.displayName.lowercase() }

                _uiState.update {
                    it.copy(
                        blockedUsers = entries,
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = if (entries.isEmpty()) {
                            "No blocked users."
                        } else {
                            "${entries.size} blocked user${if (entries.size == 1) "" else "s"}."
                        },
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to load blocked users", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load blocked users.",
                    )
                }
            }
        }
    }

    fun unblockUser(uid: String) {
        val currentUid = auth.currentUser?.uid?.trim().orEmpty()
        val targetUid = uid.trim()
        if (currentUid.isBlank() || targetUid.isBlank()) return
        if (targetUid in _uiState.value.unblockingUserIds) return

        _uiState.update {
            it.copy(
                unblockingUserIds = it.unblockingUserIds + targetUid,
                statusMessage = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val updatedBlocked = db.fetchBlockedUserIds(currentUid) - targetUid
                writeBlockedUsersToSafetyStore(currentUid, updatedBlocked)
                val removedName = _uiState.value.blockedUsers
                    .firstOrNull { it.uid == targetUid }
                    ?.displayName
                    ?: "User"
                _uiState.update {
                    it.copy(
                        blockedUsers = it.blockedUsers.filterNot { entry -> entry.uid == targetUid },
                        unblockingUserIds = it.unblockingUserIds - targetUid,
                        statusMessage = "$removedName unblocked.",
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to unblock user $targetUid", error)
                _uiState.update {
                    it.copy(
                        unblockingUserIds = it.unblockingUserIds - targetUid,
                        errorMessage = error.message ?: "Could not unblock this user right now.",
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun loadBlockedUserEntry(uid: String): BlockedUserEntry? {
        val doc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
        if (!doc.exists()) return BlockedUserEntry(uid = uid, displayName = "Unknown user")
        return BlockedUserEntry(
            uid = uid,
            displayName = resolveBlockedUserDisplayName(doc),
            profileImageUrl = doc.resolveProfileImageUrl(),
        )
    }

    private suspend fun writeBlockedUsersToSafetyStore(
        currentUid: String,
        blockedUsers: Set<String>,
    ) {
        db.collection(FirestoreCollection.USERS)
            .document(currentUid)
            .collection(FirestoreSubcollection.SETTINGS)
            .document(CHAT_PRIVACY_SETTINGS_DOC)
            .set(
                mapOf(
                    "blockedUserIds" to blockedUsers.toList().sorted(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun resolveBlockedUserDisplayName(doc: DocumentSnapshot): String {
        val name = doc.getString("name").orEmpty().trim()
        val username = doc.getString("username").orEmpty().trim().removePrefix("@")
        val email = doc.getString("email").orEmpty().trim()
        return when {
            name.isNotBlank() && !name.contains("@") -> name
            username.isNotBlank() -> username
            email.isNotBlank() -> email.substringBefore("@")
            name.isNotBlank() -> name
            else -> "Unknown user"
        }
    }

    companion object {
        private const val TAG = "BlockedUsersVM"
    }
}
