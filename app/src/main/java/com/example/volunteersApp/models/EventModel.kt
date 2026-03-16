package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern Data Class representing an Opportunity (Event or Job).
 * Refactored to a unified model for Firestore.
 */
data class EventModel(
    @DocumentId
    val eventId: String = "",

    val title: String = "",
    val description: String = "",
    val category: String = "",
    val eventDateTime: Timestamp? = null,
    val duration: String? = null,
    val locationName: String? = null,
    val locationAddress: String? = null,
    val geoPoint: GeoPoint? = null,
    val payment: Double? = null,
    val eventFee: Double = 0.0, // Added field for event fee

    val volunteerLimit: Int? = null,
    val participantsCount: Int = 0,

    val skills: List<String> = emptyList(),
    val imageUrl: String? = null,

    @get:JvmName("isActive")
    val isActive: Boolean = true,

    val closeEntries: Boolean = false,

    val status: OpportunityStatus = OpportunityStatus.UNKNOWN,
    val opportunityType: OpportunityType = OpportunityType.UNKNOWN,

    val organizerId: String? = null,
    val organizerName: String? = null,

    val requirements: String? = null,
    val contactInfo: String? = null,

    @get:Exclude @set:Exclude var userStatus: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastUpdatedAt: Timestamp? = null
) {
    @get:Exclude
    val eventDateObject: Date?
        get() = eventDateTime?.toDate()

    /**
     * UI Helper: Returns a user-friendly date string.
     */
    @get:Exclude
    val formattedDate: String
        get() = eventDateTime?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "Date TBD"

    /**
     * UI Helper: Returns a formatted currency string.
     */
    @get:Exclude
    val formattedPayment: String
        get() = payment?.let { String.format(Locale.US, "$%.2f", it) } ?: "Free"
}
