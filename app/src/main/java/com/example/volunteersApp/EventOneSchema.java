package com.example.volunteersApp;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.FieldValue; // For serverTimestamp
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.Timestamp; // Firestore Timestamp

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

@IgnoreExtraProperties
public class EventOneSchema {

    @DocumentId
    private String eventId; // Automatically populated by Firestore

    private String eventName;
    private String description;
    private String location;
    private String organizerId;     // ID of the user/organization who created the event
    private String organizerName;   // Name of the user/organization hosting
    private String category;        // RENAMED from eventType for consistency with ViewModel/Fragment
    private String imageUrl;
    private String skillsRequired;

    private Double payment;
    private Double avgRating;
    private Long requiredVolunteers;
    private Integer volunteerLimit;
    private Integer participantsCount;
    // private String eventType; // <-- REMOVED

    private Boolean closeEntries;

    private Timestamp eventTimestamp;
    private Object createdAt; // Use Object for FieldValue.serverTimestamp()

    private String status;          // e.g., "upcoming", "ongoing", "completed", "cancelled"

    // --- Transient fields for derived date/time information ---
    @Exclude
    private transient Date eventDateObject;
    @Exclude
    private transient String eventDateString;
    @Exclude
    private transient int eventTimeHours = -1;
    @Exclude
    private transient int eventTimeMinutes = -1;

    private int volunteersRegistered;
    private int volunteersNeeded; // Derived: requiredVolunteers - volunteersRegistered

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    // --- Constructors ---

    public EventOneSchema() {
        // Required empty public constructor for Firestore's toObject() method
        this.participantsCount = 0;
        this.volunteersRegistered = 0;
        this.avgRating = 0.0;
        this.closeEntries = false;
        this.status = "upcoming"; // Default status
        this.volunteersNeeded = 0;
    }

    // Constructor for creating a new event
    public EventOneSchema(String eventName, String organizerId, String organizerName, String description, String location,
                          String category, Timestamp eventTimestamp, String skillsRequired,
                          Double payment, Long requiredVolunteers, Integer volunteerLimit, String imageUrl, String status) {
        this.eventName = eventName;
        this.organizerId = organizerId;
        this.organizerName = organizerName;
        this.description = description;
        this.location = location;
        this.category = category; // This is already correct
        this.setEventTimestamp(eventTimestamp);
        this.skillsRequired = skillsRequired;
        this.payment = (payment != null) ? payment : 0.0;
        this.requiredVolunteers = (requiredVolunteers != null) ? requiredVolunteers : 0L;
        this.volunteerLimit = volunteerLimit;
        this.imageUrl = imageUrl;
        this.status = (status != null && !status.isEmpty()) ? status : "upcoming";

        // Defaults & Initial derived values
        this.avgRating = 0.0;
        this.closeEntries = false;
        this.participantsCount = 0;
        this.volunteersRegistered = 0;
        this.volunteersNeeded = (requiredVolunteers != null) ? requiredVolunteers.intValue() : 0;
        this.createdAt = FieldValue.serverTimestamp();
    }

    // --- Getters and Setters ---

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocationName() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    // Getter and Setter for eventType REMOVED
    // public String getEventType() { return eventType; }
    // public void setEventType(String eventType) { this.eventType = eventType; }

    public String getOrganizerName() { return organizerName; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getSkillsRequired() { return skillsRequired; }
    public void setSkillsRequired(String skillsRequired) { this.skillsRequired = skillsRequired; }

    public Double getPayment() { return payment; }
    public void setPayment(Double payment) { this.payment = payment; }

    public Double getAvgRating() { return avgRating; }
    public void setAvgRating(Double avgRating) { this.avgRating = avgRating; }

    public Long getRequiredVolunteers() { return requiredVolunteers; }
    public void setRequiredVolunteers(Long requiredVolunteers) {
        this.requiredVolunteers = requiredVolunteers;
        updateVolunteersNeeded();
    }

    public Integer getVolunteerLimit() { return volunteerLimit; }
    public void setVolunteerLimit(Integer volunteerLimit) { this.volunteerLimit = volunteerLimit; }

    public Integer getParticipantsCount() { return participantsCount; }
    public void setParticipantsCount(Integer participantsCount) { this.participantsCount = participantsCount; }

    public Boolean getCloseEntries() { return closeEntries; }
    public void setCloseEntries(Boolean closeEntries) { this.closeEntries = closeEntries; }

    public Timestamp getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Timestamp eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
        if (eventTimestamp != null) {
            this.eventDateObject = eventTimestamp.toDate();
            this.eventDateString = DATE_FORMATTER.format(this.eventDateObject);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(this.eventDateObject);
            this.eventTimeHours = calendar.get(Calendar.HOUR_OF_DAY);
            this.eventTimeMinutes = calendar.get(Calendar.MINUTE);
        } else {
            this.eventDateObject = null;
            this.eventDateString = null;
            this.eventTimeHours = -1;
            this.eventTimeMinutes = -1;
        }
    }

    public Object getCreatedAt() { return createdAt; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getVolunteersRegistered() { return volunteersRegistered; }
    public void setVolunteersRegistered(int volunteersRegistered) {
        this.volunteersRegistered = volunteersRegistered;
        updateVolunteersNeeded();
    }

    public int getVolunteersNeeded() {
        return volunteersNeeded;
    }

    public void setVolunteersNeeded(int volunteersNeeded) {
        this.volunteersNeeded = volunteersNeeded;
    }

    private void updateVolunteersNeeded() {
        if (this.requiredVolunteers != null) {
            this.volunteersNeeded = Math.max(0, this.requiredVolunteers.intValue() - this.volunteersRegistered);
        } else {
            this.volunteersNeeded = 0;
        }
    }

    // --- Excluded (Transient) Getters ---
    @Exclude
    public Date getEventDateObject() { return eventDateObject; }

    @Exclude
    public String getEventDateString() { return eventDateString; }

    @Exclude
    public int getEventTimeHours() { return eventTimeHours; }

    @Exclude
    public int getEventTimeMinutes() { return eventTimeMinutes; }

    @Exclude
    public String getFormattedTime() {
        if (eventTimeHours != -1 && eventTimeMinutes != -1) {
            return String.format(Locale.getDefault(), "%02d:%02d", eventTimeHours, eventTimeMinutes);
        }
        return "N/A";
    }

    // --- Overridden Methods ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventOneSchema that = (EventOneSchema) o;
        return Objects.equals(eventId, that.eventId) &&
                Objects.equals(eventName, that.eventName) &&
                Objects.equals(organizerId, that.organizerId) &&
                Objects.equals(eventTimestamp, that.eventTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, eventName, organizerId, eventTimestamp);
    }

    @Override
    public String toString() {
        return "EventModel{" +
                "eventId='" + eventId + '\'' +
                ", eventName='" + eventName + '\'' +
                ", description='" + description + '\'' +
                ", location='" + location + '\'' +
                ", organizerId='" + organizerId + '\'' +
                ", organizerName='" + organizerName + '\'' +
                // ", eventType='" + eventType + '\'' + // REMOVED
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", skillsRequired='" + skillsRequired + '\'' +
                ", payment=" + payment +
                ", avgRating=" + avgRating +
                ", requiredVolunteers=" + requiredVolunteers +
                ", volunteerLimit=" + volunteerLimit +
                ", participantsCount=" + participantsCount +
                ", volunteersRegistered=" + volunteersRegistered +
                ", volunteersNeeded=" + volunteersNeeded +
                ", closeEntries=" + closeEntries +
                ", eventTimestamp=" + (eventTimestamp != null ? eventTimestamp.toDate().toString() : "null") +
                ", createdAt=" + (createdAt instanceof Timestamp ? ((Timestamp) createdAt).toDate().toString() : (createdAt != null ? createdAt.toString() : "null")) +
                ", eventDateString='" + getEventDateString() + '\'' +
                ", formattedTime='" + getFormattedTime() + '\'' +
                '}';
    }
}

