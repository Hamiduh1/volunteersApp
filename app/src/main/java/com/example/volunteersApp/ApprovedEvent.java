package com.example.volunteersApp;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import java.util.Objects;
import com.example.volunteersApp.models.EventModel;

// Assuming EventModel is in the same package or imported correctly.

public class ApprovedEvent {

    @DocumentId // If this ApprovedEvent is a document and you want its ID here.
    // OR, if this field should be named differently in Firestore, use @PropertyName
    private String approvedEventId; // Unique ID for this entry in the "approvedEvents" collection

    private String eventId;         // ID of the original event in the "events" collection
    private String eventName;       // Name of the event (denormalized for quick access)
    private String organizerId;     // UID of the event organizer (renamed from organizerId)

    // Contains all other details from EventModel.
    // This is useful for having all event data readily available with the approved event entry.
    // If 'eventDetails' in Firestore is named differently, e.g., "event_data":
    // @PropertyName("event_data")
    private EventModel eventDetails;

    /**
     * Default constructor required for calls to Firestore's toObject(Class) method.
     */
    public ApprovedEvent() {
        // Default constructor is essential for Firestore deserialization
    }

    /**
     * Constructs a new ApprovedEvent.
     *
     * @param eventId      The unique ID of the original event (from the main events collection).
     * @param eventName    The name of the event.
     * @param organizerId  The unique ID of the organizer of the event.
     * @param eventDetails The full details of the event (EventModel object).
     */
    public ApprovedEvent(String eventId, String eventName, String organizerId, EventModel eventDetails) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.organizerId = organizerId;
        this.eventDetails = eventDetails;
    }

    // --- Getters ---

    public String getApprovedEventId() {
        return approvedEventId;
    }

    public String getEventId() {
        return eventId;
    }

    /**
     * Gets the name of the event. This is often denormalized from EventModel
     * for easier display in lists of approved events.
     *
     * @return The name of the event.
     */
    public String getTitle() {
        // Fallback to get name from eventDetails if eventName field is somehow null
        // but eventDetails and its name are present. This provides some resilience.
        if (eventName == null && eventDetails != null && eventDetails.getTitle() != null) {
            return eventDetails.getTitle();
        }
        return eventName;
    }

    /**
     * Gets the unique ID of the event organizer.
     *
     * @return The organizer's unique ID.
     */
    public String getOrganizerId() {
        return organizerId;
    }

    public EventModel getEventDetails() {
        return eventDetails;
    }

    // --- Setters ---
    // Setters are good practice for Firestore deserialization and for manual object creation.

    public void setApprovedEventId(String approvedEventId) {
        this.approvedEventId = approvedEventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public void setEventDetails(EventModel eventDetails) {
        this.eventDetails = eventDetails;
        // Optionally, ensure eventName is synchronized if eventDetails is set/updated
        if (this.eventName == null && eventDetails != null && eventDetails.getTitle() != null) {
            this.eventName = eventDetails.getTitle();
        }
    }

    // --- Optional: toString(), equals(), and hashCode() for debugging and collections ---

    @Override
    public String toString() {
        return "ApprovedEvent{" +
                "approvedEventId='" + approvedEventId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", eventName='" + getTitle() + '\'' + // Use getter to benefit from fallback
                ", organizerId='" + organizerId + '\'' +
                ", eventDetailsPresent=" + (eventDetails != null) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovedEvent that = (ApprovedEvent) o;
        return Objects.equals(approvedEventId, that.approvedEventId) && // Compare by its own unique ID
                Objects.equals(eventId, that.eventId) &&
                Objects.equals(getTitle(), that.getTitle()) && // Compare by getter
                Objects.equals(organizerId, that.organizerId) &&
                Objects.equals(eventDetails, that.eventDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(approvedEventId, eventId, getTitle(), organizerId, eventDetails);
    }
}
