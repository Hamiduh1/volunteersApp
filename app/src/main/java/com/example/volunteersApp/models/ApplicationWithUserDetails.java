package com.example.volunteersApp.models;

import java.util.Objects;

public class ApplicationWithUserDetails {

    // User-specific details
    private String uid;                 // User's UID (from User object or EventApplication's volunteerUid)
    private String name;                // User's name (from User object)
    private String email;               // User's email (from User object)
    private String profileImageUrl;     // User's profile image URL (from User object)

    // Encapsulated EventApplication object
    private EventApplication application;    // Holds all details of the application itself

    // No-argument constructor required for Firestore deserialization
    public ApplicationWithUserDetails() {
    }

    // Constructor to combine EventApplication and User details
    public ApplicationWithUserDetails(EventApplication application, User user) {
        this.application = application;
        if (user != null) {
            this.uid = user.getUid(); // Primary source for UID
            this.name = user.getTitle();
            this.email = user.getEmail(); // Assuming User model has getEmail()
            this.profileImageUrl = user.getProfileImageUrl(); // Assuming User model has getProfileImageUrl()
        } else if (application != null) {
            // Fallback to application details if user is null
            this.uid = application.getVolunteerUid(); // Fallback for UID
            this.name = application.getVolunteerName(); // Fallback for name
            this.email = null; // Email might not be stored directly in application, or add if it is
            this.profileImageUrl = null; // Profile image URL might not be in application
        } else {
            // Handle case where both are null (should ideally not happen)
            this.name = "N/A";
            this.email = "N/A";
            this.uid = null;
            this.profileImageUrl = null;
        }
    }

    // Alternative constructor if you already have individual user fields
    // This constructor assumes 'volunteerUid' is the primary identifier for the user part.
    public ApplicationWithUserDetails(EventApplication application, String volunteerUid, String volunteerName, String volunteerEmail, String volunteerProfileImageUrl) {
        this.application = application;
        this.uid = volunteerUid; // This is the volunteer's UID
        this.name = volunteerName;
        this.email = volunteerEmail;
        this.profileImageUrl = volunteerProfileImageUrl;
    }


    // --- Getters ---

    // User details getters
    public String getUid() {
        return uid;
    }

    public String getTitle() {
        // Provide a non-null name, falling back if necessary
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (application != null && application.getVolunteerName() != null && !application.getVolunteerName().isEmpty()) {
            return application.getVolunteerName(); // Fallback to name from application
        }
        return "N/A"; // Default placeholder
    }

    public String getEmail() {
        // Provide non-null email
        return (email != null && !email.isEmpty()) ? email : "N/A";
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    // Explicit getter for volunteerUid, which in this model is represented by 'uid'
    public String getVolunteerUid() {
        return uid;
    }

    // EventApplication object getter
    public EventApplication getApplication() {
        return application;
    }

    // --- Convenience Getters (delegating to the EventApplication object) ---
    public String getApplicationId() {
        return (application != null) ? application.getApplicationId() : null;
    }

    public String getJobPostId() {
        return (application != null) ? application.getJobPostId() : null;
    }

    public String getStatus() {
        return (application != null) ? application.getStatus() : null;
    }

    public com.google.firebase.Timestamp getApplicationTimestamp() {
        return (application != null) ? application.getApplicationTimestamp() : null;
    }

    // This specifically gets the name stored at the time of application, if different from current user name
    public String getApplicationVolunteerName() {
        return (application != null) ? application.getVolunteerName() : null;
    }


    // --- Setters ---
    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setApplication(EventApplication application) {
        this.application = application;
    }

    // --- Convenience methods for display (optional) ---
    public String getApplicantNameForDisplay() {
        String currentName = getTitle(); // Uses the getter which has fallbacks
        return (currentName != null && !currentName.isEmpty() && !currentName.equals("N/A")) ? currentName : "Applicant";
    }

    public String getApplicantEmailForDisplay() {
        return (email != null && !email.isEmpty() && !email.equals("N/A")) ? email : "Email not available";
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationWithUserDetails that = (ApplicationWithUserDetails) o;

        // Primary comparison based on EventApplication ID if available
        if (application != null && that.application != null &&
                application.getApplicationId() != null && that.application.getApplicationId() != null) {
            if (!application.getApplicationId().equals(that.application.getApplicationId())) {
                return false;
            }
            // If EventApplication IDs match, check content
            return Objects.equals(uid, that.uid) &&
                    Objects.equals(getTitle(), that.getTitle()) && // Use getter for consistent comparison
                    Objects.equals(getEmail(), that.getEmail()) && // Use getter
                    Objects.equals(profileImageUrl, that.profileImageUrl) &&
                    Objects.equals(application, that.application); // Deep equals for application
        }

        // Fallback comparison if application or its ID is null
        return Objects.equals(uid, that.uid) &&
                Objects.equals(getTitle(), that.getTitle()) &&
                Objects.equals(getEmail(), that.getEmail()) &&
                Objects.equals(profileImageUrl, that.profileImageUrl) &&
                Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        // Consistent with equals. Prioritize application ID if available.
        if (application != null && application.getApplicationId() != null) {
            return Objects.hash(application.getApplicationId(), uid, getTitle(), getEmail(), profileImageUrl, application);
        }
        return Objects.hash(uid, getTitle(), getEmail(), profileImageUrl, application);
    }

    @Override
    public String toString() {
        String applicationDetails = (application != null) ? application.toString() : "null";
        return "ApplicationWithUserDetails{" +
                "uid='" + uid + '\'' +
                ", name='" + getTitle() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", profileImageUrl='" + (profileImageUrl != null && !profileImageUrl.isEmpty() ? "exists" : "null/empty") + '\'' +
                ", application=" + applicationDetails +
                '}';
    }

    public void setApplicationId(String applicationId) {
        if (application != null) {
            application.setApplicationId(applicationId);
        }
    }

    public void setEventId(String eventId) {
        if (application != null) {
            application.setEventId(eventId);
        }
    }

    public void setStatus(String s) {
        if (application != null) {
            application.setStatus(s);
        }
    }
}
