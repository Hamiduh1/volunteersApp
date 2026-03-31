package com.example.volunteersApp.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.jobs.JobDetailViewModel.ApplicationStatus
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class EventDetailUiState(
    val event: EventModel? = null,
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val error: String? = null,
    val isOrganizer: Boolean = false,
    val applicationStatus: ApplicationStatus = ApplicationStatus.UNKNOWN,
    val walletBalance: Double = 0.0
)

class EventDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "EventDetailViewModel"

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    fun loadEventDetails(eventId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.update { it.copy(error = "User not authenticated.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val eventDocDeferred = db.collection("events").document(eventId).get()
                val userDocDeferred = db.collection("users").document(userId).get()
                val applicationDocDeferred = db.collection("events").document(eventId)
                    .collection("applications").document(userId).get()

                val eventDoc = eventDocDeferred.await()
                val userDoc = userDocDeferred.await()
                val applicationDoc = applicationDocDeferred.await()

                val event = eventDoc.toObject(EventModel::class.java)
                if (event == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Event not found.") }
                    return@launch
                }

                val isOrganizer = event.organizerId == userId
                val wallet = userDoc.get("wallet") as? Map<*, *>
                val balance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0

                // FIX: Corrected the logic to determine the status.
                // The `isOrganizer` flag is handled separately by the UI.
                val status = if (applicationDoc.exists()) {
                    when (applicationDoc.getString("status")?.lowercase()) {
                        "pending" -> ApplicationStatus.APPLIED_PENDING
                        "approved", "accepted" -> ApplicationStatus.APPROVED
                        "rejected" -> ApplicationStatus.REJECTED
                        else -> ApplicationStatus.UNKNOWN
                    }
                } else if (event.closeEntries) {
                    ApplicationStatus.JOB_CLOSED // Using JOB_CLOSED as it represents the same UI state
                } else {
                    ApplicationStatus.CAN_APPLY
                }

                _uiState.update {
                    it.copy(
                        event = event,
                        walletBalance = balance,
                        isOrganizer = isOrganizer, // This is what the UI will use to show/hide the management bar
                        applicationStatus = status,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching event details", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun applyForEvent(eventId: String) {
        if (auth.currentUser == null) return
        val currentState = _uiState.value
        val event = currentState.event ?: return

        if (event.eventFee > 0 && currentState.walletBalance < event.eventFee) {
            viewModelScope.launch { _actionResult.emit(Resource.Error("Insufficient wallet balance.")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                val result = FunctionsClient.callMap(
                    "applyForEvent",
                    mapOf("eventId" to eventId)
                )
                val success = result?.get("success") as? Boolean ?: false
                if (!success) {
                    throw IllegalStateException("Event signup failed. Please try again.")
                }

                _actionResult.emit(Resource.Success(Unit))
                loadEventDetails(eventId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply for event", e)
                _actionResult.emit(Resource.Error("Failed to apply: ${e.localizedMessage}"))
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun updateEntryStatus(eventId: String, closeEntries: Boolean) {
        if (!_uiState.value.isOrganizer) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                db.collection("events").document(eventId)
                    .update("closeEntries", closeEntries)
                    .await()

                _uiState.update {
                    it.copy(event = it.event?.copy(closeEntries = closeEntries))
                }
                _actionResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update entry status", e)
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Update failed."))
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }
}
