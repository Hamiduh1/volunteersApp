package com.example.volunteersApp.ui.volunteers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup
import com.example.volunteersApp.firebase.FirestoreSubcollection

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
    private var primaryListener: ListenerRegistration? = null
    private var legacyListener: ListenerRegistration? = null
    private val primaryDocs = linkedMapOf<String, DocumentSnapshot>()
    private val legacyDocs = linkedMapOf<String, DocumentSnapshot>()

    private fun toApplicant(doc: DocumentSnapshot): EventApplication? {
        return runCatching {
            EventApplication(
                applicationId = doc.id,
                appliedDate = doc.getDate("appliedDate"),
                eventId = doc.getString("eventId")
                    .orEmpty()
                    .ifBlank { doc.reference.parent.parent?.id.orEmpty() },
                volunteerId = doc.getString("volunteerId")
                    .orEmpty()
                    .ifBlank { doc.getString("volunteerUid").orEmpty() }
                    .ifBlank { doc.getString("userId").orEmpty() },
                organizerId = doc.getString("organizerId")
                    .orEmpty()
                    .ifBlank { doc.getString("organizerUid").orEmpty() },
                eventTitle = doc.getString("eventTitle").orEmpty(),
                eventName = doc.getString("eventName")
                    .orEmpty()
                    .ifBlank { doc.getString("eventTitle").orEmpty() },
                volunteerName = doc.getString("volunteerName").orEmpty(),
                volunteerEmail = doc.getString("volunteerEmail").orEmpty(),
                status = ApplicationStatus.fromString(doc.getString("status"))
            )
        }.onFailure { ex ->
            Log.e(TAG, "Failed to parse applicant ${doc.id}", ex)
        }.getOrNull()
    }

    private fun emitMergedApplicants() {
        allApplicants = (primaryDocs.values + legacyDocs.values)
            .associateBy { it.reference.path }
            .values
            .mapNotNull { doc -> toApplicant(doc) }
        applyFilters()
    }

    // mode can be a specific event ID or null for all-applications view
    fun startListening(mode: String?) {
        val organizerId = auth.currentUser?.uid
        if (organizerId == null) {
            _uiState.update { it.copy(error = "User not authenticated.", isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        primaryListener?.remove()
        legacyListener?.remove()
        primaryDocs.clear()
        legacyDocs.clear()

        val primaryQuery: Query = if (mode == null) {
            // Mode is null: Fetch all applications for the organizer across all events
            db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS).whereEqualTo("organizerId", organizerId)
        } else {
            // Mode is a specific eventId: Fetch applications for that single event
            db.collection(FirestoreCollection.EVENTS).document(mode)
                .collection(FirestoreSubcollection.APPLICATIONS)
                .whereEqualTo("organizerId", organizerId)
        }

        val legacyQuery: Query = if (mode == null) {
            db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS).whereEqualTo("organizerUid", organizerId)
        } else {
            db.collection(FirestoreCollection.EVENTS).document(mode)
                .collection(FirestoreSubcollection.APPLICATIONS)
                .whereEqualTo("organizerUid", organizerId)
        }

        primaryListener = primaryQuery.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e(TAG, "Snapshot listen failed", error)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load applicants: ${error.message}") }
                return@addSnapshotListener
            }

            if (snapshots != null) {
                primaryDocs.clear()
                snapshots.documents.forEach { doc -> primaryDocs[doc.reference.path] = doc }
                emitMergedApplicants()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        legacyListener = legacyQuery.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w(TAG, "Legacy organizerUid listen failed; continuing with organizerId query", error)
                return@addSnapshotListener
            }
            legacyDocs.clear()
            snapshots?.documents?.forEach { doc -> legacyDocs[doc.reference.path] = doc }
            emitMergedApplicants()
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
            val matchesStatus = matchesStatusBucket(app.status, currentState.statusFilter)
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

    private fun matchesStatusBucket(
        actual: ApplicationStatus,
        selectedBucket: ApplicationStatus?
    ): Boolean {
        if (selectedBucket == null) return true
        return when (selectedBucket) {
            ApplicationStatus.APPROVED -> actual in setOf(
                ApplicationStatus.APPROVED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.ATTENDED,
                ApplicationStatus.COMPLETED
            )
            ApplicationStatus.REJECTED -> actual in setOf(
                ApplicationStatus.REJECTED,
                ApplicationStatus.REJECTED_BY_EMPLOYER
            )
            else -> actual == selectedBucket
        }
    }

    fun updateStatus(eventId: String, applicationId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            try {
                // This path assumes applications are sub-collections of events. It is correct.
                db.collection(FirestoreCollection.EVENTS).document(eventId)
                    .collection(FirestoreSubcollection.APPLICATIONS).document(applicationId)
                    .update("status", newStatus.name).await()
                // The listener will automatically refresh the list
            } catch (e: Exception) {
                Log.e(TAG, "Update status failed", e)
                _uiState.update { it.copy(error = "Failed to update status.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        primaryListener?.remove()
        legacyListener?.remove()
    }
}
