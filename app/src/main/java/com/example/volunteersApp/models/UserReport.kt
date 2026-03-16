package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Modernized data class for user reports.
 * Uses immutable properties, consistent naming, and modern Timestamp.
 */
data class UserReport(
    @DocumentId
    val reportId: String = "",

    val reportedUserName: String? = null,
    val reportedUserEmail: String? = null,
    val eventName: String? = null,
    val reasonForReport: String? = null,
    val reportingUserDisplayName: String? = null,
    val reportingUserId: String? = null,

    @ServerTimestamp
    val timestamp: Timestamp? = null
) {
    // No-arg constructor required by Firestore for deserialization
    constructor() : this("")
}
