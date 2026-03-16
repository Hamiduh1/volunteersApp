package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Modernized ViewModel for the User Directory screen.
 * Handles fetching, filtering, and sending chat invitations using reactive StateFlow.
 */
class UserDirectoryViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val currentUser get() = auth.currentUser

    private val _uiState = MutableStateFlow(UserDirectoryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchUsers()
    }

    /**
     * Fetches all users from Firestore, excluding the current user.
     */
    fun fetchUsers() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            val user = currentUser
            if (user == null) {
                _uiState.update { it.copy(isLoading = false, error = "User not authenticated.") }
                return@launch
            }

            try {
                // Fetching from 'users' collection ordered by name
                val result = db.collection("users")
                    .orderBy("name", Query.Direction.ASCENDING)
                    .get()
                    .await()

                val users = result.toObjects<User>().filter { it.uid != user.uid }
                
                _uiState.update { it.copy(isLoading = false, allUsers = users) }
                Log.d(TAG, "Successfully fetched ${users.size} users.")

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching users", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load directory: ${e.localizedMessage}") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Sends a chat invitation to a specific user.
     */
    fun sendChatInvitation(recipient: User, onComplete: (success: Boolean, message: String) -> Unit) {
        val inviter = currentUser
        if (inviter == null) {
            onComplete(false, "Authentication required.")
            return
        }

        val recipientId = recipient.uid
        if (recipientId.isBlank()) {
            onComplete(false, "Invalid user selection.")
            return
        }

        viewModelScope.launch {
            // The document ID is still the inviter's UID to prevent duplicates
            val invitationRef = db.collection("users").document(recipientId)
                .collection("invitations").document(inviter.uid)

            try {
                val existingInvite = invitationRef.get().await()
                if (existingInvite.exists()) {
                    onComplete(false, "Invitation already sent to ${recipient.name}.")
                    return@launch
                }

                // --- FIX: Add the 'senderId' field to the data map ---
                // This makes the document self-contained and easy to parse.
                val invitationData = hashMapOf(
                    "senderId" to inviter.uid, // Explicitly add senderId
                    "senderName" to (inviter.displayName ?: "A User"),
                    "senderProfilePicUrl" to (inviter.photoUrl?.toString() ?: ""),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "User Directory"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                onComplete(true, "Chat invitation sent!")

            } catch (e: FirebaseFirestoreException) {
                Log.e(TAG, "Failed to send invitation", e)
                onComplete(false, "Could not send invitation. Please try again.")
            }
        }
    }

    companion object {
        private const val TAG = "UserDirectoryVM"
    }
}
