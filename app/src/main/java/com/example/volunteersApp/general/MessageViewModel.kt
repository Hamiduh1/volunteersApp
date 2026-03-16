package com.example.volunteersApp.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.FriendlyMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MessageViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _messages = MutableStateFlow<List<FriendlyMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    init {
        observeMessages()
    }

    private fun observeMessages() {
        db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val messageList = snapshot?.toObjects(FriendlyMessage::class.java) ?: emptyList()
                _messages.value = messageList
            }
    }

    fun sendMessage(text: String) {
        val user = auth.currentUser ?: return
        if (text.isBlank()) return

        val newMessage = FriendlyMessage(
            text = text,
            name = user.displayName ?: "Anonymous",
            photoUrl = user.photoUrl?.toString(),
            userId = user.uid
            // Timestamp is handled by @ServerTimestamp in the model
        )

        viewModelScope.launch {
            try {
                db.collection("messages").add(newMessage).await()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
