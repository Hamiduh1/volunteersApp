package com.example.volunteersApp.ui.volunteers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class VolunteeringUiState(
    val events: List<EventModel> = emptyList(), // Renamed for clarity
    val appliedEventIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val searchQuery: String = "",
    val categoryFilter: String? = null,
    val categories: List<String> = listOf(
        "All", "Technology", "Health", "Education", "Engineering", "Social Services", "Environment", "Animal Welfare"
    )
)

class VolunteeringViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(VolunteeringUiState())
    val uiState: StateFlow<VolunteeringUiState> = _uiState.asStateFlow()

    private var fullEventsList: List<EventModel> = emptyList()

    init {
        fetchEvents()
        fetchUserAppliedEvents()
    }

    fun fetchEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val snapshot = db.collection("events")
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                fullEventsList = snapshot.toObjects(EventModel::class.java).sortedBy { it.eventDateTime }
                applyFilters()
            } catch (e: Exception) {
                Log.e("VolunteeringViewModel", "Error fetching events", e)
                _uiState.update { it.copy(errorMessage = "Failed to load events.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun fetchUserAppliedEvents() {
        val uid = auth.currentUser?.uid ?: return
        db.collectionGroup("applications")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("VolunteeringViewModel", "Error listening to applications", error)
                    _uiState.update { it.copy(errorMessage = "Failed to load application statuses.") }
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.mapNotNull { it.getString("eventId") }?.toSet() ?: emptySet()
                _uiState.update { it.copy(appliedEventIds = ids) }
            }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setCategoryFilter(category: String?) {
        val filter = if (category?.equals("All", true) == true) null else category
        _uiState.update { it.copy(categoryFilter = filter) }
        applyFilters()
    }

    private fun applyFilters() {
        val query = _uiState.value.searchQuery.lowercase()
        val cat = _uiState.value.categoryFilter

        val filtered = fullEventsList.filter { item ->
            val matchesQuery = query.isEmpty() ||
                item.title?.lowercase()?.contains(query) == true ||
                item.organizerName?.lowercase()?.contains(query) == true ||
                item.locationName?.lowercase()?.contains(query) == true

            val matchesCategory = cat == null || item.category == cat

            matchesQuery && matchesCategory
        }
        _uiState.update { it.copy(events = filtered) } // Updated to use 'events'
    }

    fun applyForEvent(event: EventModel) {
        val currentUser = auth.currentUser ?: return
        val uid = currentUser.uid

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                val newApplicationRef = db.collection("events").document(event.eventId)
                    .collection("applications").document()

                val applicationData = hashMapOf(
                    "applicationId" to newApplicationRef.id,
                    "eventId" to event.eventId,
                    "eventTitle" to event.title,
                    "userId" to uid,
                    "organizerId" to event.organizerId,
                    "volunteerName" to (currentUser.displayName ?: "Volunteer"),
                    "volunteerEmail" to currentUser.email,
                    "status" to "pending",
                    "appliedDate" to FieldValue.serverTimestamp()
                )

                newApplicationRef.set(applicationData).await()

                _uiState.update { it.copy(successMessage = "Application Submitted!") }
            } catch (e: Exception) {
                Log.e("VolunteeringViewModel", "Apply failed", e)
                _uiState.update { it.copy(errorMessage = "Failed to submit application.") }
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
