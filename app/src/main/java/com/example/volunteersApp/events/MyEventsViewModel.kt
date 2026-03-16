package com.example.volunteersApp.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventRegistration
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// This class holds the full event details and the user's application status for that event
data class MyEventItem(
    val event: EventModel,
    val registration: EventRegistration
)

data class MyEventsUiState(
    val events: List<MyEventItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: MyEventsViewModel.EventFilter = MyEventsViewModel.EventFilter.APPLIED
)

class MyEventsViewModel : ViewModel() {

    enum class EventFilter { APPLIED, UPCOMING, PAST }

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "MyEventsViewModel"

    private val _uiState = MutableStateFlow(MyEventsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchMyEventsData()
    }

    fun setFilter(filter: EventFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        fetchMyEventsData()
    }

    private fun applyLocalFilter(items: List<MyEventItem>): List<MyEventItem> {
        val now = Date()
        val filtered = items.filter { item ->
            val eventDate = item.event.eventDateObject
            val isUpcoming = eventDate?.after(now) ?: false

            when (_uiState.value.filter) {
                EventFilter.APPLIED -> isUpcoming
                EventFilter.UPCOMING -> isUpcoming
                EventFilter.PAST -> !isUpcoming
            }
        }
        return filtered.sortedBy { it.event.eventDateObject }
    }

    fun fetchMyEventsData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, events = emptyList()) }
            try {
                var query: Query = db.collectionGroup("applications")
                    .whereEqualTo("userId", userId)

                when (_uiState.value.filter) {
                    EventFilter.APPLIED -> {
                        query = query.whereEqualTo("status", "pending")
                    }
                    EventFilter.UPCOMING, EventFilter.PAST -> {
                        query = query.whereEqualTo("status", "approved")
                    }
                }

                val appsSnapshot = query.get().await()

                val eventItems = appsSnapshot.documents.mapNotNull { appDoc ->
                    val registration = appDoc.toObject(EventRegistration::class.java)
                    if (registration != null) {
                        try {
                            val eventDoc = db.collection("events").document(registration.eventId).get().await()
                            val event = eventDoc.toObject(EventModel::class.java)
                            if (event != null) {
                                MyEventItem(event, registration)
                            } else { null }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch event ${registration.eventId}", e)
                            null
                        }
                    } else { null }
                }

                val filteredEvents = applyLocalFilter(eventItems)
                _uiState.update { it.copy(events = filteredEvents) }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching my events", e)
                _uiState.update { it.copy(error = "Failed to load your events.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun withdrawFromEvent(eventId: String, registrationId: String) {
        viewModelScope.launch {
            try {
                db.collection("events").document(eventId)
                    .collection("applications").document(registrationId)
                    .delete()
                    .await()
                // Re-fetch the data for the current filter
                fetchMyEventsData()
            } catch (e: Exception) {
                Log.e(TAG, "Withdrawal failed", e)
                _uiState.update { it.copy(error = "Withdrawal failed. Please try again.") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(error = null) }
    }
}
