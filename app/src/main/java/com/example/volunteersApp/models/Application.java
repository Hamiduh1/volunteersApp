package com.example.volunteersApp.models;

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
public class Application {

    @DocumentId
    private String applicationId;      // Unique ID for this application document, auto-populated by Firestore

    private String jobPostId;          // ID of the job/event this application is for
    private String jobTitle;           // Title of the job/event
    private String organizationName;   // Name of the organization posting the job/event
    private String volunteerUid;       // UID of the volunteer who applied
    private String employerUid;        // UID of the employer/organization contact
    private String volunteerName;      // Snapshot of the volunteer's name at the time of application

    @ServerTimestamp
    private Timestamp applicationTimestamp;

    private String status;             // e.g., "pending", "accepted", "rejected"
    private Timestamp jobCreatedAt;    // Timestamp of when the original job/event was created

    // Composite key, can be useful, but ensure it's consistently set if used for primary logic.
    // For DiffUtil and general equality, applicationId is usually the definitive key once persisted.
    private String volunteerUid_jobPostId;

    public Application() {
        // Firestore requires a no-argument constructor
    }

    // Full constructor for manual object creation.
    // applicationId is set by Firestore. applicationTimestamp is set by @ServerTimestamp.
    public Application(String jobPostId, String jobTitle, String organizationName,
                       String volunteerUid, String employerUid, String volunteerName,
                       String status, Timestamp jobCreatedAt) { // Removed volunteerUid_jobPostId for clarity if it's derived
        this.jobPostId = jobPostId;
        this.jobTitle = jobTitle;
        this.organizationName = organizationName;
        this.volunteerUid = volunteerUid;
        this.employerUid = employerUid;
        this.volunteerName = volunteerName;
        this.status = status;
        this.jobCreatedAt = jobCreatedAt;
        // Optionally, construct volunteerUid_jobPostId here if needed and not set separately
        if (volunteerUid != null && jobPostId != null) {
            this.volunteerUid_jobPostId = volunteerUid + "_" + jobPostId;
        }
    }

    // --- Getters ---
    public String getApplicationId() { return applicationId; }
    public String getJobPostId() { return jobPostId; }
    public String getJobTitle() { return jobTitle; }
    public String getOrganizationName() { return organizationName; }
    public String getVolunteerUid() { return volunteerUid; }
    public String getEmployerUid() { return employerUid; }
    public String getVolunteerName() { return volunteerName; }
    public Timestamp getApplicationTimestamp() { return applicationTimestamp; }
    public String getStatus() { return status; }
    public Timestamp getJobCreatedAt() { return jobCreatedAt; }
    public String getVolunteerUid_jobPostId() { return volunteerUid_jobPostId; }

    // --- Setters ---
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public void setJobPostId(String jobPostId) { this.jobPostId = jobPostId; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public void setVolunteerUid(String volunteerUid) { this.volunteerUid = volunteerUid; }
    public void setEmployerUid(String employerUid) { this.employerUid = employerUid; }
    public void setVolunteerName(String volunteerName) { this.volunteerName = volunteerName; }
    public void setApplicationTimestamp(Timestamp applicationTimestamp) { this.applicationTimestamp = applicationTimestamp; }
    public void setStatus(String status) { this.status = status; }
    public void setJobCreatedAt(Timestamp jobCreatedAt) { this.jobCreatedAt = jobCreatedAt; }
    public void setVolunteerUid_jobPostId(String volunteerUid_jobPostId) { this.volunteerUid_jobPostId = volunteerUid_jobPostId; }

    @Exclude
    public String getFormattedApplicationDate() {
        if (applicationTimestamp == null) {
            return "N/A";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(applicationTimestamp.toDate());
        } catch (Exception e) {
            // Consider logging the error: Log.e("ApplicationModel", "Error formatting date", e);
            return "Date Error";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Application that = (Application) o;

        // If applicationId is available, it's the most reliable unique identifier.
        if (applicationId != null && that.applicationId != null) {
            return applicationId.equals(that.applicationId);
        }
        // If one has an ID and the other doesn't, they are not equal (unless comparing pre/post persistence)
        if ((applicationId != null && that.applicationId == null) || (applicationId == null && that.applicationId != null)) {
            // For DiffUtil, you might treat an object without an ID and one with an ID as different items.
            // Or, if an ID is generated upon saving, they might be the "same" item pre/post save.
            // The current fallback is content-based if IDs are missing on both.
        }

        // Fallback to content-based equality if IDs are not available on one or both.
        // This is important for comparing objects before they are saved to Firestore (when applicationId would be null)
        // or for DiffUtil's areContentsTheSame when areItemsTheSame might have returned true based on a temporary local ID.
        return Objects.equals(jobPostId, that.jobPostId) &&
                Objects.equals(volunteerUid, that.volunteerUid) &&
                Objects.equals(jobTitle, that.jobTitle) &&
                Objects.equals(organizationName, that.organizationName) &&
                Objects.equals(employerUid, that.employerUid) &&
                Objects.equals(volunteerName, that.volunteerName) &&
                Objects.equals(applicationTimestamp, that.applicationTimestamp) && // Timestamps can be tricky due to precision
                Objects.equals(status, that.status) &&
                Objects.equals(jobCreatedAt, that.jobCreatedAt) &&
                Objects.equals(volunteerUid_jobPostId, that.volunteerUid_jobPostId);
    }

    @Override
    public int hashCode() {
        // If applicationId is available, use it for a consistent hash code.
        if (applicationId != null) {
            return Objects.hash(applicationId);
        }
        // Fallback to content-based hash code if ID is not available.
        return Objects.hash(jobPostId, jobTitle, organizationName, volunteerUid, employerUid,
                volunteerName, applicationTimestamp, status, jobCreatedAt, volunteerUid_jobPostId);
    }

    @Override
    public String toString() {
        return "Application{" +
                "applicationId='" + applicationId + '\'' +
                ", jobPostId='" + jobPostId + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", organizationName='" + organizationName + '\'' +
                ", volunteerUid='" + volunteerUid + '\'' +
                ", employerUid='" + employerUid + '\'' +
                ", volunteerName='" + volunteerName + '\'' +
                ", applicationTimestamp=" + (applicationTimestamp != null ? getFormattedApplicationDate() : "null") +
                ", status='" + status + '\'' +
                ", jobCreatedAt=" + (jobCreatedAt != null ? jobCreatedAt.toDate().toString() : "null") + // Or format this too
                ", volunteerUid_jobPostId='" + volunteerUid_jobPostId + '\'' +
                '}';
    }

    public void setEventId(String eventId) {
        this.jobPostId = eventId;

    }
}
