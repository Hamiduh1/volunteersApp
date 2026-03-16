package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventWithVolunteerCount
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.events.EventRepository
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OrganizerActivityViewModel(
    private val eventRepository: EventRepository,
    private val applicationRepository: ApplicationRepository
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(OrganizerSummaryUiState())
    val uiState: StateFlow<OrganizerSummaryUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "OrganizerActivityVM"
    }

    init {
        refreshData()
    }

    private fun fetchOrganizerSummaryData(organizerId: String, isRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !isRefresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val hostedEventsResult = eventRepository.getEventsByOrganizerId(organizerId).first()

                _uiState.update { it.copy(hostedEvents = hostedEventsResult) }

                if (hostedEventsResult is Resource.Success) {
                    val events = hostedEventsResult.data ?: emptyList()

                    val volunteerResult = processEventsWithVolunteers(events)

                    val suggestions = generateSuggestions(events)

                    _uiState.update {
                        it.copy(
                            activeVolunteers = Resource.Success(volunteerResult),
                            suggestions = suggestions,
                            isLoading = false
                        )
                    }
                } else if (hostedEventsResult is Resource.Error) {
                    _uiState.update {
                        it.copy(
                            activeVolunteers = Resource.Error(hostedEventsResult.message ?: "Sync Failed"),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in Dashboard load", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private suspend fun processEventsWithVolunteers(hostedEvents: List<EventModel>): List<EventWithVolunteerCount> {
        val deferredCounts = hostedEvents.map { event ->
            viewModelScope.async {
                try {
                    val appsRes = applicationRepository.getApplicationsForEvent(event.eventId).first()
                    if (appsRes is Resource.Success) {
                        val approvedCount = appsRes.data?.count {
                            it.status == ApplicationStatus.APPROVED
                        } ?: 0
                        EventWithVolunteerCount(event, approvedCount)
                    } else {
                        EventWithVolunteerCount(event, 0)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error counting volunteers for ${event.eventId}", e)
                    EventWithVolunteerCount(event, 0)
                }
            }
        }
        return deferredCounts.awaitAll().filter { it.confirmedVolunteersCount > 0 }
    }

    private fun generateSuggestions(hostedEvents: List<EventModel>): List<String> {
        val suggestions = mutableListOf<String>()
        if (hostedEvents.isEmpty()) {
            suggestions.add("Welcome! Start by creating your first volunteering event.")
        } else {
            suggestions.add("Check the 'Requests' tab to review pending applications.")
            hostedEvents.firstOrNull()?.let {
                suggestions.add("Promoting '${it.title}' can help fill remaining slots.")
            }
        }
        suggestions.add("Keep your profile updated to build trust with volunteers.")
        return suggestions
    }

    fun refreshData() {
        auth.currentUser?.uid?.let { fetchOrganizerSummaryData(it, isRefresh = true) }
    }
}
