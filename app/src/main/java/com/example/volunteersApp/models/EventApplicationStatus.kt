package com.example.volunteersApp.models

/**
 * Represents the various statuses an application for an event can have.
 */
enum class EventApplicationStatus {
    /**
     * The application has been submitted by the volunteer and is awaiting review by the organizer.
     */
    PENDING,

    /**
     * The organizer has viewed the application but has not yet made a decision.
     * (Optional status, some systems go directly from PENDING to APPROVED/REJECTED)
     */
    VIEWED,

    /**
     * The organizer has approved the volunteer's application for the event.
     */
    APPROVED,

    /**
     * The organizer has rejected the volunteer's application for the event.
     */
    REJECTED,

    /**
     * The volunteer has been placed on a waitlist for the event,
     * typically if the event is full but there's a chance spots might open up.
     */
    WAITLISTED,

    /**
     * The volunteer themself has withdrawn their application before a decision was made
     * or after being approved/waitlisted.
     */
    WITHDRAWN_BY_VOLUNTEER,

    /**
     * The application was automatically cancelled, e.g., if the event itself was cancelled.
     * (Optional, depends on system complexity)
     */
    CANCELLED_AUTOMATICALLY,

    /**
     * The volunteer confirmed their attendance after being approved.
     * (Optional, for events requiring an extra confirmation step)
     */
    ATTENDANCE_CONFIRMED,

    /**
     * The volunteer was approved but did not show up for the event.
     * (Optional, post-event status update)
     */
    NO_SHOW,

    /**
     * The volunteer attended and completed their role at the event.
     * (Optional, post-event status update)
     */
    COMPLETED
}

// Example of how you might use it in your EventApplication model:
/*
@IgnoreExtraProperties
public class EventApplication {
    // ... other fields ...
    private String status; // Store as String in Firestore

    // ... constructors ...

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Helper to get enum (not stored in Firestore directly unless you use a converter)
    @Exclude
    public EventApplicationStatus getStatusEnum() {
        try {
            if (status != null) {
                return EventApplicationStatus.valueOf(status.uppercase(Locale.ROOT));
            }
        } catch (IllegalArgumentException e) {
            // Log.w("EventApplication", "Unknown status string: " + status);
        }
        return EventApplicationStatus.PENDING; // Or some other default/unknown status
    }

    // Helper to set status from enum
    @Exclude
    public void setStatusEnum(EventApplicationStatus statusEnum) {
        this.status = statusEnum.name();
    }
}
*/
