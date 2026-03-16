package com.example.volunteersApp.streams

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Import the LiveSession data class
import com.example.volunteersApp.streams.LiveSession
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class StartStreamUiState(
    val title: String = "",
    val description: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class StartStreamEvent {
    data class Success(val sessionId: String, val shareLink: String) : StartStreamEvent()
}

class StartStreamViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "StartStreamVM"

    private val _uiState = MutableStateFlow(StartStreamUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StartStreamEvent>()
    val events = _events.asSharedFlow()

    fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onDescriptionChange(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    /**
     * New function to allow the UI to reset the error state.
     */
    fun resetError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startStream() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Title cannot be empty") }
            return
        }

        val currentUser = auth.currentUser ?: run {
            _uiState.update { it.copy(error = "User not logged in") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val sessionId = UUID.randomUUID().toString()
                val hostName = currentUser.displayName.takeIf { !it.isNullOrBlank() } ?: "Anonymous Host"

                // FIX: Use a map to explicitly set the server timestamp.
                // This is more reliable than relying on @ServerTimestamp with POJOs, which can have issues.
                val liveSessionData = mapOf(
                    "agoraChannelName" to sessionId,
                    "title" to state.title,
                    "description" to state.description,
                    "hostId" to currentUser.uid,
                    "hostName" to hostName,
                    "hostProfilePicUrl" to currentUser.photoUrl?.toString(),
                    "status" to "live",
                    "startTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(), // Set server-side timestamp
                    "viewerCount" to 0L,
                    "endTime" to null
                )

                db.collection("live_sessions").document(sessionId).set(liveSessionData).await()

                val shareLink = "https://lvcaapp.com/stream/$sessionId"
                _events.emit(StartStreamEvent.Success(sessionId, shareLink))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create live session", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
