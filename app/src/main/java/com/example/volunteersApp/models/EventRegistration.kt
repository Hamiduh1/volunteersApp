package com.example.volunteersApp.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Modern Data Class representing a volunteer's registration for an event.
 * Optimized for Jetpack Compose with immutable fields and UI-aware properties.
 */
@IgnoreExtraProperties
data class EventRegistration(
    @DocumentId
    val registrationId: String = "",

    // Event Context
    val eventId: String = "",
    val eventTitle: String = "",
    val eventLocation: String = "",
    val eventTimestamp: Timestamp? = null, // Renamed for consistency with EventModel

    // Volunteer Identity
    val volunteerUid: String = "",
    val volunteerName: String = "",
    val volunteerEmail: String = "",

    // Organizer Context
    val organizerUid: String = "",
    val organizerName: String = "",

    // Application State
    val status: String = "PENDING",

    //payment
    val transactionAmount: Double = 0.0,

    // Metadata
    @ServerTimestamp
    val registrationTimestamp: Timestamp? = null,
    val lastUpdatedTimestamp: Timestamp? = null,

    // Submission Details
    val notesFromVolunteer: String? = null,
    val roleAppliedFor: String? = null
) {
    // --- Compose UI Helper Properties ---

    @get:Exclude
    val statusEnum: ApplicationStatus
        get() = ApplicationStatus.fromString(status)

    /**
     * Directly provides the Material 3 color for this registration's status.
     * Usage in Compose: color = registration.statusColor
     */
    @get:Exclude
    val statusColor: Color
        @Composable
        get() = statusEnum.color

    /**
     * Formats the event date for the registration card.
     */
    @get:Exclude
    val formattedEventDate: String
        get() = eventTimestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "Date TBD"

    /**
     * Returns true if the registration is in a state that allows withdrawal.
     */
    fun canWithdraw(): Boolean {
        return statusEnum == ApplicationStatus.PENDING ||
                statusEnum == ApplicationStatus.WAITLISTED
    }
}
