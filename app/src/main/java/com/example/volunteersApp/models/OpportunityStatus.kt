package com.example.volunteersApp.models

/**
 * A type-safe enum for the status of an Opportunity.
 */
enum class OpportunityStatus {
    // Available for new applicants
    OPEN,

    // No longer accepting applicants
    CLOSED,

    // The event or job is finished
    COMPLETED,

    // An event that has not yet occurred
    UPCOMING,

    // Default state for when the status is not set or recognized
    UNKNOWN
}
