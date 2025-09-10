
package com.example.volunteersApp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId; // It's good practice to add this if 'id' is Firestore's document ID
import java.util.Objects;

public class VolunteerOpportunity {

    @DocumentId // If 'id' is mapped directly to Firestore document ID
    private String id;
    private String name;        // This field will serve as the "title"
    private String location;    // This field will serve as the "locationName"
    private String description;
    private double payment;
    private String type;        // This field will serve as the "category" or "type"
    private Timestamp eventTimestamp;
    private String organizerId;
    private String hostName;
    private String imageUrl;
    private Long requiredVolunteers;
    private Integer volunteerLimit;
    private Integer registeredVolunteersCount;
    private boolean closeEntries;
    private double avgRating;

    // Public no-argument constructor for Firestore deserialization
    public VolunteerOpportunity() {}

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    // 'name' field is used for the title. Getter is getTitle() for compatibility/semantics.
    public String getTitle() { return name; }
    public void setTitle(String name) { this.name = name; } // Setter sets the 'name' field

    // 'location' field is used for the location name. Getter is getLocationName().
    public String getLocationName() { return location; }
    public void setLocationName(String location) { this.location = location; } // Setter sets the 'location' field

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPayment() { return payment; }
    public void setPayment(double payment) { this.payment = payment; }

    // 'type' field is used for category/type. Getter is getType().
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Timestamp getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Timestamp eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

public Long getRequiredVolunteers() { return requiredVolunteers; }
public void setRequiredVolunteers(Long requiredVolunteers) { this.requiredVolunteers = requiredVolunteers; }

public Integer getVolunteerLimit() { return volunteerLimit; }
public void setVolunteerLimit(Integer volunteerLimit) { this.volunteerLimit = volunteerLimit; }

public Integer getRegisteredVolunteersCount() { return registeredVolunteersCount; }
public void setRegisteredVolunteersCount(Integer registeredVolunteersCount) { this.registeredVolunteersCount = registeredVolunteersCount; }

public boolean isCloseEntries() { return closeEntries; } // Standard getter for boolean
public void setCloseEntries(boolean closeEntries) { this.closeEntries = closeEntries; }

public double getAvgRating() { return avgRating; }
public void setAvgRating(double avgRating) { this.avgRating = avgRating; }

// --- Adapting existing methods from your original VolunteerOpportunity ---
// The previous getTitle() which was empty has been removed.
// The previous getTitle() which returned 'title' (non-existent field) is now corrected to return 'name'.

// This method provides a concept of "Organization" which could be the hostName or fallback to locationName
public String getOrganization() {
    return (hostName != null && !hostName.isEmpty()) ? hostName : getLocationName();
}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VolunteerOpportunity that = (VolunteerOpportunity) o;
        return Double.compare(that.payment, payment) == 0 &&
                closeEntries == that.closeEntries &&
                Double.compare(that.avgRating, avgRating) == 0 &&
                Objects.equals(id, that.id) &&                     // Use java.util.Objects
                Objects.equals(name, that.name) &&                  // Use java.util.Objects
                Objects.equals(location, that.location) &&          // Use java.util.Objects
                Objects.equals(description, that.description) &&    // Use java.util.Objects
                Objects.equals(type, that.type) &&                  // Use java.util.Objects
                Objects.equals(eventTimestamp, that.eventTimestamp) &&// Use java.util.Objects
                Objects.equals(organizerId, that.organizerId) &&    // Use java.util.Objects
                Objects.equals(hostName, that.hostName) &&          // Use java.util.Objects
                Objects.equals(imageUrl, that.imageUrl) &&          // Use java.util.Objects
                Objects.equals(requiredVolunteers, that.requiredVolunteers) && // Use java.util.Objects
                Objects.equals(volunteerLimit, that.volunteerLimit) &&        // Use java.util.Objects
                Objects.equals(registeredVolunteersCount, that.registeredVolunteersCount); // Use java.util.Objects
    }

    @Override
    public int hashCode() {
        // Use java.util.Objects.hash(...)
        return Objects.hash(id, name, location, description, payment, type, eventTimestamp,
                organizerId, hostName, imageUrl, requiredVolunteers, volunteerLimit,
                registeredVolunteersCount, closeEntries, avgRating);
    }


@Override
public String toString() {
    return "VolunteerOpportunity{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", location='" + location + '\'' +
            ", eventTimestamp=" + eventTimestamp +
            ", type='" + type + '\'' +
            '}';
}
}

