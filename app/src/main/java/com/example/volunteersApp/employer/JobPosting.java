package com.example.volunteersApp.employer;

// Firestore specific imports
import com.google.firebase.Timestamp; // Use Firestore's Timestamp
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects; // For equals and hashCode

@IgnoreExtraProperties // Tells Firestore to ignore unknown properties during deserialization
public class JobPosting {

    // --- Fields ---

    @DocumentId // Firestore will automatically populate this field with the document's ID
    private String postingId; // This will be the document ID from Firestore

    private String employerUid;
    private String organizationName;
    private String eventName;
    private String jobTitle; // Renamed from 'title' to 'jobTitle' for consistency with common usage
    private String description;
    private String date; // Consider using Date or Timestamp type if you need to query/sort by date/time
    private String time; // Similar to 'date', consider a more structured type
    private String location;
    private String category; // Renamed from 'jobType' for broader meaning, can represent category/type
    private int volunteersNeeded;
    private String status; // e.g., "Active", "Filled", "Expired"

    @ServerTimestamp // Firestore will automatically set this to the server's timestamp on write/update
    private Timestamp timestamp; // Use com.google.firebase.Timestamp for direct Firestore mapping

    // --- Constructors ---

    // No-argument constructor REQUIRED for Firestore data mapping
    public JobPosting() {
    }

    public JobPosting(String employerUid, String organizationName, String eventName, String jobTitle,
                      String description, String date, String time, String location,
                      String category, int volunteersNeeded, String status) {
        this.employerUid = employerUid;
        this.organizationName = organizationName;
        this.eventName = eventName;
        this.jobTitle = jobTitle;
        this.description = description;
        this.date = date;
        this.time = time;
        this.location = location;
        this.category = category;
        this.volunteersNeeded = volunteersNeeded;
        this.status = status;
        // 'timestamp' will be set by @ServerTimestamp when saved to Firestore for the first time
        // 'postingId' will be set by @DocumentId when read from Firestore
    }

    // --- Getters and Setters (Required by Firestore for private fields) ---

    public String getPostingId() {
        return postingId;
    }

    public void setPostingId(String postingId) {
        this.postingId = postingId;
    }

    public String getEmployerUid() {
        return employerUid;
    }

    public void setEmployerUid(String employerUid) {
        this.employerUid = employerUid;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getTitle() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getLocationName() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getVolunteersNeeded() {
        return volunteersNeeded;
    }

    public void setVolunteersNeeded(int volunteersNeeded) {
        this.volunteersNeeded = volunteersNeeded;
    }

    // Getter for the Firestore Timestamp object
    public Timestamp getTimestamp() {
        return timestamp;
    }

    // Setter for the Firestore Timestamp object
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    // --- Helper Methods ---

    /**
     * Converts the Firestore Timestamp to a Long (milliseconds since epoch).
     * This is the method your sorting comparator in MyPostedJobsActivity expects.
     *
     * @return Timestamp as Long (milliseconds since epoch), or null if timestamp is null.
     */
    @Exclude // Exclude this from Firestore serialization as it's a derived value
    public Long getTimestampLong() {
        if (this.timestamp != null) {
            return this.timestamp.toDate().getTime(); // Converts Firestore Timestamp to java.util.Date, then to milliseconds
        }
        return null;
    }

    /**
     * Creates a Map representation for specific update operations.
     * When doing manual map-based updates, use FieldValue.serverTimestamp() for timestamp fields.
     *
     * @return A map of the job posting data.
     */
    @Exclude // Exclude this method from being serialized to Firestore
    public Map<String, Object> toMapForUpdate() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("employerUid", employerUid);
        result.put("organizationName", organizationName);
        result.put("eventName", eventName);
        result.put("jobTitle", jobTitle);
        result.put("description", description);
        result.put("date", date);
        result.put("time", time);
        result.put("location", location);
        result.put("category", category);
        result.put("volunteersNeeded", volunteersNeeded);
        result.put("status", status);
        // For server timestamp during manual map updates, you would add:
        // result.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        // However, if simply saving/updating the whole POJO, @ServerTimestamp on the field handles it.
        return result;
    }

    // --- equals() and hashCode() for object comparison ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobPosting that = (JobPosting) o;
        // Use postingId for equality if available (as it's the document ID from Firestore)
        if (postingId != null && that.postingId != null) {
            return postingId.equals(that.postingId);
        }
        // Fallback for objects not yet having a Firestore ID or for content comparison
        return volunteersNeeded == that.volunteersNeeded &&
                Objects.equals(employerUid, that.employerUid) &&
                Objects.equals(organizationName, that.organizationName) &&
                Objects.equals(eventName, that.eventName) &&
                Objects.equals(jobTitle, that.jobTitle) &&
                Objects.equals(description, that.description) &&
                Objects.equals(date, that.date) &&
                Objects.equals(time, that.time) &&
                Objects.equals(location, that.location) &&
                Objects.equals(category, that.category) &&
                Objects.equals(status, that.status) &&
                Objects.equals(timestamp, that.timestamp); // Compare Firestore Timestamp objects
    }

    @Override
    public int hashCode() {
        // Use postingId for hashcode if available
        if (postingId != null) {
            return Objects.hash(postingId);
        }
        return Objects.hash(employerUid, organizationName, eventName, jobTitle, description,
                date, time, location, category, volunteersNeeded, status, timestamp);
    }

    @Override
    public String toString() {
        return "JobPosting{" +
                "postingId='" + postingId + '\'' +
                ", employerUid='" + employerUid + '\'' +
                ", organizationName='" + organizationName + '\'' +
                ", eventName='" + eventName + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", description='" + (description != null ? description.substring(0, Math.min(description.length(), 30)) + "..." : "N/A") + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", location='" + location + '\'' +
                ", category='" + category + '\'' +
                ", volunteersNeeded=" + volunteersNeeded +
                ", status='" + status + '\'' +
                ", timestamp=" + (timestamp != null ? timestamp.toDate().toString() : "N/A") + // Display Date from Timestamp
                '}';
    }
}