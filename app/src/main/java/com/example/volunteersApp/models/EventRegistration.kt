package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a volunteer's registration or application for a specific event.
 * This would typically be stored in a Firestore collection like "event_registrations"
 * or as a subcollection under "events" (e.g., "events/{eventId}/registrations/{registrationId}")
 * and/or under "users" (e.g., "users/{volunteerId}/eventRegistrations/{registrationId_or_eventId}").
 */
data class EventRegistration(
    @DocumentId
    var registrationId: String? = null, // Firestore document ID for this registration

    var eventId: String? = null,
    var eventTitle: String? = null, // Denormalized for quick display
    var eventLocation: String? = null, // Denormalized event location
    var eventDateTime: Timestamp? = null, // Denormalized actual start date and time of the event

    var volunteerUid: String? = null,
    var volunteerName: String? = null, // Denormalized for display
    var volunteerEmail: String? = null, // Denormalized for communication

    var organizerUid: String? = null, // UID of the event organizer
    var organizerName: String? = null, // Denormalized organizer name

    var status: String? = REGISTRATION_STATUS_PENDING, // Default status

    @ServerTimestamp // Automatically set by Firestore on creation
    var registrationTimestamp: Timestamp? = null,
    var lastUpdatedTimestamp: Timestamp? = null, // For tracking updates to the registration

    var notesFromVolunteer: String? = null, // Optional notes from volunteer during registration
    var roleAppliedFor: String? = null // Optional if events have specific roles
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this(
        registrationId = null,
        eventId = null,
        eventTitle = null,
        eventLocation = null,
        eventDateTime = null,
        volunteerUid = null,
        volunteerName = null,
        volunteerEmail = null,
        organizerUid = null,
        organizerName = null,
        status = REGISTRATION_STATUS_PENDING,
        registrationTimestamp = null,
        lastUpdatedTimestamp = null,
        notesFromVolunteer = null,
        roleAppliedFor = null
    )

    companion object {
        // Define common status strings as constants for consistency
        const val REGISTRATION_STATUS_PENDING = "pending"
        const val REGISTRATION_STATUS_CONFIRMED = "confirmed"
        const val REGISTRATION_STATUS_WAITLISTED = "waitlisted"
        const val REGISTRATION_STATUS_REJECTED = "rejected"
        const val REGISTRATION_STATUS_CANCELLED_VOLUNTEER = "cancelled_by_volunteer"
        const val REGISTRATION_STATUS_CANCELLED_ORGANIZER = "cancelled_by_organizer"
        const val REGISTRATION_STATUS_ATTENDED = "attended"
        const val REGISTRATION_STATUS_NO_SHOW = "no_show"
    }
}

