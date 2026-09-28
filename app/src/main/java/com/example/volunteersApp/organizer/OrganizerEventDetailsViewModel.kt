package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * Modernized UI State for the Organizer's Event Detail view.
 */
data class EventDetailsUiState(
    val event: EventModel? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isActionLoading: Boolean = false // For buttons like "Close Entries"
)

class OrganizerEventDetailsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val TAG = "OrganizerEventVM"

    private val _uiState = MutableStateFlow(EventDetailsUiState())
    val uiState = _uiState.asStateFlow()

    // SharedFlow for one-time events like showing Toasts
    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    private var eventListener: ListenerRegistration? = null

    /**
     * MODIFIED: Uses a snapshot listener for real-time updates.
     * If an organizer edits the event, the details view updates instantly.
     */
    fun loadEventDetails(eventId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        eventListener?.remove() // Cleanup old listener if exists

        eventListener = db.collection(FirestoreCollection.EVENTS).document(eventId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val event = snapshot.toObject(EventModel::class.java)
                    _uiState.update { it.copy(event = event, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Event not found.") }
                }
            }
    }

    /**
     * FIX: This function now updates the correct `isActive` field in Firestore.
     */
    fun setEventActiveStatus(eventId: String, newActiveState: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                // Correctly targets the 'isActive' field that the UI depends on.
                db.collection(FirestoreCollection.EVENTS).document(eventId)
                    .update("isActive", newActiveState).await()

                val statusMsg = if (newActiveState) "Event is now OPEN" else "Event is now CLOSED"
                _actionResult.emit(Resource.Success(Unit))
                Log.d(TAG, statusMsg)
            } catch (e: Exception) {
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Update failed"))
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    /**
     * CLEANUP: Ensures the Firestore listener is detached when the user leaves the screen.
     */
    override fun onCleared() {
        super.onCleared()
        eventListener?.remove()
        Log.d(TAG, "Event listener detached.")
    }
}
