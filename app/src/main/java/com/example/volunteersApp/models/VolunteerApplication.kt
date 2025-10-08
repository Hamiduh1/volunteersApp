
/** This VolunteerApplication model class is primarily for representing
 * the application itself, which is an entity that exists between a volunteer
 * and an event (managed by an organizer).•It is used by BOTH volunteers
 * and organizers.•For Volunteers: It's their application to an event.
 * •For Organizers: It's an application they have received for an event they manage.**/



package com.example.volunteersApp.models // <--- Corrected package based on other files

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp // Import for annotation

// Note: I'm assuming ApplicationStatus enum is in the same file or correctly imported.
// If ApplicationStatus is in this file as per your original snippet,
// ensure its package declaration matches or it's imported correctly elsewhere.

data class VolunteerApplication(
    var applicationId: String = "",
    var eventId: String = "",
    var volunteerId: String = "",
    var volunteerName: String = "",
    var volunteerEmail: String = "",
    var volunteerPhoneNumber: String? = null,
    var volunteerProfileImageUrl: String? = null,
    var eventTitle: String = "",
    var applicationStatus: String = ApplicationStatus.PENDING.name,

    // Renaming 'messageFromVolunteer' to 'notes' to match the fragment
    var notes: String? = null, // <<< CHANGED FROM messageFromVolunteer

    var reasonForRejection: String? = null,
    var organizerNotes: String? = null, // Internal notes by the organizer

    @ServerTimestamp // Recommended for fields populated by the server
    var appliedAt: Timestamp? = null,
    @ServerTimestamp // Recommended for fields populated by the server
    var lastUpdatedAt: Timestamp? = null,

    // Example: if you link applications to organizers directly, not used in detail fragment directly yet
    val organizerId: String? = null
) {
    constructor() : this(
        applicationId = "",
        eventId = "",
        volunteerId = "",
        volunteerName = "",
        volunteerEmail = "",
        volunteerPhoneNumber = null,
        volunteerProfileImageUrl = null,
        eventTitle = "",
        applicationStatus = ApplicationStatus.PENDING.name,
        notes = null, // <<< CHANGED
        reasonForRejection = null,
        organizerNotes = null,
        appliedAt = null,
        lastUpdatedAt = null,
        organizerId = null
    )
}
