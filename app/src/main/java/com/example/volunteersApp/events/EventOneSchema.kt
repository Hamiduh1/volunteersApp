package com.example.volunteersApp.events

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * REFACTOR NOTES:
 * 1. Data Class Stability: Uses immutable 'val' for Firestore fields.
 * 2. Compose-Friendly: Added helper properties for UI colors and formatted strings.
 * 3. Schema Alignment: Matches the "volunteersRegistered" and "eventTimestamp"
 *    fields used in the ViewModels.
 */
@IgnoreExtraProperties
data class EventModel(
    @DocumentId
    val eventId: String = "",

    // Basic Info
    val title: String = "",
    val description: String = "",
    val locationName: String = "",
    val category: String = "",
    val imageUrl: String? = null,

    // Organizer Info
    val organizerId: String = "",
    val organizerName: String = "",

    // Requirements & Limits
    val skillsRequired: String? = null,
    val payment: Double = 0.0,
    val requiredVolunteers: Long = 0L,
    val volunteerLimit: Int = 0,

    // Tracking Counts
    val participantsCount: Int = 0,
    val volunteersRegistered: Int = 0,

    // State Flags
    val closeEntries: Boolean = false,
    val status: String = "upcoming", // "upcoming", "ongoing", "completed"

    // Timestamps
    val eventTimestamp: Timestamp? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,

    // UI-Only state (Not stored in main event doc, populated in ViewModel)
    @get:Exclude
    var userStatus: String? = null
) {

    // --- Computed Properties for Compose UI ---

    @get:Exclude
    val eventDate: Date?
        get() = eventTimestamp?.toDate()

    @get:Exclude
    val formattedDate: String
        get() = eventDate?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "Date TBD"

    @get:Exclude
    val formattedTime: String
        get() = eventDate?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
        } ?: "Time TBD"

    @get:Exclude
    val volunteersRemaining: Int
        get() = (volunteerLimit - volunteersRegistered).coerceAtLeast(0)

    /**
     * Determines the color of the status chip in Compose screens.
     * Prevents logic duplication in EventCard and EventDetailScreen.
     */
    @get:Exclude
    val statusColor: Color
        get() = when (userStatus?.lowercase()) {
            "approved", "accepted" -> Color(0xFF4CAF50) // Green
            "pending" -> Color(0xFFFF9800) // Orange
            "rejected" -> Color(0xFFF44336) // Red
            else -> when(status.lowercase()) {
                "upcoming" -> Color(0xFF2196F3) // Blue
                "completed" -> Color(0xFF9E9E9E) // Gray
                else -> Color.DarkGray
            }
        }

    /**
     * Logic check for UI state to determine if "Apply" button should be enabled.
     */
    fun isOpenForApplication(): Boolean {
        val now = Timestamp.now()
        val isFull = volunteersRegistered >= volunteerLimit
        val isPast = eventTimestamp != null && eventTimestamp.seconds < now.seconds

        return !closeEntries && !isFull && !isPast
    }
}

/**
 * ARCHITECTURAL COMMENTARY:
 *
 * Why this is useful for your Compose migration:
 *
 * 1. State Stability: Compose uses structural equality to decide if a screen should
 *    re-render. Because this is now a 'data class', Compose can instantly see if
 *    the data returned from Firestore is identical to the previous frame.
 *
 * 2. Property Access: In Compose, we prefer accessing properties directly
 *    (e.g., event.title) rather than Java getters (e.g., event.getTitle()).
 *    This Kotlin class makes your Screen code much cleaner.
 *
 * 3. Boilerplate Reduction: You can delete the old 'EventOneSchema.java'.
 *    This one file handles serialization, deserialization, and UI-helper logic.
 *
 * 4. Interop: If your old fragments still call .getTitle(), Kotlin automatically
 *    generates those synthetic getters so you won't break existing code.
 */
