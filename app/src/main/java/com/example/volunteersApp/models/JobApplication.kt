package com.example.volunteersApp.models

import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.*

data class JobApplication(
    @DocumentId
    var applicationId: String = "",
    val jobId: String = "",
    val jobTitle: String? = "",
    val organizationName: String? = "",
    val userId: String = "",
    val volunteerName: String? = "",
    val volunteerEmail: String? = "",
    val volunteerProfileImageUrl: String? = null,
    val notesFromVolunteer: String? = null,
    val employerUid: String? = "",
    var status: String = ApplicationStatus.PENDING.name,
    @ServerTimestamp
    val appliedAt: Date? = null,
    @ServerTimestamp
    var lastUpdatedAt: Date? = null
) {
    val statusEnum: ApplicationStatus
        get() = try {
            ApplicationStatus.valueOf(status.uppercase(Locale.getDefault()))
        } catch (e: IllegalArgumentException) {
            ApplicationStatus.UNKNOWN
        }

    val formattedDate: String
        get() = appliedAt?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "N/A"

    val statusColor: Color
        get() = statusEnum.color

    val statusDisplayName: String
        get() = statusEnum.displayName

    val canWithdraw: Boolean
        get() = statusEnum == ApplicationStatus.PENDING || statusEnum == ApplicationStatus.VIEWED

    val companyName: String?
        get() = organizationName
}
