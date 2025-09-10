package com.example.volunteersApp;

import com.example.volunteersApp.models.EventModel; // Ensure this path is correct
import java.util.Objects;

public class VolunteerEventItem {

    private String eventId;      // Firestore Document ID
    private String organizerId;  // UID of the host (consistent with EventModel)
    private EventModel eventDetails;

    // Public no-argument constructor: useful for some libraries or manual instantiation.
    // If this class were directly deserialized by Firestore, it would be essential.
    public VolunteerEventItem() {
    }

    /**
     * Constructs a VolunteerEventItem.
     *
     * @param eventId      The unique ID of the event (typically Firestore document ID).
     * @param organizerId  The unique ID of the event host.
     * @param eventDetails The detailed information about the event (EventModel).
     * @throws IllegalArgumentException if any of the critical parameters are null or invalid.
     */
    public VolunteerEventItem(String eventId, String organizerId, EventModel eventDetails) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty for VolunteerEventItem construction.");
        }
        // Assuming organizerId is crucial for this item's context.
        // If it could legitimately be null for some VolunteerEventItem instances, remove this check
        // or provide a different constructor.
        if (organizerId == null || organizerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Organizer ID cannot be null or empty for VolunteerEventItem construction.");
        }
        if (eventDetails == null) {
            throw new IllegalArgumentException("EventDetails cannot be null for VolunteerEventItem construction.");
        }

        this.eventId = eventId;
        this.organizerId = organizerId;
        this.eventDetails = eventDetails;
    }

    // --- Getters ---
    public String getEventId() {
        return eventId;
    }

    // CORRECTED: Standard Java naming convention
    public String getOrganizerId() {
        return organizerId;
    }

    public EventModel getEventDetails() {
        return eventDetails;
    }

    // --- Setters ---
    // Useful if the object needs to be modified after creation, or for some mapping libraries.
    // If immutability is desired, these could be removed and fields made final.

    public void setEventId(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty when setting.");
        }
        this.eventId = eventId;
    }

    // CORRECTED: Standard Java naming convention
    public void setOrganizerId(String organizerId) {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Organizer ID cannot be null or empty when setting.");
        }
        this.organizerId = organizerId;
    }

    public void setEventDetails(EventModel eventDetails) {
        if (eventDetails == null) {
            throw new IllegalArgumentException("EventDetails cannot be null when setting.");
        }
        this.eventDetails = eventDetails;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VolunteerEventItem that = (VolunteerEventItem) o;
        // Compare based on eventId first, as it should be unique.
        // Then consider organizerId and eventDetails if finer-grained equality is needed,
        // though relying on EventModel's equals (which uses eventId) might be sufficient.
        return Objects.equals(eventId, that.eventId) &&
                Objects.equals(organizerId, that.organizerId) &&
                Objects.equals(eventDetails, that.eventDetails); // Relies on EventModel.equals()
    }

    @Override
    public int hashCode() {
        // Use the same fields as in equals()
        return Objects.hash(eventId, organizerId, eventDetails); // Relies on EventModel.hashCode()
    }

    @Override
    public String toString() {
        return "VolunteerEventItem{" +
                "eventId='" + eventId + '\'' +
                ", organizerId='" + organizerId + '\'' +
                ", eventDetailsTitle=" + (eventDetails != null && eventDetails.getTitle() != null ? eventDetails.getTitle() : "N/A") +
                '}';
    }


}

