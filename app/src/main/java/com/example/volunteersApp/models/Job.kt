package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Modern Data Class representing a Job Opportunity.
 * UPDATED: Uses immutable `val` properties and a type-safe enum for status.
 */
@IgnoreExtraProperties
data class Job(
    @DocumentId
    val jobId: String = "", // Document ID, automatically populated by Firestore

    val title: String = "",
    val title_lowercase: String = "", // For case-insensitive searching

    val employerId: String = "",
    val employerName: String = "",
    val employerLogoUrl: String? = null,

    val description: String = "",
    val responsibilities: List<String> = emptyList(),

    val locationString: String = "",
    val locationIsRemote: Boolean = false,

    val category: String = "", // e.g., "Environmental", "Community"
    val jobType: String = "", // e.g., "Full-time", "Volunteer"

    @ServerTimestamp
    val postedDate: Timestamp? = null,
    val applicationDeadline: Timestamp? = null,

    // UPDATED: Using a type-safe enum for the job's status
    val status: JobStatus = JobStatus.OPEN,

    val requiredSkills: List<String> = emptyList(),
    val preferredSkills: List<String> = emptyList(),

    val salaryOrCompensation: String? = null,
    val duration: String? = null,

    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val applyInstructionsOrUrl: String? = null,

    val totalSlots: Int = 1,
    val slotsFilled: Int = 0,
    val applicantsCount: Int = 0,
    val applicantIds: List<String> = emptyList(),

    @ServerTimestamp
    val lastUpdated: Timestamp? = null
) {
    // Firestore requires a no-argument constructor
    constructor() : this("")
}
