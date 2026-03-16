package com.example.volunteersApp.ui.volunteers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication // This import will be replaced by the local definition
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// FIX: Define or update your EventApplication data class to match the Firestore document structure.
// If this class exists in `com.example.volunteersApp.models`, update it there.


data class ViewApplicantsUiState(
    val applicants: List<EventApplication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val statusFilter: ApplicationStatus? = null
)

class ViewApplicantsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "ViewApplicantsVM"

    private val _uiState = MutableStateFlow(ViewApplicantsUiState())
    val uiState = _uiState.asStateFlow()

    private var allApplicants: List<EventApplication> = emptyList()
    private var listenerRegistration: ListenerRegistration? = null

    // mode can be a specific event ID or null for all-applications view
    fun startListening(mode: String?) {
        val organizerId = auth.currentUser?.uid
        if (organizerId == null) {
            _uiState.update { it.copy(error = "User not authenticated.", isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        listenerRegistration?.remove() // Stop any previous listener

        val query: Query = if (mode == null) {
            // Mode is null: Fetch all applications for the organizer across all events
            db.collectionGroup("applications").whereEqualTo("organizerId", organizerId)
        } else {
            // Mode is a specific eventId: Fetch applications for that single event
            db.collection("events").document(mode).collection("applications")
        }

        listenerRegistration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e(TAG, "Snapshot listen failed", error)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load applicants: ${error.message}") }
                return@addSnapshotListener
            }

            if (snapshots != null) {
                allApplicants = snapshots.toObjects(EventApplication::class.java)
                applyFilters()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onStatusFilterChanged(status: ApplicationStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        applyFilters()
    }

    private fun applyFilters() {
        val currentState = _uiState.value
        val filteredList = allApplicants.filter { app ->
            // FIX: Now references to eventName will resolve correctly
            val matchesSearch = app.volunteerName.contains(currentState.searchQuery, ignoreCase = true) ||
                    app.eventName.contains(currentState.searchQuery, ignoreCase = true)
            val matchesStatus = currentState.statusFilter == null || app.status == currentState.statusFilter
            matchesSearch && matchesStatus
        }

        _uiState.update {
            it.copy(
                // FIX: The sortedByDescending call will also resolve correctly now
                applicants = filteredList.sortedByDescending { applicant -> applicant.appliedDate },
                isLoading = false
            )
        }
    }

    fun updateStatus(eventId: String, applicationId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            try {
                // This path assumes applications are sub-collections of events. It is correct.
                db.collection("events").document(eventId)
                    .collection("applications").document(applicationId)
                    .update("status", newStatus).await()
                // The listener will automatically refresh the list
            } catch (e: Exception) {
                Log.e(TAG, "Update status failed", e)
                _uiState.update { it.copy(error = "Failed to update status.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove() // Clean up the listener
    }
}
