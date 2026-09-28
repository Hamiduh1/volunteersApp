package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.models.EventModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

data class HostedEventsUiState(
    val events: List<EventModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class HostedEventsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage

    private val _uiState = MutableStateFlow(HostedEventsUiState())
    val uiState = _uiState.asStateFlow()

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        fetchHostedEvents()
    }

    fun fetchHostedEvents() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true, error = null, message = null) }

        listenerRegistration?.remove()
        listenerRegistration = db.collection(FirestoreCollection.EVENTS)
            .whereEqualTo("organizerId", userId)
            .orderBy("eventDateTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                    return@addSnapshotListener
                }

                val events = snapshots?.toObjects<EventModel>() ?: emptyList()
                _uiState.update { it.copy(events = events, isLoading = false) }
            }
    }

    fun publishEvent(eventId: String) = viewModelScope.launch {
        try {
            db.collection(FirestoreCollection.EVENTS).document(eventId)
                .update(mapOf(
                    "isActive" to true,
                    "status" to "PUBLISHED"
                )).await()
        } catch (e: Exception) {
            Log.e("HostedEventsViewModel", "Error publishing event", e)
            _uiState.update { it.copy(error = "Failed to publish event.") }
        }
    }

    fun unpublishEvent(eventId: String) = viewModelScope.launch {
        try {
            db.collection(FirestoreCollection.EVENTS).document(eventId)
                .update(mapOf(
                    "isActive" to false,
                    "status" to "DRAFT"
                )).await()
        } catch (e: Exception) {
            Log.e("HostedEventsViewModel", "Error unpublishing event", e)
            _uiState.update { it.copy(error = "Failed to unpublish event.") }
        }
    }

    suspend fun deleteEvent(event: EventModel): Boolean {
        auth.currentUser?.uid ?: return false
        _uiState.update { it.copy(isLoading = true, error = null, message = null) } // Show loading state

        return try {
            val result = FunctionsClient.callMap(
                CallableFunction.DELETE_ORGANIZER_EVENT,
                mapOf("eventId" to event.eventId)
            ) ?: error("The event service returned no result.")
            if (result["success"] != true) {
                error((result["message"] as? String) ?: "Could not remove this event.")
            }

            val outcome = result["outcome"] as? String
            val imageUrl = (result["imageUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?: event.imageUrl.takeIf { outcome == "DELETED" }
            imageUrl?.let {
                try {
                    storage.getReferenceFromUrl(it).delete().await()
                } catch (e: Exception) {
                    Log.w("HostedEventsViewModel", "Failed to delete event image: $it", e)
                }
            }
            val message = result["message"] as? String
            _uiState.update { it.copy(isLoading = false, message = message) }
            true
        } catch (e: Exception) {
            Log.e("HostedEventsViewModel", "Delete failed for event ${event.eventId}", e)
            _uiState.update { it.copy(isLoading = false, error = "Failed to delete event: ${e.localizedMessage}") }
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
