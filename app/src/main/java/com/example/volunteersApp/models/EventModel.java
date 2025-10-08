package com.example.volunteersApp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.database.Exclude;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Objects;

public class EventModel {

    @DocumentId
    private String eventId; // Unique ID for the opportunity (job or event)

    private String title;
    private String description;
    private String category;
    private Timestamp eventDateTime;
    private String duration;
    private String locationName;
    private String locationAddress;
    private GeoPoint geoPoint;
    private Double payment; // If applicable to either jobs or events

    private Integer volunteerLimit;
    private Integer participantsCount; // Volunteers/applicants signed up

    private List<String> requiredSkills;
    private String imageUrl;
    private boolean isActive; // General flag for whether the opportunity is active
    private String status;    // e.g., "OPEN", "CLOSED", "FILLED", "PENDING_APPROVAL", "DRAFT"

    // --- Fields to distinguish poster type and their specific info ---
    private String opportunityType; // "JOB" or "EVENT" - CRUCIAL FOR DIFFERENTIATION

    // Organizer-specific fields (populated if opportunityType is "EVENT")
    private String organizerId;
    private String organizerName;
    // Potentially other organizer-specific details

    // Employer-specific fields (populated if opportunityType is "JOB")
    private String employerUid;
    private String employerOrganizationName; // Could be the company name
    private String jobSpecificRole; // If a "job" has a role distinct from the main "title"
    // Potentially other employer/job-specific details

    // --- Common fields potentially from JobPost or for better structure ---
    private String recurrenceType; // E.g., "One-time", "Ongoing", "Flexible" (replaces JobPost.jobType)
    private String requirements;   // Specific prerequisites or conditions
    private String contactInfo;    // Generic contact info (could be organizer's or employer's based on type)
    @Exclude
    private String userStatus;

    @Exclude
    public String getUserStatus() {
        return userStatus;
    }

    @Exclude
    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    @ServerTimestamp
    private Timestamp createdAt;
    @ServerTimestamp
    private Timestamp lastUpdatedAt;

    public EventModel() {
        // No-argument constructor required for Firestore
    }

    // --- Getters ---
    public String getEventId() { return eventId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public Timestamp getEventDateTime() { return eventDateTime; }
    public String getDuration() { return duration; }
    public String getLocationName() { return locationName; }
    public String getLocationAddress() { return locationAddress; }
    public GeoPoint getGeoPoint() { return geoPoint; }
    public Double getPayment() { return payment; }
    public Integer getVolunteerLimit() { return volunteerLimit; }
    public Integer getParticipantsCount() { return participantsCount; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return isActive; }
    public String getStatus() { return status; }

    public String getOpportunityType() { return opportunityType; }

    public String getOrganizerId() { return organizerId; }
    public String getOrganizerName() { return organizerName; }

    public String getEmployerUid() { return employerUid; }
    public String getEmployerOrganizationName() { return employerOrganizationName; }
    public String getJobSpecificRole() { return jobSpecificRole; }

    public String getRecurrenceType() { return recurrenceType; }
    public String getRequirements() { return requirements; }
    public String getContactInfo() { return contactInfo; }

    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getLastUpdatedAt() { return lastUpdatedAt; }

    public Date getEventDateObject() {
        if (eventDateTime != null) {
            return eventDateTime.toDate();
        }
        return null;
    }

    // --- Setters ---
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setEventDateTime(Timestamp eventDateTime) { this.eventDateTime = eventDateTime; }
    public void setDuration(String duration) { this.duration = duration; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public void setLocationAddress(String locationAddress) { this.locationAddress = locationAddress; }
    public void setGeoPoint(GeoPoint geoPoint) { this.geoPoint = geoPoint; }
    public void setPayment(Double payment) { this.payment = payment; }
    public void setVolunteerLimit(Integer volunteerLimit) { this.volunteerLimit = volunteerLimit; }
    public void setParticipantsCount(Integer participantsCount) { this.participantsCount = participantsCount; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setActive(boolean active) { isActive = active; }
    public void setStatus(String status) { this.status = status; }

    public void setOpportunityType(String opportunityType) { this.opportunityType = opportunityType; }

    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }

    public void setEmployerUid(String employerUid) { this.employerUid = employerUid; }
    public void setEmployerOrganizationName(String employerOrganizationName) { this.employerOrganizationName = employerOrganizationName; }
    public void setJobSpecificRole(String jobSpecificRole) { this.jobSpecificRole = jobSpecificRole; }

    public void setRecurrenceType(String recurrenceType) { this.recurrenceType = recurrenceType; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setLastUpdatedAt(Timestamp lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventModel that = (EventModel) o;
        // For identity, eventId is key.
        if (!Objects.equals(eventId, that.eventId)) return false;
        // For content, compare all relevant fields.
        return Objects.equals(title, that.title) &&
                Objects.equals(description, that.description) &&
                Objects.equals(category, that.category) &&
                Objects.equals(eventDateTime, that.eventDateTime) &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(locationName, that.locationName) &&
                Objects.equals(locationAddress, that.locationAddress) &&
                Objects.equals(geoPoint, that.geoPoint) &&
                Objects.equals(payment, that.payment) &&
                Objects.equals(volunteerLimit, that.volunteerLimit) &&
                Objects.equals(participantsCount, that.participantsCount) &&
                Objects.equals(requiredSkills, that.requiredSkills) &&
                Objects.equals(imageUrl, that.imageUrl) &&
                isActive == that.isActive &&
                Objects.equals(status, that.status) &&
                Objects.equals(opportunityType, that.opportunityType) &&
                Objects.equals(organizerId, that.organizerId) &&
                Objects.equals(organizerName, that.organizerName) &&
                Objects.equals(employerUid, that.employerUid) &&
                Objects.equals(employerOrganizationName, that.employerOrganizationName) &&
                Objects.equals(jobSpecificRole, that.jobSpecificRole) &&
                Objects.equals(recurrenceType, that.recurrenceType) &&
                Objects.equals(requirements, that.requirements) &&
                Objects.equals(contactInfo, that.contactInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, title, description, category, eventDateTime, duration,
                locationName, locationAddress, geoPoint, payment, volunteerLimit, participantsCount,
                requiredSkills, imageUrl, isActive, status, opportunityType, organizerId,
                organizerName, employerUid, employerOrganizationName, jobSpecificRole,
                recurrenceType, requirements, contactInfo);
    }

    @Override
    public String toString() {
        return "EventModel{" +
                "eventId='" + eventId + '\'' +
                ", opportunityType='" + opportunityType + '\'' +
                ", title='" + title + '\'' +
                (organizerName != null ? ", organizerName='" + organizerName + '\'' : "") +
                (employerOrganizationName != null ? ", employerOrganizationName='" + employerOrganizationName + '\'' : "") +
                ", eventDateTime=" + (eventDateTime != null ? eventDateTime.toDate() : "null") +
                ", status='" + status + '\'' +
                '}';
    }
}





