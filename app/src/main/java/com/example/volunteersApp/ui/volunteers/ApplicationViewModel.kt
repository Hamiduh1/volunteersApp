package com.example.volunteersApp.ui.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

data class OrganizerApplicationsUiState(
    val applications: List<EventApplication> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
)

class ApplicationViewModel(private val applicationRepository: ApplicationRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizerApplicationsUiState())
    val uiState = _uiState.asStateFlow()

    private val statusFilter = MutableStateFlow<ApplicationStatus?>(null)
    private var allApplications: List<EventApplication> = emptyList()
    private var listenJob: Job? = null

    fun startListening(eventId: String) {
        val normalizedEventId = eventId.trim()
        if (normalizedEventId.isBlank()) {
            startListeningForManagedEvents()
            return
        }

        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            applicationRepository.getApplicationsForEvent(normalizedEventId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = resource.message ?: "Failed to load applications."
                            )
                        }
                    }
                    is Resource.Success -> {
                        allApplications = resource.data.orEmpty()
                        applyFilters()
                    }
                }
            }
        }
    }

    /** Reads only subcollections beneath events owned by the signed-in organizer. */
    fun startListeningForManagedEvents() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            val organizerUid = FirebaseAuth.getInstance().currentUser?.uid
            if (organizerUid.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Sign in to view managed applications.",
                        applications = emptyList()
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                loadManagedApplicationsWithoutCollectionGroup(organizerUid)
            }.onSuccess { apps ->
                allApplications = apps
                applyFilters()
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Could not load your managed applications. Please try again."
                    )
                }
            }
        }
    }

    private suspend fun loadManagedApplicationsWithoutCollectionGroup(organizerUid: String): List<EventApplication> {
        val db = FirebaseFirestore.getInstance()
        val eventDocs = db.collection(FirestoreCollection.EVENTS)
            .whereEqualTo("organizerId", organizerUid)
            .get()
            .await()
            .documents

        val result = mutableListOf<EventApplication>()
        for (eventDoc in eventDocs) {
            val eventId = eventDoc.id
            val appsByOrganizerId = db.collection(FirestoreCollection.EVENTS)
                .document(eventId)
                .collection(FirestoreSubcollection.APPLICATIONS)
                .whereEqualTo("organizerId", organizerUid)
                .get()
                .await()
                .documents

            val appsByOrganizerUid = db.collection(FirestoreCollection.EVENTS)
                .document(eventId)
                .collection(FirestoreSubcollection.APPLICATIONS)
                .whereEqualTo("organizerUid", organizerUid)
                .get()
                .await()
                .documents

            val mergedApps = (appsByOrganizerId + appsByOrganizerUid)
                .associateBy { it.reference.path }
                .values

            mergedApps.forEach { doc ->
                result += EventApplication(
                    applicationId = doc.id,
                    appliedDate = doc.getDate("appliedDate"),
                    eventId = doc.getString("eventId").orEmpty().ifBlank { eventId },
                    volunteerId = doc.getString("volunteerId")
                        .orEmpty()
                        .ifBlank { doc.getString("volunteerUid").orEmpty() }
                        .ifBlank { doc.getString("userId").orEmpty() },
                    organizerId = organizerUid,
                    eventTitle = doc.getString("eventTitle").orEmpty(),
                    eventName = doc.getString("eventName")
                        .orEmpty()
                        .ifBlank { doc.getString("eventTitle").orEmpty() },
                    volunteerName = doc.getString("volunteerName").orEmpty(),
                    volunteerEmail = doc.getString("volunteerEmail").orEmpty(),
                    status = ApplicationStatus.fromString(doc.getString("status")),
                    applicationTimestamp = doc.getTimestamp("applicationTimestamp")
                        ?: doc.getTimestamp("appliedDate")
                )
            }
        }

        return result.sortedByDescending { app ->
            app.appliedDate ?: app.applicationTimestamp?.toDate()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onStatusFilterChanged(status: ApplicationStatus?) {
        statusFilter.value = status
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val search = state.searchQuery.trim().lowercase(Locale.US)
        val status = statusFilter.value

        val filteredList = allApplications.filter { application ->
            val statusMatches = matchesStatusBucket(application.status, status)
            val searchMatches = search.isBlank() ||
                application.volunteerName.lowercase(Locale.US).contains(search) ||
                application.eventTitle.lowercase(Locale.US).contains(search) ||
                application.eventName.lowercase(Locale.US).contains(search)
            statusMatches && searchMatches
        }.sortedByDescending { app ->
            app.appliedDate ?: app.applicationTimestamp?.toDate()
        }

        _uiState.update {
            it.copy(
                applications = filteredList,
                isLoading = false,
                error = null
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

    fun updateStatus(applicationId: String, eventId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            try {
                when (val result = applicationRepository.updateApplicationStatus(applicationId, eventId, newStatus)) {
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.message ?: "Could not update the application status. Please try again."
                        )
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not update the application status. Please try again.")
            }
        }
    }
}

