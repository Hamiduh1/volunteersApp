/** Purpose: This class represents a volunteer's application to
 *  participate in a specific event.
 *  It acts as a "join" or "link" between a User (the volunteer) and an Event.
 **/

package com.example.volunteersApp.models;

import android.service.autofill.Validators;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

@IgnoreExtraProperties
public class EventApplication {

    @DocumentId
    private String applicationId;

    private String eventId;
    private String eventTitle;
    private String organizerName;
    private String jobPostId;
    private String volunteerUid;
    private String organizerUid;
    private String volunteerName;
    private String volunteerProfileImageUrl;
    private String volunteerEmail;         // Added
    private String notes;                  // Added (notes from volunteer)
    private String reasonForRejection;     // Added (reason set by organizer)

    @ServerTimestamp
    private Timestamp applicationTimestamp; // This is 'appliedAt'

    private String status;

    @ServerTimestamp
    private Timestamp eventCreatedAt;

    private String volunteerUid_eventId;


    // No-argument constructor for Firestore
    public EventApplication() {
    }

    // Full constructor including all fields
    public EventApplication(String eventId, String eventTitle, String organizerName,
                            String volunteerUid, String organizerUid, String volunteerName,
                            String volunteerProfileImageUrl, String volunteerEmail,
                            String notes, String reasonForRejection,
                            String status, Timestamp eventCreatedAt, Timestamp applicationTimestamp) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.organizerName = organizerName;
        this.volunteerUid = volunteerUid;
        this.organizerUid = organizerUid;
        this.volunteerName = volunteerName;
        this.volunteerProfileImageUrl = volunteerProfileImageUrl;
        this.volunteerEmail = volunteerEmail;
        this.notes = notes;
        this.reasonForRejection = reasonForRejection;
        this.status = status;
        this.eventCreatedAt = eventCreatedAt;
        this.applicationTimestamp = applicationTimestamp; // Explicitly set if passed, otherwise @ServerTimestamp handles it

        if (volunteerUid != null && eventId != null) {
            this.volunteerUid_eventId = volunteerUid + "_" + eventId;
        }
    }

    public String getJobPostId() { return jobPostId; }
    public void setJobPostId(String jobPostId) { this.jobPostId = jobPostId; }

    // --- Getters ---
    public String getApplicationId() { return applicationId; }
    public String getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public String getOrganizerName() { return organizerName; }
    public String getVolunteerUid() { return volunteerUid; }
    public String getOrganizerUid() { return organizerUid; }
    public String getVolunteerName() { return volunteerName; }
    public String getVolunteerProfileImageUrl() { return volunteerProfileImageUrl; }
    public String getVolunteerEmail() { return volunteerEmail; } // Getter for volunteerEmail
    public String getNotes() { return notes; }                   // Getter for notes
    public String getReasonForRejection() { return reasonForRejection; } // Getter for reasonForRejection
    public Timestamp getApplicationTimestamp() { return applicationTimestamp; }
    public String getStatus() { return status; }
    public Timestamp getEventCreatedAt() { return eventCreatedAt; }
    public String getVolunteerUid_eventId() { return volunteerUid_eventId; }

    // --- Setters ---
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }
    public void setVolunteerUid(String volunteerUid) { this.volunteerUid = volunteerUid; }
    public void setOrganizerUid(String organizerUid) { this.organizerUid = organizerUid; }
    public void setVolunteerName(String volunteerName) { this.volunteerName = volunteerName; }
    public void setVolunteerProfileImageUrl(String volunteerProfileImageUrl) { this.volunteerProfileImageUrl = volunteerProfileImageUrl; }
    public void setVolunteerEmail(String volunteerEmail) { this.volunteerEmail = volunteerEmail; } // Setter for volunteerEmail
    public void setNotes(String notes) { this.notes = notes; }                   // Setter for notes
    public void setReasonForRejection(String reasonForRejection) { this.reasonForRejection = reasonForRejection; } // Setter for reasonForRejection
    public void setApplicationTimestamp(Timestamp applicationTimestamp) { this.applicationTimestamp = applicationTimestamp; }
    public void setStatus(String status) { this.status = status; }
    public void setEventCreatedAt(Timestamp eventCreatedAt) { this.eventCreatedAt = eventCreatedAt; }
    public void setVolunteerUid_eventId(String volunteerUid_eventId) { this.volunteerUid_eventId = volunteerUid_eventId; }


    // --- Helper Methods ---

    @Exclude
    public String getFormattedApplicationDate() {
        if (applicationTimestamp == null) {
            return "N/A";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(applicationTimestamp.toDate());
        } catch (Exception e) {
            return "Date Error";
        }
    }

    @Exclude
    public String getFormattedEventCreationDate() {
        if (eventCreatedAt == null) {
            return "N/A";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            return sdf.format(eventCreatedAt.toDate());
        } catch (Exception e) {
            return "Date Error";
        }
    }

    // --- equals, hashCode, toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventApplication that = (EventApplication) o;
        if (applicationId != null && that.applicationId != null) {
            return applicationId.equals(that.applicationId);
        }
        return Objects.equals(eventId, that.eventId) &&
                Objects.equals(eventTitle, that.eventTitle) &&
                Objects.equals(organizerName, that.organizerName) &&
                Objects.equals(volunteerUid, that.volunteerUid) &&
                Objects.equals(organizerUid, that.organizerUid) &&
                Objects.equals(volunteerName, that.volunteerName) &&
                Objects.equals(volunteerProfileImageUrl, that.volunteerProfileImageUrl) &&
                Objects.equals(volunteerEmail, that.volunteerEmail) && // Included
                Objects.equals(notes, that.notes) &&                   // Included
                Objects.equals(reasonForRejection, that.reasonForRejection) && // Included
                Objects.equals(applicationTimestamp, that.applicationTimestamp) &&
                Objects.equals(status, that.status) &&
                Objects.equals(eventCreatedAt, that.eventCreatedAt) &&
                Objects.equals(volunteerUid_eventId, that.volunteerUid_eventId);
    }

    @Override
    public int hashCode() {
        if (applicationId != null) {
            return Objects.hash(applicationId);
        }
        return Objects.hash(eventId, eventTitle, organizerName, volunteerUid, organizerUid,
                volunteerName, volunteerProfileImageUrl, volunteerEmail, notes, reasonForRejection, // Included
                applicationTimestamp, status, eventCreatedAt, volunteerUid_eventId);
    }

    @Override
    public String toString() {
        return "EventApplication{" +
                "applicationId='" + applicationId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", eventTitle='" + eventTitle + '\'' +
                ", organizerName='" + organizerName + '\'' +
                ", volunteerUid='" + volunteerUid + '\'' +
                ", organizerUid='" + organizerUid + '\'' +
                ", volunteerName='" + volunteerName + '\'' +
                ", volunteerProfileImageUrl='" + volunteerProfileImageUrl + '\'' +
                ", volunteerEmail='" + volunteerEmail + '\'' + // Included
                ", notes='" + notes + '\'' +                   // Included
                ", reasonForRejection='" + reasonForRejection + '\'' + // Included
                ", applicationTimestamp=" + (applicationTimestamp != null ? getFormattedApplicationDate() : "null") +
                ", status='" + status + '\'' +
                ", eventCreatedAt=" + (eventCreatedAt != null ? getFormattedEventCreationDate() : "null") +
                ", volunteerUid_eventId='" + volunteerUid_eventId + '\'' +
                '}';
    }
}
