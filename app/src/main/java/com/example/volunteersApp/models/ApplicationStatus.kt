// In a file like ApplicationStatus.kt or Models.kt within the 'models' package
package com.example.volunteersApp.models



enum class ApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WAITLISTED,
    WITHDRAWN,
    ACCEPTED

    // Add any other statuses you need
}