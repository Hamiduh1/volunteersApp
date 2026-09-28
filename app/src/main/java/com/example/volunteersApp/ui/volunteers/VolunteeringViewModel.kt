package com.example.volunteersApp.ui.volunteers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup

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
                val snapshot = db.collection(FirestoreCollection.EVENTS)
                    .get()
                    .await()

                // Keep filtering local so older documents without a consistent isActive field still show up.
                fullEventsList = snapshot.toObjects(EventModel::class.java)
                    .filter { it.isActive }
                    .sortedBy { it.eventDateTime }
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
        db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
            .whereEqualTo("volunteerUid", uid)
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
        if (auth.currentUser == null) {
            _uiState.update { it.copy(errorMessage = "Please sign in before applying.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                val result = FunctionsClient.callMap(
                    CallableFunction.APPLY_FOR_EVENT,
                    mapOf("eventId" to event.eventId)
                )
                if (result?.get("success") != true) {
                    throw IllegalStateException("Event signup could not be completed.")
                }
                _uiState.update { it.copy(successMessage = "Application submitted.") }
            } catch (e: Exception) {
                Log.e("VolunteeringViewModel", "Apply failed", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to submit application.") }
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
