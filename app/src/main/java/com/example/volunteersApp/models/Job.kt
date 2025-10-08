
package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

// Using @DocumentId is a cleaner way to automatically map the document ID.
import com.google.firebase.firestore.DocumentId

data class Job(
    @DocumentId
    var jobId: String = "", // Document ID, automatically populated by Firestore

    var title: String = "",
    var title_lowercase: String = "", // For case-insensitive searching

    var employerId: String = "", // UID of the employer who posted
    var employerName: String = "", // Denormalized for easier display
    var employerLogoUrl: String? = null,

    var description: String = "",
    var responsibilities: List<String> = emptyList(),

    var locationString: String = "",
    var locationIsRemote: Boolean = false,

    var category: String = "", // e.g., "Environmental", "Community", "Tech"
    var jobType: String = "", // e.g., "Full-time", "Part-time"

    @ServerTimestamp
    var postedDate: Timestamp? = null,
    var applicationDeadline: Timestamp? = null,

    var status: String = "open", // Overall status: "open", "closed"

    var requiredSkills: List<String> = emptyList(),

    var salaryOrCompensation: String? = null,
    var duration: String? = null,

    var contactEmail: String? = null,
    var applyInstructionsOrUrl: String? = null,

    var applicantsCount: Int = 0, // Renamed from applicationCount for clarity

    @ServerTimestamp
    var lastUpdated: Timestamp? = null,

    // THE ONLY ADDITION: A transient field to hold the user's status for this specific job.
    // @get:Exclude tells Firestore to ignore this field when reading/writing.
    // This field is populated manually in the MyJobsViewModel.
    @get:Exclude @set:Exclude
    var userStatus: String? = null

) {
    // A no-argument constructor is required by Firestore for deserialization.
    // It's automatically generated for data classes if all properties have default values.
}





/**
The @get:Exclude and @set:Exclude annotations are crucial.
They tell Firestore to completely ignore this field. It will not be saved
to your database documents, and Firestore won't look for it when reading
documents.•This field acts as a temporary container that exists only while
the Job object is in your app's memory. It's populated by the MyJobsViewModel
after fetching the job and the user's application status separately.The old
var id: String has been replaced with @DocumentId var jobId: String.•
The @DocumentId annotation is a cleaner, more modern way to tell Firestore
to automatically populate this field with the document's ID when data is
read. This removes the need for manual mapping or a toMap() function.

package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude // To exclude fields from being written to Firestore if needed
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint // For more precise location (optional)
import com.google.firebase.firestore.ServerTimestamp

data class Job(
    // Use @get:Exclude for id if you want Firestore to auto-generate it
    // and you only set it client-side after fetching.
    // However, it's often useful to store the ID within the document for easy reference.
    var id: String = "", // Document ID, make it var if you set it after creation

    var title: String = "",
    var title_lowercase: String = "", // For case-insensitive searching in Firestore (populate this when creating/updating)

    var employerId: String = "", // UID of the employer who posted
    var employerName: String = "", // Denormalized for easier display
    var employerLogoUrl: String? = null, // Optional URL for employer's logo

    var description: String = "",
    var responsibilities: List<String> = emptyList(), // More structured than just description

    var locationString: String = "", // User-friendly location string (e.g., "New York, NY")
    var locationGeoPoint: GeoPoint? = null, // For map integration and geo-queries (optional)
    var locationIsRemote: Boolean = false,

    var category: String = "", // e.g., "Environmental", "Community", "Tech"
    var jobType: String = "", // e.g., "Full-time", "Part-time", "Internship", "Volunteer"

    @ServerTimestamp
    var postedDate: Timestamp? = null,
    var applicationDeadline: Timestamp? = null, // Optional deadline

    var status: String = "open", // "open", "closed", "filled", "expired"

    var requiredSkills: List<String> = emptyList(),
    var preferredSkills: List<String> = emptyList(), // Optional

    var salaryOrCompensation: String? = null, // e.g., "$50,000/year", "Volunteer position", "$20/hour"
    var duration: String? = null, // e.g., "3 months", "Ongoing"

    var contactEmail: String? = null,
    var contactPhone: String? = null,
    var applyInstructionsOrUrl: String? = null, // Instructions or a direct URL to apply

    var totalSlots: Int = 1, // Total available positions for this job
    var slotsFilled: Int = 0, // How many positions have been filled

    // For tracking applicants (choose one or a combination based on your query needs):
    // 1. Simple list of UIDs who applied (good for "array-contains" query)
    var applicantIds: List<String> = emptyList(),

    // 2. Map of applicant UID to their application status directly on the job
    // This can be useful for direct lookups but can make the job document large
    // and querying for "all jobs where my status is pending" is harder.
    // var applicantStatusMap: Map<String, String> = emptyMap(), // e.g., Map<UID, "pending"|"accepted"|"rejected">

    // 3. Number of applications (simple counter, less detail)
    var applicationCount: Int = 0,

    @ServerTimestamp
    var lastUpdated: Timestamp? = null // To know when the job was last modified
) {
    // No-argument constructor is required by Firestore for deserialization
    constructor() : this(
        id = "",
        title = "",
        title_lowercase = "",
        employerId = "",
        employerName = "",
        employerLogoUrl = null,
        description = "",
        responsibilities = emptyList(),
        locationString = "",
        locationGeoPoint = null,
        locationIsRemote = false,
        category = "",
        jobType = "",
        postedDate = null,
        applicationDeadline = null,
        status = "open",
        requiredSkills = emptyList(),
        preferredSkills = emptyList(),
        salaryOrCompensation = null,
        duration = null,
        contactEmail = null,
        contactPhone = null,
        applyInstructionsOrUrl = null,
        totalSlots = 1,
        slotsFilled = 0,
        applicantIds = emptyList(),
        applicationCount = 0,
        lastUpdated = null
    )

    // Helper to prepare data for Firestore, especially for title_lowercase
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title,
            "title_lowercase" to title.lowercase(), // Ensure this is set
            "employerId" to employerId,
            "employerName" to employerName,
            "employerLogoUrl" to employerLogoUrl,
            "description" to description,
            "responsibilities" to responsibilities,
            "locationString" to locationString,
            "locationGeoPoint" to locationGeoPoint,
            "locationIsRemote" to locationIsRemote,
            "category" to category,
            "jobType" to jobType,
            "postedDate" to postedDate, // Firestore handles @ServerTimestamp
            "applicationDeadline" to applicationDeadline,
            "status" to status,
            "requiredSkills" to requiredSkills,
            "preferredSkills" to preferredSkills,
            "salaryOrCompensation" to salaryOrCompensation,
            "duration" to duration,
            "contactEmail" to contactEmail,
            "contactPhone" to contactPhone,
            "applyInstructionsOrUrl" to applyInstructionsOrUrl,
            "totalSlots" to totalSlots,
            "slotsFilled" to slotsFilled,
            "applicantIds" to applicantIds,
            "applicationCount" to applicationCount,
            "lastUpdated" to FieldValue.serverTimestamp() // For explicit last update
        )
    }
}
**/