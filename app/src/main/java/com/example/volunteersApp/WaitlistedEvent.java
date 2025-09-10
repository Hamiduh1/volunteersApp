package com.example.volunteersApp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Added for clarity on eventDetails in constructor/setter

import com.example.volunteersApp.models.EventModel; // Ensure this import is correct
import java.util.Objects;

public class WaitlistedEvent {
    // Fields
    private EventModel eventDetails;
    private String eventId;
    private String organizerId; // This typically refers to the ID of the user/entity that organized the event

    // Default constructor for Firebase/Firestore deserialization
    public WaitlistedEvent() {
        // Public no-argument constructor
    }

    // Parameterized constructor
    public WaitlistedEvent(@Nullable EventModel eventDetails, @Nullable String eventId, @Nullable String organizerId) {
        this.eventDetails = eventDetails;
        this.eventId = eventId;
        this.organizerId = organizerId;
    }

    // Getters
    @Nullable
    public EventModel getEventDetails() {
        return eventDetails;
    }

    @Nullable
    public String getEventId() {
        return eventId;
    }

    @Nullable
    public String getOrganizerId() { // Corrected Naming Convention
        return organizerId;
    }

    // Setters
    public void setEventDetails(@Nullable EventModel eventDetails) {
        this.eventDetails = eventDetails;
    }

    public void setEventId(@Nullable String eventId) {
        this.eventId = eventId;
    }

    public void setOrganizerId(@Nullable String organizerId) { // Corrected Naming Convention
        this.organizerId = organizerId;
    }


    @NonNull
    @Override
    public String toString() {
        if (eventDetails != null && eventDetails.getTitle() != null) {
            return "Waitlisted: " + eventDetails.getTitle(); // Added prefix for clarity
        }
        if (eventId != null) {
            return "Waitlisted Event ID: " + eventId;
        }
        return "Waitlisted Event (Details Unavailable)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WaitlistedEvent that = (WaitlistedEvent) o;
        // Primary equality based on eventId if available, as eventDetails might be complex or change
        if (eventId != null) {
            return Objects.equals(eventId, that.eventId) &&
                    Objects.equals(organizerId, that.organizerId); // Organizer ID can also be part of the key
        }
        // Fallback to reference equality or more complex comparison if eventId is null
        return Objects.equals(organizerId, that.organizerId) &&
                Objects.equals(eventDetails, that.eventDetails); // Compare all fields for full equality
    }

    @Override
    public int hashCode() {
        // Hash based on the same fields used in equals()
        return Objects.hash(eventId, organizerId, eventDetails);
    }
}

