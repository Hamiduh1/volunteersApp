package com.example.volunteersApp.chat
//StateFlow is the modern equivalent of LiveData for Kotlin-based projects
// and works seamlessly with Compose. We will also convert the asynchronous
// Firebase calls to use Kotlin's suspend functions, making the code much cleaner.

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Represents the possible states of the Chat List UI.
sealed interface ChatListUiState {
    data class Success(val conversations: List<ChatConversation>) : ChatListUiState
    data class Error(val message: String) : ChatListUiState
    object Loading : ChatListUiState
}

/**
 * ViewModel for the chat list screen.
 * This class is responsible for fetching the user's active conversations from Firestore
 * and exposing them to the UI through a StateFlow.
 */
class ChatListViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser
    private var conversationListener: ListenerRegistration? = null

    // Backing property for the UI state, exposed as a read-only StateFlow.
    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        // Start listening for conversations as soon as the ViewModel is created.
        listenForConversations()
    }

    /**
     * Attaches a snapshot listener to the 'chats' collection in Firestore.
     * It fetches all chats where the current user is a participant.
     */
    private fun listenForConversations() {
        if (currentUser == null) {
            _uiState.value = ChatListUiState.Error("You must be logged in to view chats.")
            return
        }
        // If a listener is already active, don't create a new one.
        if (conversationListener != null) return

        _uiState.value = ChatListUiState.Loading

        conversationListener = db.collection("chats")
            .whereArrayContains("participants", currentUser.uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    _uiState.value = ChatListUiState.Error("Failed to load chats.")
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    // Process the documents asynchronously in a coroutine.
                    viewModelScope.launch {
                        processSnapshots(snapshots.documents)
                    }
                } else {
                    // If there are no chat documents, emit an empty success state.
                    _uiState.value = ChatListUiState.Success(emptyList())
                }
            }
    }

    /**
     * Processes a list of chat documents, fetches details for the other participant in each chat,
     * and updates the UI state with the fully formed conversation list.
     */
    private suspend fun processSnapshots(documents: List<DocumentSnapshot>) {
        val newConversationList = mutableListOf<ChatConversation>()

        for (doc in documents) {
            val participants = doc.get("participants") as? List<String> ?: continue

            // Find the ID of the other user in the chat.
            val otherUserId = participants.firstOrNull { it != currentUser?.uid } ?: continue

            try {
                // Asynchronously fetch the other user's details from the 'users' collection.
                val userDoc = db.collection("users").document(otherUserId).get().await()

                val conversation = ChatConversation(
                    chatId = doc.id,
                    otherParticipantId = otherUserId,
                    lastMessageText = doc.getString("lastMessageText") ?: "",
                    otherParticipantName = userDoc.getString("name") ?: "Unknown User",
                    otherParticipantProfilePicUrl = userDoc.getString("profilePictureUrl"),
                    lastMessageTimestamp = (doc.get("lastMessageTimestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                )
                newConversationList.add(conversation)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch user details for chat ${doc.id}", e)
                // Optionally, you could add a partial conversation with an error state here.
            }
        }
        // Update the UI with the final, sorted list.
        _uiState.value = ChatListUiState.Success(newConversationList)
    }

    /**
     * Detaches the Firestore listener when the ViewModel is about to be destroyed.
     * This is crucial to prevent memory leaks.
     */
    override fun onCleared() {
        super.onCleared()
        conversationListener?.remove()
        conversationListener = null
    }

    companion object {
        private const val TAG = "ChatListViewModel"
    }
}
