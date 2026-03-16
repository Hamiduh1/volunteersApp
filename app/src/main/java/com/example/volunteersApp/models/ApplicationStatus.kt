package com.example.volunteersApp.models

import androidx.compose.ui.graphics.Color

// UPDATED: Added displayName and color properties for UI consistency.
enum class ApplicationStatus(val displayName: String, val color: Color) {
    PENDING("Pending", Color.Gray),
    VIEWED("Viewed", Color(0xFFFFA726)), // Orange
    APPROVED("Approved", Color(0xFF66BB6A)), // Green
    REJECTED("Rejected", Color(0xFFEF5350)), // Red
    REJECTED_BY_EMPLOYER("Rejected", Color(0xFFEF5350)), // Red, alias for UI
    WITHDRAWN("Withdrawn", Color.DarkGray),
    WAITLISTED("Waitlisted", Color(0xFF42A5F5)), // Blue
    UNKNOWN("Unknown", Color.Black);

    companion object {
        fun fromString(status: String): ApplicationStatus {
            return try {
                valueOf(status.uppercase())
            } catch (e: IllegalArgumentException) {
                UNKNOWN
            }
        }
    }
}
