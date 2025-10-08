// PATH: app/src/main/java/com/example/volunteersApp/models/Event.java

/**
 *  Purpose: This class represents the event itself. It defines all the intrinsic
 * details of an event that an organizer creates and manages.
  */
// PATH: app/src/main/java/com/example/volunteersApp/models/Event.java
package com.example.volunteersApp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@IgnoreExtraProperties
public class Event {

    @DocumentId
    private String eventId;
    private String organizerId;
    private String eventName;
    private String description;
    private Timestamp eventTimestamp;
    private String location;
    private String category;
    private String organizerName;
    private String imageUrl; // This field was missing a public getter
    private int volunteersNeeded;
    private int volunteersRegistered;
    private String status; // This field was missing a public getter
    @ServerTimestamp
    private Timestamp createdAtTimestamp;

    private List<String> applicants;

    public Event() {
    }

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
        this.volunteersRegistered = 0;
        this.status = status;
    }


    // --- Getters ---

    public String getEventId() { return eventId; }
    public String getOrganizerId() { return organizerId; }
    public String getTitle() { return eventName; }
    public String getDescription() { return description; }
    public Timestamp getEventTimestamp() { return eventTimestamp; }
    public String getLocationName() { return location; }
    public String getCategory() { return category; }
    public String getOrganizerName() { return organizerName; }
    public int getVolunteersNeeded() { return volunteersNeeded; }
    public int getVolunteersRegistered() { return volunteersRegistered; }
    public Timestamp getCreatedAtTimestamp() { return createdAtTimestamp; }

    // --- NEWLY ADDED/FIXED GETTERS ---

    /**
     * ADDED: Exposes the 'status' field for Kotlin property access.
     * Solves the error "Cannot access 'field status'".
     */
    public String getStatus() {
        return status;
    }

    /**
     * ADDED: Exposes the 'imageUrl' field for Kotlin property access.
     * Solves the error "Cannot access 'field imageUrl'".
     */
    public String getImageUrl() {
        return imageUrl;
    }

    // These were added in the previous step but are kept here for completeness.
    public String getLocation() { return location; }
    public Date getDate() {
        if (eventTimestamp != null) {
            return eventTimestamp.toDate();
        }
        return null;
    }
    public List<String> getApplicants() { return applicants; }

    // --- END OF NEWLY ADDED/FIXED GETTERS ---


    // --- Setters ---
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public void setDescription(String description) { this.description = description; }
    public void setEventTimestamp(Timestamp eventTimestamp) { this.eventTimestamp = eventTimestamp; }
    public void setLocation(String location) { this.location = location; }
    public void setCategory(String category) { this.category = category; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; } // The setter already existed
    public void setVolunteersNeeded(int volunteersNeeded) { this.volunteersNeeded = volunteersNeeded; }
    public void setVolunteersRegistered(int volunteersRegistered) { this.volunteersRegistered = volunteersRegistered; }
    public void setStatus(String status) { this.status = status; } // The setter already existed
    public void setCreatedAtTimestamp(Timestamp createdAtTimestamp) { this.createdAtTimestamp = createdAtTimestamp; }
    public void setApplicants(List<String> applicants) { this.applicants = applicants; }


    // ... equals, hashCode, and toString methods are unchanged ...
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
}
