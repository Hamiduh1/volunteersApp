package com.example.volunteersApp.viewmodels // Typically ViewModels are in the feature package

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
//import com.example.volunteersApp.models.VolunteerApplication
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventWithVolunteerCount // Import the new data class
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.repository.ApplicationRepository
import com.example.volunteersApp.models.EventApplicationStatus // <<< ADD/USE THIS

import com.example.volunteersApp.repository.EventRepository // Assuming you have this
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.collections.filter
import kotlin.collections.isNullOrEmpty

class OrganizerActivityViewModel(
    application: Application, // AndroidViewModel can access EventApplication context if needed
    private val eventRepository: EventRepository,
    private val applicationRepository: ApplicationRepository
) : AndroidViewModel(application) {

    private val _hostedEventsSummary = MutableLiveData<Resource<List<EventModel>>>()
    val hostedEventsSummary: LiveData<Resource<List<EventModel>>> = _hostedEventsSummary

    private val _eventsWithConfirmedVolunteers = MutableLiveData<Resource<List<EventWithVolunteerCount>>>()
    val eventsWithConfirmedVolunteers: LiveData<Resource<List<EventWithVolunteerCount>>> = _eventsWithConfirmedVolunteers

    private val _suggestions = MutableLiveData<List<String>>()
    val suggestions: LiveData<List<String>> = _suggestions

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    companion object {
        private const val TAG = "OrganizerActivityVM"
    }

    fun fetchOrganizerSummaryData(organizerId: String, isRefresh: Boolean = false) {
        if (_isLoading.value == true && !isRefresh) {
            Log.d(TAG, "Already loading summary data.")
            return
        }
        Log.d(TAG, "Fetching organizer summary data for $organizerId. Refresh: $isRefresh")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // --- 1. Fetch Hosted Events ---
                _hostedEventsSummary.postValue(Resource.Loading())
                // Assuming eventRepository.getEventsByOrganizerId returns a Flow
                // Use .first() if you only need the initial load for summary, or handle ongoing flow
                val hostedEventsResult = eventRepository.getEventsByOrganizerId(organizerId).first()
                if (hostedEventsResult is Resource.Success) {
                    _hostedEventsSummary.postValue(Resource.Success(hostedEventsResult.data ?: emptyList()))
                    Log.d(TAG, "Hosted events fetched: ${hostedEventsResult.data?.size ?: 0}")

                    // --- 2. Fetch Events with Confirmed Volunteers (based on hosted events) ---
                    if (!hostedEventsResult.data.isNullOrEmpty()) {
                        processEventsWithVolunteers(hostedEventsResult.data)
                    } else {
                        _eventsWithConfirmedVolunteers.postValue(Resource.Success(emptyList()))
                    }
                } else if (hostedEventsResult is Resource.Error) {
                    _hostedEventsSummary.postValue(Resource.Error(hostedEventsResult.message ?: "Failed to load hosted events"))
                    _eventsWithConfirmedVolunteers.postValue(Resource.Error(hostedEventsResult.message ?: "Cannot process volunteers without events"))
                    Log.e(TAG, "Error fetching hosted events: ${hostedEventsResult.message}")
                }


                // --- 3. Generate Suggestions ---
                generateSuggestions(hostedEventsResult.data ?: emptyList())

            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching organizer summary data", e)
                _hostedEventsSummary.postValue(Resource.Error("Error: ${e.message}"))
                _eventsWithConfirmedVolunteers.postValue(Resource.Error("Error: ${e.message}"))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun processEventsWithVolunteers(hostedEvents: List<EventModel>) {
        _eventsWithConfirmedVolunteers.postValue(Resource.Loading())
        val eventsWithCounts = mutableListOf<EventWithVolunteerCount>()

        // Use async for concurrent fetching of application counts if many events
        // Note: Firestore has limits on concurrent operations, so be mindful for very large lists.
        // For a small number of events, sequential might be fine.
        val deferredCounts = hostedEvents.map { event ->
            viewModelScope.async {
                try {
                    // Assuming applicationRepository.getApplicationsForEvent returns a Flow
                    val applicationsResource = applicationRepository.getApplicationsForEvent(event.eventId).first()
                    if (applicationsResource is Resource.Success) {
                        // Then in processEventsWithVolunteers:
                        val approvedCount = applicationsResource.data?.count { application ->
                            application.status == EventApplicationStatus.APPROVED.name // <<< Use the consistent enum
                        } ?: 0
                        Log.d(TAG, "Event ${event.eventId} has $approvedCount approved volunteers.")
                        EventWithVolunteerCount(event, approvedCount)
                    } else {
                        Log.w(TAG, "Failed to get applications for event ${event.eventId} to count volunteers.")
                        EventWithVolunteerCount(event, 0) // Default to 0 if apps couldn't be fetched
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching applications for event ${event.eventId} count", e)
                    EventWithVolunteerCount(event, 0) // Default on error
                }
            }
        }

        eventsWithCounts.addAll(deferredCounts.awaitAll().filter { it.confirmedVolunteersCount > 0 })
        _eventsWithConfirmedVolunteers.postValue(Resource.Success(eventsWithCounts))
        Log.d(TAG, "Events with confirmed volunteers: ${eventsWithCounts.size}")
    }


    private fun generateSuggestions(hostedEvents: List<EventModel>) {
        val currentSuggestions = mutableListOf<String>()
        // Simple suggestion logic
        if (hostedEvents.isEmpty()) {
            currentSuggestions.add("Start by creating your first event to engage volunteers!")
        } else {
            currentSuggestions.add("Review recent applications for your events.")
            hostedEvents.firstOrNull()?.let {
                currentSuggestions.add("Consider promoting '${it.title}' to attract more volunteers.")
            }
        }
        currentSuggestions.add("Check your event calendar for any upcoming gaps.")
        currentSuggestions.add("Explore new event categories based on community interest.")

        _suggestions.postValue(currentSuggestions)
        Log.d(TAG, "Suggestions generated: ${currentSuggestions.size}")
    }

    fun refreshData() {
        val organizerId = FirebaseAuth.getInstance().currentUser?.uid
        if (organizerId != null) {
            fetchOrganizerSummaryData(organizerId, true)
        } else {
            Log.w(TAG, "Cannot refresh data, organizerId is null.")
            _isLoading.postValue(false) // Ensure loading state is reset
        }
    }
}

