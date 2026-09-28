package com.example.volunteersApp.models

import androidx.compose.ui.graphics.Color
import java.util.Locale

// UPDATED: Added displayName and color properties for UI consistency.
enum class ApplicationStatus(val displayName: String, val color: Color) {
    PENDING("Pending", Color(0xFF1565C0)),
    PENDING_PAYMENT("Awaiting payment", Color(0xFFB45309)),
    VIEWED("Viewed", Color(0xFFB45309)),
    APPROVED("Approved", Color(0xFF0F766E)),
    ACCEPTED("Accepted", Color(0xFF0F766E)),
    ATTENDED("Attended", Color(0xFF0F766E)),
    COMPLETED("Completed", Color(0xFF2563EB)),
    REJECTED("Rejected", Color(0xFFB42318)),
    REJECTED_BY_EMPLOYER("Rejected", Color(0xFFB42318)),
    WITHDRAWN("Withdrawn", Color(0xFF5F6B7A)),
    WAITLISTED("Waitlisted", Color(0xFF7C3AED)),
    UNKNOWN("Unknown", Color(0xFF475467));

    companion object {
        fun fromString(status: String?): ApplicationStatus {
            val normalized = status?.trim().orEmpty()
            if (normalized.isEmpty()) return UNKNOWN
            return try {
                valueOf(normalized.uppercase(Locale.ROOT))
            } catch (e: IllegalArgumentException) {
                UNKNOWN
            }
        }
    }
}
