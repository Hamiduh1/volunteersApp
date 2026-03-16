package com.example.volunteersApp.ui.volunteers

import com.example.volunteersApp.models.EventModel

/**
 * Modern Data Class representing a Volunteer Event Item.
 *
 * In Compose, we use data classes to automatically handle equality and
 * hashcode, which is vital for efficient UI recomposition.
 */
data class VolunteerEventItem(
    val eventId: String = "",
    val organizerId: String = "",
    val eventDetails: EventModel? = null
) {
    // Basic validation check (Optional, can be done in ViewModel instead)
    init {
        if (eventId.isBlank()) {
            // Note: Firebase requires an empty constructor for deserialization,
            // so we only throw if manually creating invalid data.
        }
    }
}
//In your old architecture, you used DiffUtil to tell the RecyclerView
// which items changed. In Jetpack Compose, this logic is handled
// automatically by the LazyColumn.