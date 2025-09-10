// PATH: app/src/main/java/com/example/volunteersApp/models/Event.java
package com.example.volunteersApp.models;

import com.google.firebase.Timestamp; // Firestore Timestamp
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date; // For converting Timestamp to Date if needed for display formatting
import java.util.Objects; // For Objects.equals and Objects.hash

/**
 * Represents an event in the application.
 * This class is designed to be used with Firestore for data persistence.
 */
@IgnoreExtraProperties // Ensures that fields in Firestore documents not present in this class are ignored during deserialization.
public class Event {

    @DocumentId // Automatically populated with the document's ID from Firestore when fetched.
    private String eventId;

    private String organizerId; // UID of the user/organization that created the event.
    private String eventName;
    private String description;
    private Timestamp eventTimestamp; // Primary field for event's start date and time. Stored as Firestore Timestamp.
    private String location; // Could be an address or a general location name.
    private String category;
    private String organizerName; // Display name of the organizer.
    private String imageUrl;      // Optional URL for an event image.

    private int volunteersNeeded;
    private int volunteersRegistered; // Tracks how many have signed up.

    private String status; // e.g., "upcoming", "active", "completed", "cancelled".

    @ServerTimestamp // Automatically set by Firestore on creation. Can also update on server-side modifications if configured.
    private Timestamp createdAtTimestamp;

    // Required empty public constructor for Firestore deserialization.
    // Firestore uses this to instantiate objects when retrieving data.
    public Event() {
    }

    // Constructor for creating new Event objects programmatically before saving to Firestore.
    // eventId and createdAtTimestamp are typically set by Firestore, not in this constructor.
    public Event(String organizerId, String eventName, String description, Timestamp eventTimestamp,
                 String location, String category, String organizerName, String imageUrl,
                 int volunteersNeeded, String status) {
        this.organizerId = organizerId;
        this.eventName = eventName;
        this.description = description;
        this.eventTimestamp = eventTimestamp;
        this.location = location;
        this.category = category;
        this.organizerName = organizerName;
        this.imageUrl = imageUrl;
        this.volunteersNeeded = volunteersNeeded;
        this.volunteersRegistered = 0; // Typically starts at 0 when a new event is created.
        this.status = status;
        // createdAtTimestamp will be set by Firestore via @ServerTimestamp annotation.
    }

    // --- Getters ---
    // Getters are used by Firestore during deserialization and by your application code.
    public String getEventId() {
        return eventId;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return eventName;
    }

    public String getDescription() {
        return description;
    }

    public Timestamp getEventTimestamp() {
        return eventTimestamp;
    }

    public String getLocationName() {
        return location;
    }

    public String getCategory() {
        return category;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getVolunteersNeeded() {
        return volunteersNeeded;
    }

    public int getVolunteersRegistered() {
        return volunteersRegistered;
    }

    public String getStatus() {
        return status;
    }

    public Timestamp getCreatedAtTimestamp() {
        return createdAtTimestamp;
    }

    // --- Setters ---
    // Setters are needed by Firestore for deserialization and can be used for programmatic updates.
    // The setter for eventId is particularly useful when @DocumentId is used,
    // allowing Firestore to set the ID after retrieving the document.
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEventTimestamp(Timestamp eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setVolunteersNeeded(int volunteersNeeded) {
        this.volunteersNeeded = volunteersNeeded;
    }

    public void setVolunteersRegistered(int volunteersRegistered) {
        this.volunteersRegistered = volunteersRegistered;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAtTimestamp(Timestamp createdAtTimestamp) {
        this.createdAtTimestamp = createdAtTimestamp;
    }

    // --- Helper method to convert Firestore Timestamp to java.util.Date for formatting ---
    // Useful for displaying dates in the UI if direct Timestamp handling is not preferred.
    public Date getEventDateForDisplay() {
        if (eventTimestamp != null) {
            return eventTimestamp.toDate();
        }
        return null;
    }

    public Date getCreatedAtDateForDisplay() {
        if (createdAtTimestamp != null) {
            return createdAtTimestamp.toDate();
        }
        return null;
    }

    // --- equals() and hashCode() ---
    // Current implementation: Equality based on 'eventId'. This is often sufficient for entities
    // retrieved from a database where eventId is the unique primary key.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(eventId, event.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    // --- toString() for debugging ---
    // Provides a readable string representation of the event, useful for logging.
    @Override
    public String toString() {
        return "Event{" +
                "eventId='" + eventId + '\'' +
                ", eventName='" + eventName + '\'' +
                ", organizerName='" + organizerName + '\'' +
                ", eventTimestamp=" + (eventTimestamp != null ? eventTimestamp.toDate() : "null") +
                ", location='" + location + '\'' +
                ", status='" + status + '\'' +
                ", volunteersNeeded=" + volunteersNeeded +
                ", volunteersRegistered=" + volunteersRegistered +
                ", createdAtTimestamp=" + (createdAtTimestamp != null ? createdAtTimestamp.toDate() : "null") +
                '}';
    }
}
