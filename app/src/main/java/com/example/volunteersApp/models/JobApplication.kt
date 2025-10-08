package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

@IgnoreExtraProperties // Good for handling schema evolution
data class JobApplication(
    @DocumentId
    var applicationId: String? = null,      // Unique ID for this application document, auto-populated by Firestore

    var jobId: String? = null,              // ID of the JOB this application is for
    var jobTitle: String? = null,           // Title of the JOB
    var companyName: String? = null,        // Name of the Company posting the job (replaces employerName for clarity)
    var volunteerUid: String? = null,       // UID of the volunteer who applied
    var employerUid: String? = null,        // UID of the EMPLOYER contact (replaces employerId for clarity and consistency)
    var volunteerName: String? = null,      // Snapshot of the volunteer's name at the time of application
    var volunteerEmail: String? = null,     // Optional: Volunteer's email at time of application (denormalized)
    var volunteerProfileImageUrl: String? = null, // Optional: Volunteer's profile image URL (denormalized)


    @ServerTimestamp
    var applicationTimestamp: Timestamp? = null, // When the application was made

    var status: String? = null,             // e.g., "PENDING", "ACCEPTED", "REJECTED", "WITHDRAWN", "SHORTLISTED"
    // Consider using an enum for status if you haven't already:
    // var applicationStatus: ApplicationStatus? = ApplicationStatus.PENDING

    @ServerTimestamp // Or specific timestamp from JobModel if different
    var jobPostedAt: Timestamp? = null,     // Timestamp of when the original JOB was posted

    // Optional: Fields specific to job applications
    var coverLetterLink: String? = null,    // Link to cover letter in Firebase Storage or external
    var resumeLink: String? = null,         // Link to resume in Firebase Storage or external
    var notesFromVolunteer: String? = null, // Any notes the volunteer added with their application
    var notesFromEmployer: String? = null,  // Internal notes by the employer about this application

    var lastUpdatedAt: Timestamp? = null    // Timestamp of when this application record was last updated
) {
    // No-argument constructor for Firestore deserialization
    // Already provided by Kotlin data class if all properties have defaults or are nullable.
    // Explicitly defining it can be good for clarity if you have other constructors.
    constructor() : this(
        applicationId = null,
        jobId = null,
        jobTitle = null,
        companyName = null,
        volunteerUid = null,
        employerUid = null,
        volunteerName = null,
        volunteerEmail = null,
        volunteerProfileImageUrl = null,
        applicationTimestamp = null,
        status = null, // Or ApplicationStatus.PENDING.name
        jobPostedAt = null,
        coverLetterLink = null,
        resumeLink = null,
        notesFromVolunteer = null,
        notesFromEmployer = null,
        lastUpdatedAt = null
    )

    // Example of a more complete constructor if needed for manual creation
    // (though often objects are created with default values and then properties are set)
    constructor(
        jobId: String?, jobTitle: String?, companyName: String?,
        volunteerUid: String?, employerUid: String?, volunteerName: String?,
        volunteerEmail: String?, volunteerProfileImageUrl: String?, status: String?, jobPostedAt: Timestamp?
        // Add other fields as needed
    ) : this(
        applicationId = null, // Typically set by Firestore
        jobId = jobId,
        jobTitle = jobTitle,
        companyName = companyName,
        volunteerUid = volunteerUid,
        employerUid = employerUid,
        volunteerName = volunteerName,
        volunteerEmail = volunteerEmail,
        volunteerProfileImageUrl = volunteerProfileImageUrl,
        applicationTimestamp = null, // Typically set by @ServerTimestamp
        status = status,
        jobPostedAt = jobPostedAt,
        coverLetterLink = null,
        resumeLink = null,
        notesFromVolunteer = null,
        notesFromEmployer = null,
        lastUpdatedAt = null // Typically set on updates
    )


    @Exclude
    fun getFormattedApplicationDate(): String {
        if (applicationTimestamp == null) {
            return "N/A"
        }
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.format(applicationTimestamp!!.toDate()) // Safe call or !! based on nullability expectation
        } catch (e: Exception) {
            // Log.e("JobApplication", "Error formatting date", e)
            "Date Error"
        }
    }

    // equals() and hashCode() are automatically generated by data classes based on primary constructor properties.
    // If you need custom logic (e.g., only comparing by applicationId if not null),
    // you would need to override them. For most cases, the default is fine.

    // toString() is also auto-generated and usually sufficient for debugging.
}

// Optional: Consider an enum for application status if you don't have one globally
// enum class JobApplicationStatus {
//    PENDING,
//    VIEWED,
//    SHORTLISTED,
//    INTERVIEWING,
//    OFFERED,
//    ACCEPTED, // Volunteer accepted offer
//    REJECTED_BY_EMPLOYER,
//    REJECTED_BY_VOLUNTEER, // Volunteer declined offer
//    WITHDRAWN // Volunteer withdrew application
// }
