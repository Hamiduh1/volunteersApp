package com.example.volunteersApp.models

import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.*

@IgnoreExtraProperties
data class JobApplication(
    var applicationId: String = "",
    val jobId: String = "",
    val jobTitle: String? = "",
    val organizationName: String? = "",
    val userId: String = "",
    val volunteerUid: String? = null,
    val volunteerName: String? = "",
    val volunteerEmail: String? = "",
    val volunteerProfileImageUrl: String? = null,
    val notesFromVolunteer: String? = null,
    val employerUid: String? = "",
    val employerId: String? = null,
    var status: String = ApplicationStatus.PENDING.name,
    @ServerTimestamp
    val appliedAt: Date? = null,
    @ServerTimestamp
    val appliedDate: Date? = null,
    @ServerTimestamp
    var lastUpdatedAt: Date? = null
) {
    val applicantUid: String
        get() = userId.ifBlank { volunteerUid.orEmpty() }

    val statusEnum: ApplicationStatus
        get() = try {
            ApplicationStatus.valueOf(status.uppercase(Locale.getDefault()))
        } catch (e: IllegalArgumentException) {
            ApplicationStatus.UNKNOWN
        }

    val formattedDate: String
        get() = (appliedAt ?: appliedDate)?.let {
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
