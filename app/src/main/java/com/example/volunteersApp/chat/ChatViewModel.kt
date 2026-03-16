package com.example.volunteersApp.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

class ChatViewModel(private val chatId: String) : ViewModel() {
    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser
    private var messageListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForMessages()
    }

    private fun listenForMessages() {
        if (messageListener != null) return

        val messagesRef = db.collection("chats").document(chatId).collection("messages")

        messageListener = messagesRef.orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    _uiState.update { it.copy(error = "Failed to load messages.") }
                    return@addSnapshotListener
                }

                val messages = snapshots?.toObjects(ChatMessage::class.java) ?: emptyList()
                _uiState.update { it.copy(messages = messages) }
            }
    }

    fun sendMessage(messageText: String) {
        val trimmedText = messageText.trim()
        if (trimmedText.isBlank() || currentUser == null) {
            return
        }

        val message = ChatMessage(
            messageText = trimmedText,
            senderId = currentUser.uid
        )

        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val newMessageRef = db.collection("chats").document(chatId)
                        .collection("messages").document()
                    batch.set(newMessageRef, message)

                    val chatDocRef = db.collection("chats").document(chatId)
                    batch.update(
                        chatDocRef,
                        "lastMessageText", trimmedText,
                        "lastMessageTimestamp", FieldValue.serverTimestamp()
                    )
                }.await()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageListener?.remove()
    }

    companion object {
        private const val TAG = "ChatViewModel"

        fun provideFactory(chatId: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(chatId) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
