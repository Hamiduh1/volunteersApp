package com.example.volunteersApp.models

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UNIFIED DATA MODEL: EventApplication
 * This is the single source of truth for applications between volunteers and events.
 * It is designed to work seamlessly with Firestore and Jetpack Compose.
 */
@Keep
@IgnoreExtraProperties
data class EventApplication(
    @DocumentId
    val applicationId: String = "",

    @ServerTimestamp
    val appliedDate: Date? = null, // Added missing field
    // Linkage IDs
    val eventId: String = "",
    val volunteerId: String = "", // Unified name for clarity
    val organizerId: String = "", // Unified name for clarity

    // Denormalized data for high-performance UI rendering
    val eventTitle: String = "",
    val eventName: String = "",
    val organizerName: String = "",
    val volunteerName: String = "",
    val volunteerEmail: String = "",
    val volunteerProfileImageUrl: String? = null,
    // FIX: Removed the redundant 'eventName' property. 'eventTitle' is the correct one.

    // Communication and Status
    val notes: String? = null, // Notes provided by the volunteer
    val reasonForRejection: String? = null, // Feedback provided by the organizer
    val organizerNotes: String? = null, // Internal private notes for organizers

    // --- CRITICAL FIX: Single 'status' property with the correct Enum type ---
    // Firestore automatically serializes enums to their string names (e.g., "PENDING").
    // When reading data, Firestore automatically converts the string back to the enum.
    val status: ApplicationStatus = ApplicationStatus.PENDING,


    // Timestamps
    @ServerTimestamp
    val applicationTimestamp: Timestamp? = null,
    @ServerTimestamp
    val lastUpdatedTimestamp: Timestamp? = null,

    // Compound key for preventing duplicate applications in Firestore
    val volunteerId_eventId: String? = null
) {
    // Required empty constructor for Firestore deserialization
    constructor() : this(applicationId = "")

    // --- Compose UI Helper Properties ---
    // These helpers are excluded from Firestore serialization but available in your app's UI code.

    /**
     * Provides the Material 3 color directly to Composables based on the status.
     * Note: This is an example. You should define 'color' on your ApplicationStatus enum.
     */
    @get:Exclude
    val statusColor: Color
        get() = status.color

    /**
     * Provides a user-friendly display name.
     * Note: This is an example. You should define 'displayName' on your ApplicationStatus enum.
     */
    @get:Exclude
    val statusDisplayName: String
        get() = status.displayName


    /**
     * Formats the application date for display in the UI.
     */
    @get:Exclude
    val formattedDate: String
        get() = applicationTimestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "N/A"
}
