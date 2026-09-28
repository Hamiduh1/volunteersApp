package com.example.volunteersApp.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.OpportunityStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * Modern State container for the Events Screen.
 */
data class EventUiState(
    val events: List<EventModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categoryFilter: String? = null,
    val searchQuery: String = ""
)

class EventViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val eventsRef = db.collection(FirestoreCollection.EVENTS)

    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "EventViewModel initialized.")
        fetchEvents() // Initial load
    }

    /**
     * Updates category and triggers a re-fetch.
     */
    fun setCategoryFilter(category: String?) {
        val cleanCategory = if (category.isNullOrBlank() || category == "All") null else category
        _uiState.update { it.copy(categoryFilter = cleanCategory) }
        fetchEvents()
    }

    /**
     * Updates search query and triggers a re-fetch.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        fetchEvents()
    }

    /**
     * Primary data fetching logic using Coroutines and Await.
     */
    fun fetchEvents(organizerId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val state = _uiState.value
                var query: Query = eventsRef

                if (!organizerId.isNullOrEmpty()) {
                    query = query.whereEqualTo(FIELD_ORGANIZER_ID, organizerId)
                        .orderBy(FIELD_EVENT_DATE_TIME, Query.Direction.DESCENDING)
                } else {
                    query = query.whereEqualTo(FIELD_STATUS, OpportunityStatus.OPEN.name)
                        .whereGreaterThan(FIELD_EVENT_DATE_TIME, Timestamp(Date()))

                    state.categoryFilter?.let {
                        query = query.whereEqualTo(FIELD_CATEGORY, it)
                    }

                    if (state.searchQuery.isNotBlank()) {
                        val search = state.searchQuery.lowercase()
                        query = query.orderBy(FIELD_TITLE_LOWERCASE)
                            .whereGreaterThanOrEqualTo(FIELD_TITLE_LOWERCASE, search)
                            .whereLessThanOrEqualTo(FIELD_TITLE_LOWERCASE, search + "\uf8ff")
                    } else {
                        query = query.orderBy(FIELD_EVENT_DATE_TIME, Query.Direction.ASCENDING)
                    }
                }

                val snapshot = query.limit(30).get().await()
                val fetchedList = snapshot.toObjects<EventModel>()

                _uiState.update {
                    it.copy(
                        events = fetchedList,
                        isLoading = false,
                        errorMessage = if (fetchedList.isEmpty()) "No events found." else null
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching events", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load events. Please try again."
                    )
                }
            }
        }
    }

    fun refreshEvents() = fetchEvents()

    companion object {
        private const val TAG = "EventViewModel"
        private const val FIELD_STATUS = "status"
        private const val FIELD_EVENT_DATE_TIME = "eventDateTime"
        private const val FIELD_CATEGORY = "category"
        private const val FIELD_TITLE_LOWERCASE = "titleLowercase"
        private const val FIELD_ORGANIZER_ID = "organizerId"
    }
}
