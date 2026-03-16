package com.example.volunteersApp.employer.ui.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Modern Data Class for Employer Profile information.
 * Refactored for Jetpack Compose architecture and standard Firestore integration.
 */
@IgnoreExtraProperties
data class EmployerProfile(
    @DocumentId
    val documentId: String = "",

    var organizationName: String? = null,
    var contactEmail: String? = null,
    var description: String? = null,
    var location: String? = null,
    var profileImageUrl: String? = null,

    @ServerTimestamp
    val lastUpdatedTimestamp: Timestamp? = null,

    // --- Role Change Request Fields ---
    var interestedInBecomingOrganizer: Boolean = false,
    var interestedInBecomingVolunteer: Boolean = false,
    var roleChangeRequestStatus: String? = null // e.g., "PENDING_ORGANIZER", "PENDING_VOLUNTEER"
) {
    // Required empty constructor for Firestore is generated automatically by Kotlin data class
    // because all properties have default values.

    /**
     * Helper for manual Firestore updates if not using the full POJO.
     */
    @Exclude
    fun toMapForUpdate(): Map<String, Any?> {
        return mapOf(
            "organizationName" to organizationName,
            "contactEmail" to contactEmail,
            "description" to description,
            "location" to location,
            "profileImageUrl" to profileImageUrl
        )
    }
}