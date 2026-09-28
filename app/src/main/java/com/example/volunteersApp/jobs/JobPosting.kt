package com.example.volunteersApp.jobs

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Modern Data Class for Job Postings.
 * Refactored for Jetpack Compose architecture and standard Firestore integration.
 */
@IgnoreExtraProperties
data class JobPosting(
    val postingId: String = "",

    val employerUid: String? = null,
    val organizationName: String? = null,

    // Corresponds to eventName in legacy code; standardizing on 'title' for UI consistency
    val title: String? = null,

    val jobTitle: String? = null,
    val description: String? = null,
    val date: String? = null,
    val time: String? = null,
    val locationName: String? = null,
    val category: String? = null,
    val volunteersNeeded: Int = 0,
    val status: String? = "open",
    // Canonical deadline used for discovery and server-side application checks.
    val applicationDeadline: Timestamp? = null,
    val closeEntries: Boolean = false,
    val retainedForHistory: Boolean = false,

    @ServerTimestamp
    val timestamp: Timestamp? = null
)
