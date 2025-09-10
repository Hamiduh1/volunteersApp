package com.example.volunteersApp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
// import com.google.firebase.firestore.PropertyName; // Keep if Firestore field names ever differ from Java
// import com.google.firebase.firestore.FieldValue; // For serverTimestamp

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@IgnoreExtraProperties
public class EventDetails {

    @DocumentId
    private String eventId;

    private String eventName;       // Assuming this was 'eventName' or 'name' in your activities
    private String description;
    private String location;
    private String type;            // CHANGED BACK from 'eventType'
    private String skillsRequired;
    private String imageUrl;
    private String status;          // e.g., "Upcoming", "Ongoing", "Completed", "Cancelled"

    private String organizerId;     // CHANGED BACK from 'organizerId'
    private String organizerName;   // CHANGED BACK from 'hostName'

    private Timestamp eventTimestamp; // Specific date and time of the event
    private Timestamp createdAt;      // When the event document was created

    private Integer volunteerLimit;   // Max number of volunteers
    // CHANGED BACK: Current number of confirmed/joined/registered volunteers
    private Integer registeredVolunteersCount;


    private Double payment;
    private Double avgRating;
    private Boolean closeEntries;    // Whether new participants can join
    private Long requiredVolunteers; // Estimated number of volunteers needed

    // Lists for tracking application/acceptance flow if needed
    private List<String> appliedVolunteerUids;  // Users who have applied
    private List<String> acceptedVolunteerUids; // Users whose applications were accepted

    // Transient fields for easy display in UI
    private transient String formattedDate;
    private transient String formattedTime;
    private static final SimpleDateFormat DATE_FORMATTER_DISPLAY = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMATTER_DISPLAY = new SimpleDateFormat("hh:mm a", Locale.getDefault());


    public EventDetails() {
        // Required empty public constructor for Firestore's toObject() method
        this.appliedVolunteerUids = new ArrayList<>();
        this.acceptedVolunteerUids = new ArrayList<>();
        this.registeredVolunteersCount = 0; // Default
        this.volunteerLimit = 0; // Default: 0 often means no specific limit
        this.status = "Upcoming"; // Default status
        this.skillsRequired = "None";
        this.payment = 0.0;
        this.avgRating = 0.0;
        this.closeEntries = false;
        this.requiredVolunteers = 0L;
        // this.createdAt = Timestamp.now(); // Or use FieldValue.serverTimestamp() upon saving
    }

    // Constructor for creating a new event (example, ensure parameters match your common usage)
    public EventDetails(String eventName, String organizerId, String organizerName, String description, String location,
                        String type, Timestamp eventTimestamp, String skillsRequired,
                        Integer volunteerLimit, Long requiredVolunteers, Double payment, String imageUrl, String status) {
        this.eventName = eventName;
        this.organizerId = organizerId;
        this.organizerName = organizerName;
        this.description = description;
        this.location = location;
        this.type = type;
        this.setEventTimestamp(eventTimestamp); // This will populate derived date/time fields
        this.skillsRequired = skillsRequired != null ? skillsRequired : "None";
        this.volunteerLimit = (volunteerLimit != null && volunteerLimit >= 0) ? volunteerLimit : 0;
        this.requiredVolunteers = (requiredVolunteers != null && requiredVolunteers >= 0) ? requiredVolunteers : 0L;
        this.payment = (payment != null) ? payment : 0.0;
        this.imageUrl = imageUrl;
        this.status = (status != null && !status.isEmpty()) ? status : "Upcoming";

        // Defaults for other fields
        this.avgRating = 0.0;
        this.closeEntries = false;
        this.registeredVolunteersCount = 0; // New events start with 0 confirmed participants
        this.appliedVolunteerUids = new ArrayList<>();
        this.acceptedVolunteerUids = new ArrayList<>();
        // this.createdAt = FieldValue.serverTimestamp(); // For new objects; Firestore handles this if field is Object
    }


    // --- Getters ---
    public String getEventId() { return eventId; }
    public String getTitle() { return eventName; }
    public String getDescription() { return description; }
    public String getLocationName() { return location; }
    public String getType() { return type; } // CHANGED BACK
    public String getSkillsRequired() { return skillsRequired; }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public String getOrganizerId() { return organizerId; } // CHANGED BACK
    public String getOrganizerName() { return organizerName; } // CHANGED BACK
    public Timestamp getEventTimestamp() { return eventTimestamp; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Integer getVolunteerLimit() { return volunteerLimit; }

    // CHANGED BACK
    public Integer getRegisteredVolunteersCount() {
        // If you strictly derive this from acceptedVolunteerUids list:
        // return acceptedVolunteerUids != null ? acceptedVolunteerUids.size() : 0;
        // Otherwise, if it's a directly managed field in Firestore:
        return (registeredVolunteersCount != null && registeredVolunteersCount >= 0) ? registeredVolunteersCount : 0;
    }

    public Double getPayment() { return payment; }
    public Double getAvgRating() { return avgRating; }
    public Boolean getCloseEntries() { return closeEntries != null ? closeEntries : false; }
    public Long getRequiredVolunteers() { return requiredVolunteers; }

    public List<String> getAppliedVolunteerUids() {
        return (appliedVolunteerUids != null) ? appliedVolunteerUids : new ArrayList<>();
    }
    public List<String> getAcceptedVolunteerUids() {
        return (acceptedVolunteerUids != null) ? acceptedVolunteerUids : new ArrayList<>();
    }

    // --- Setters ---
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public void setDescription(String description) { this.description = description; }
    public void setLocation(String location) { this.location = location; }
    public void setType(String type) { this.type = type; } // CHANGED BACK
    public void setSkillsRequired(String skillsRequired) { this.skillsRequired = skillsRequired; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setStatus(String status) { this.status = status; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; } // CHANGED BACK
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; } // CHANGED BACK

    public void setEventTimestamp(Timestamp eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
        if (eventTimestamp != null) {
            Date date = eventTimestamp.toDate();
            this.formattedDate = DATE_FORMATTER_DISPLAY.format(date);
            this.formattedTime = TIME_FORMATTER_DISPLAY.format(date);
        } else {
            this.formattedDate = null;
            this.formattedTime = null;
        }
    }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public void setVolunteerLimit(Integer volunteerLimit) {
        this.volunteerLimit = (volunteerLimit != null && volunteerLimit >= 0) ? volunteerLimit : 0;
    }

    // CHANGED BACK
    public void setRegisteredVolunteersCount(Integer registeredVolunteersCount) {
        this.registeredVolunteersCount = (registeredVolunteersCount != null && registeredVolunteersCount >= 0) ? registeredVolunteersCount : 0;
    }

    public void setPayment(Double payment) { this.payment = payment; }
    public void setAvgRating(Double avgRating) { this.avgRating = avgRating; }
    public void setCloseEntries(Boolean closeEntries) { this.closeEntries = closeEntries != null ? closeEntries : false; }
    public void setRequiredVolunteers(Long requiredVolunteers) { this.requiredVolunteers = requiredVolunteers; }

    public void setAppliedVolunteerUids(List<String> appliedVolunteerUids) {
        this.appliedVolunteerUids = (appliedVolunteerUids != null) ? new ArrayList<>(appliedVolunteerUids) : new ArrayList<>();
    }

    public void setAcceptedVolunteerUids(List<String> acceptedVolunteerUids) {
        this.acceptedVolunteerUids = (acceptedVolunteerUids != null) ? new ArrayList<>(acceptedVolunteerUids) : new ArrayList<>();
        // If registeredVolunteersCount should always reflect the size of this list, update it here:
        // this.registeredVolunteersCount = this.acceptedVolunteerUids.size();
        // Or ensure Firestore transactions update both the list and the count atomically.
    }


    // --- Excluded (Transient) Getters for UI Formatting ---
    @Exclude
    public String getFormattedDate() {
        if (formattedDate == null && eventTimestamp != null) {
            setEventTimestamp(this.eventTimestamp);
        }
        return formattedDate != null ? formattedDate : "N/A";
    }

    @Exclude
    public String getFormattedTime() {
        if (formattedTime == null && eventTimestamp != null) {
            setEventTimestamp(this.eventTimestamp);
        }
        return formattedTime != null ? formattedTime : "N/A";
    }

    // --- Business Logic / Helper methods (optional) ---
    @Exclude
    public boolean isOpenForApplication() {
        if (getCloseEntries()) {
            return false;
        }
        if (volunteerLimit != null && volunteerLimit > 0) {
            // This logic depends on how you interpret volunteerLimit and registeredVolunteersCount
            // If registeredVolunteersCount is the number of already confirmed people:
            return getRegisteredVolunteersCount() < volunteerLimit;
            // If it's based on number of people who have only *applied*:
            // return getAppliedVolunteerUids().size() < volunteerLimit;
        }
        return true; // No limit and not closed
    }

    @Exclude
    public int getAvailableSlots() {
        if (volunteerLimit != null && volunteerLimit > 0) {
            int currentRegistered = getRegisteredVolunteersCount();
            return Math.max(0, volunteerLimit - currentRegistered);
        }
        return Integer.MAX_VALUE; // Represents "unlimited"
    }


    // --- equals(), hashCode(), toString() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventDetails that = (EventDetails) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "EventDetails{" +
                "eventId='" + eventId + '\'' +
                ", eventName='" + eventName + '\'' +
                ", organizerName='" + organizerName + '\'' + // CHANGED BACK
                ", type='" + type + '\'' +                   // CHANGED BACK
                ", location='" + location + '\'' +
                ", eventTimestamp=" + (eventTimestamp != null ? getFormattedDate() + " " + getFormattedTime() : "null") +
                ", status='" + status + '\'' +
                ", volunteerLimit=" + volunteerLimit +
                ", registeredVolunteersCount=" + getRegisteredVolunteersCount() + // CHANGED BACK
                ", skillsRequired='" + skillsRequired + '\'' +
                ", closeEntries=" + getCloseEntries() +
                '}';
    }
}


