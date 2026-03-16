package com.example.volunteersApp.models

import androidx.compose.runtime.Immutable
import com.example.volunteersApp.models.EventModel

/**
 * A UI-specific model that wraps an Event and its confirmed participant count.
 *
 * @property event The full details of the event.
 * @property confirmedVolunteersCount The number of approved volunteers for this event.
 */
@Immutable // Tells Compose that this object's properties won't change after creation
data class EventWithVolunteerCount(
    val event: EventModel,
    val confirmedVolunteersCount: Int
) {
    /**
     * Helper to determine if the event has reached its registration limit.
     * Useful for showing a "Full" badge in the Summary Screen.
     */
    val isFullyStaffed: Boolean
        get() = confirmedVolunteersCount >= (event.volunteerLimit ?: 0)
}
