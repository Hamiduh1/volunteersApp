package com.example.volunteersApp.models;

import android.util.Log;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ServerValue;

import java.io.Serializable;
import java.util.Objects; // <<< Import for Objects.equals and Objects.hash

@IgnoreExtraProperties
public class JobPost implements Serializable {

    @Exclude
    private String jobPostId; // Key of the node in RTDB

    private String employerUid;
    private String organizationName;
    private String eventName;
    private String jobTitle;    // Used as "title" by the adapter
    private String description;
    private String date;        // Display date
    private String time;        // Display time
    private long eventTimestamp; // For sorting/logic related to the event's actual date/time
    private String location;    // Used by the adapter
    private String category;
    private String imageUrl;
    private long volunteersNeeded;
    private String status;      // e.g., "OPEN", "CLOSED", "FILLED"
    private String jobType;     // e.g., "One-time Event", "Ongoing", "Urgent"

    // Timestamps - these will store Long after conversion, or ServerValue.TIMESTAMP before write
    private Object createdAt;
    private Object updatedAt;
    private Object postedDate;

    // --- Fields based on JobPostDetailActivity ---
    private String skills;
    private String urgency;
    private String requirements;
    private String contactInfoProvidedByEmployer;
    private String hours;
    // --- End of new fields ---

    public JobPost() {
        // Default constructor required for calls to DataSnapshot.getValue(JobPost.class)
    }

    public void initializeTimestampsForCreation() {
        this.createdAt = ServerValue.TIMESTAMP;
        this.updatedAt = ServerValue.TIMESTAMP;
        this.postedDate = ServerValue.TIMESTAMP;
    }

    public void initializeTimestampForUpdate() {
        this.updatedAt = ServerValue.TIMESTAMP;
    }

    // --- Getters and Setters ---

    // @Exclude is already on the field, so getter doesn't strictly need it again
    // but DiffUtil might also look at getters.
    // For DiffUtil's areItemsTheSame, this ID is crucial.
    public String getJobPostId() {
        return jobPostId;
    }

    public void setJobPostId(String jobPostId) {
        this.jobPostId = jobPostId;
    }

    public String getEmployerUid() { return employerUid; }
    public void setEmployerUid(String employerUid) { this.employerUid = employerUid; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getTitle() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public long getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(long eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public String getLocationName() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getVolunteersNeeded() { return volunteersNeeded; }
    public void setVolunteersNeeded(long volunteersNeeded) { this.volunteersNeeded = volunteersNeeded; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public Object getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Object createdAtObject) {
        if (createdAtObject instanceof Long) {
            this.createdAt = (Long) createdAtObject;
        } else if (createdAtObject instanceof Number) {
            this.createdAt = ((Number) createdAtObject).longValue();
        } else if (createdAtObject == ServerValue.TIMESTAMP) {
            this.createdAt = ServerValue.TIMESTAMP;
        } else if (createdAtObject != null) {
            Log.w("JobPost", "Unexpected type for createdAt: " + createdAtObject.getClass().getName() + ". Value: " + createdAtObject);
            this.createdAt = null;
        } else {
            this.createdAt = null;
        }
    }

    @Exclude
    public Long getCreatedAtTimestamp() {
        if (createdAt instanceof Long) {
            return (Long) createdAt;
        }
        if (createdAt instanceof Number) {
            return ((Number) createdAt).longValue();
        }
        return null;
    }

    public Object getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Object updatedAtObject) {
        if (updatedAtObject instanceof Long) {
            this.updatedAt = (Long) updatedAtObject;
        } else if (updatedAtObject instanceof Number) {
            this.updatedAt = ((Number) updatedAtObject).longValue();
        } else if (updatedAtObject == ServerValue.TIMESTAMP) {
            this.updatedAt = ServerValue.TIMESTAMP;
        } else if (updatedAtObject != null) {
            Log.w("JobPost", "Unexpected type for updatedAt: " + updatedAtObject.getClass().getName() + ". Value: " + updatedAtObject);
            this.updatedAt = null;
        } else {
            this.updatedAt = null;
        }
    }

    @Exclude
    public Long getUpdatedAtTimestamp() {
        if (updatedAt instanceof Long) {
            return (Long) updatedAt;
        }
        if (updatedAt instanceof Number) {
            return ((Number) updatedAt).longValue();
        }
        return null;
    }

    public Object getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(Object postedDateObject) {
        if (postedDateObject instanceof Long) {
            this.postedDate = (Long) postedDateObject;
        } else if (postedDateObject instanceof Number) {
            this.postedDate = ((Number) postedDateObject).longValue();
        } else if (postedDateObject == ServerValue.TIMESTAMP) {
            this.postedDate = ServerValue.TIMESTAMP;
        } else if (postedDateObject != null) {
            Log.w("JobPost", "Unexpected type for postedDate: " + postedDateObject.getClass().getName() + ". Value: " + postedDateObject);
            this.postedDate = null;
        } else {
            this.postedDate = null;
        }
    }

    @Exclude
    public Long getPostedDateTimestamp() {
        if (postedDate instanceof Long) {
            return (Long) postedDate;
        }
        if (postedDate instanceof Number) {
            return ((Number) postedDate).longValue();
        }
        return null;
    }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getContactInfoProvidedByEmployer() { return contactInfoProvidedByEmployer; }
    public void setContactInfoProvidedByEmployer(String contactInfoProvidedByEmployer) { this.contactInfoProvidedByEmployer = contactInfoProvidedByEmployer; }

    public String getHours() { return hours; }
    public void setHours(String hours) { this.hours = hours; }

   // @Exclude
   // public String getTitle() {
     //   return this.jobTitle;
    //}

    // --- equals() and hashCode() for DiffUtil ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobPost jobPost = (JobPost) o;
        return eventTimestamp == jobPost.eventTimestamp &&
                volunteersNeeded == jobPost.volunteersNeeded &&
                Objects.equals(jobPostId, jobPost.jobPostId) && // Crucial for areItemsTheSame via areContentsTheSame if ID changes
                Objects.equals(employerUid, jobPost.employerUid) &&
                Objects.equals(organizationName, jobPost.organizationName) &&
                Objects.equals(eventName, jobPost.eventName) &&
                Objects.equals(jobTitle, jobPost.jobTitle) &&
                Objects.equals(description, jobPost.description) &&
                Objects.equals(date, jobPost.date) &&
                Objects.equals(time, jobPost.time) &&
                Objects.equals(location, jobPost.location) &&
                Objects.equals(category, jobPost.category) &&
                Objects.equals(imageUrl, jobPost.imageUrl) &&
                Objects.equals(status, jobPost.status) &&
                Objects.equals(jobType, jobPost.jobType) &&
                Objects.equals(createdAt, jobPost.createdAt) && // Compare the Object form for ServerValue.TIMESTAMP possibility
                Objects.equals(updatedAt, jobPost.updatedAt) && // Compare the Object form
                Objects.equals(postedDate, jobPost.postedDate) && // Compare the Object form
                Objects.equals(skills, jobPost.skills) &&
                Objects.equals(urgency, jobPost.urgency) &&
                Objects.equals(requirements, jobPost.requirements) &&
                Objects.equals(contactInfoProvidedByEmployer, jobPost.contactInfoProvidedByEmployer) &&
                Objects.equals(hours, jobPost.hours);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobPostId, employerUid, organizationName, eventName, jobTitle,
                description, date, time, eventTimestamp, location, category, imageUrl,
                volunteersNeeded, status, jobType, createdAt, updatedAt, postedDate,
                skills, urgency, requirements, contactInfoProvidedByEmployer, hours);
    }
}