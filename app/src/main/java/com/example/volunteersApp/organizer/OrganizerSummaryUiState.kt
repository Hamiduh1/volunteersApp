package com.example.volunteersApp.organizer

import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventWithVolunteerCount
import com.example.volunteersApp.models.Resource

/**
 * Data class representing the UI state for the Organizer Summary screen.
 * Used by OrganizerActivityViewModel to provide a single source of truth for the UI.
 */
data class OrganizerSummaryUiState(
    val isLoading: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val hostedEvents: Resource<List<EventModel>> = Resource.Loading(),
    val activeVolunteers: Resource<List<EventWithVolunteerCount>> = Resource.Loading(),
    val error: String? = null,
    val successMessage: String? = null,
    val generatedResponse: String? = null,
)
