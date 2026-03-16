package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

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
    data class NavigateToChat(val chatId: String, val otherUserId: String) : InvitationEvent()
}

class InvitationsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser
    private var invitationListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow<InvitationsUiState>(InvitationsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableStateFlow<InvitationEvent?>(null)
    val events = _events.asStateFlow()

    init {
        listenForInvitations()
    }

    private fun listenForInvitations() {
        if (currentUser == null) {
            _uiState.value = InvitationsUiState.Error("You must be logged in.")
            return
        }
        if (invitationListener != null) return

        invitationListener = db.collection("users").document(currentUser.uid)
            .collection("invitations")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Failed to load invitations.", e)
                    _uiState.value = InvitationsUiState.Error("Failed to load invitations.")
                    return@addSnapshotListener
                }

                // FIX: Correctly parse the Invitation object.
                // Since 'senderId' is now a field in the document, a direct toObject() call works reliably.
                val invitations = snapshots?.mapNotNull { doc ->
                    doc.toObject(Invitation::class.java)
                } ?: emptyList()

                _uiState.value = InvitationsUiState.Success(invitations)
            }
    }

    fun acceptInvitation(invitation: Invitation) {
        viewModelScope.launch {
            val currentUserId = currentUser?.uid
            if (currentUserId == null) {
                _events.value = InvitationEvent.ShowToast("Authentication Error.")
                return@launch
            }

            // The sender of the invitation is the "other user"
            val otherUserId = invitation.senderId
            if (otherUserId.isBlank()) {
                _events.value = InvitationEvent.ShowToast("Invalid sender information.")
                return@launch
            }

            val newChatRef = db.collection("chats").document()

            try {
                db.runBatch { batch ->
                    // 1. Create the new chat document
                    val chatData = mapOf(
                        "chatId" to newChatRef.id,
                        "participants" to listOf(currentUserId, otherUserId),
                        "lastMessageText" to "Chat started! Say hi.",
                        "lastMessageTimestamp" to FieldValue.serverTimestamp()
                    )
                    batch.set(newChatRef, chatData)

                    // 2. Update the invitation status in the recipient's (our) subcollection.
                    // The document ID of the invitation is the sender's ID (otherUserId).
                    val myInvitationRef = db.collection("users").document(currentUserId)
                        .collection("invitations").document(otherUserId)
                    batch.update(myInvitationRef, "status", "accepted")

                }.await()

                _events.value = InvitationEvent.ShowToast("Invitation accepted!")
                _events.value = InvitationEvent.NavigateToChat(newChatRef.id, otherUserId)

            } catch (e: Exception) {
                Log.e(TAG, "Error accepting invitation", e)
                _events.value = InvitationEvent.ShowToast("Failed to accept invitation.")
            }
        }
    }

    fun declineInvitation(invitation: Invitation) {
        viewModelScope.launch {
            val currentUserId = currentUser?.uid
            if (currentUserId == null) {
                _events.value = InvitationEvent.ShowToast("Authentication Error.")
                return@launch
            }

            val otherUserId = invitation.senderId
            if (otherUserId.isBlank()) {
                _events.value = InvitationEvent.ShowToast("Invalid sender information.")
                return@launch
            }

            try {
                // To decline, we delete the invitation document from our own subcollection.
                // The document ID is the sender's ID.
                db.collection("users").document(currentUserId)
                    .collection("invitations").document(otherUserId)
                    .delete().await()

                _events.value = InvitationEvent.ShowToast("Invitation declined.")
            } catch (e: Exception) {
                Log.e(TAG, "Error declining invitation", e)
                _events.value = InvitationEvent.ShowToast("Failed to decline invitation.")
            }
        }
    }

    fun onEventHandled() {
        _events.value = null
    }

    override fun onCleared() {
        super.onCleared()
        invitationListener?.remove()
    }

    companion object {
        private const val TAG = "InvitationsViewModel"
    }
}
