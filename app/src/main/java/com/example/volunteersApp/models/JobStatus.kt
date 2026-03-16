package com.example.volunteersApp.models

/**
 * Type-safe enum for the status of a Job.
 */
enum class JobStatus {
    OPEN,        // Actively accepting applications.
    CLOSED,      // No longer accepting applications (manually closed or deadline passed).
    IN_PROGRESS, // Work is ongoing.
    COMPLETED,   // The job/event has finished.
    FILLED       // All available slots have been filled.
}
