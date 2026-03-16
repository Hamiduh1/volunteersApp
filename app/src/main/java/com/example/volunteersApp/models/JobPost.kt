package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable

/**
 * UNIFIED DATA MODEL: JobPost
 * Refactored to a modern Kotlin data class with improved type safety using enums.
 * Using 'var' for some fields to maintain compatibility with legacy code and Firestore deserialization.
 */
@IgnoreExtraProperties
data class JobPost(
    @DocumentId
    var id: String = "", // Renamed from jobPostId

    var employerId: String? = null, // Renamed from employerUid
    var organizationName: String? = null,
    var title: String? = null, // Consolidated from eventName and jobTitle
    var description: String? = null,
    var date: String? = null, // Note: Consider moving to a single Timestamp
    var time: String? = null, // Note: Consider moving to a single Timestamp
    var eventTimestamp: Long = 0L,
    var location: String? = null,
    var category: String? = null,
    var imageUrl: String? = null,
    var volunteersNeeded: Long = 0L,

    // UPDATED: Using a type-safe enum for status
    var status: JobStatus = JobStatus.OPEN,
    var jobType: String? = null,

    @ServerTimestamp
    var createdAt: Timestamp? = null,
    @ServerTimestamp
    var updatedAt: Timestamp? = null,
    @ServerTimestamp
    var postedDate: Timestamp? = null,

    var skills: String? = null, // Note: Consider making this a List<String>
    var urgency: String? = null,
    var requirements: String? = null,
    var contactInfoProvidedByEmployer: String? = null,
    var hours: String? = null
) : Serializable
