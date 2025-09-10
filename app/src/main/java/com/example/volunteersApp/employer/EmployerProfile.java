package com.example.volunteersApp.employer;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@IgnoreExtraProperties
public class EmployerProfile {

    private String organizationName;
    private String contactEmail;
    private String description;
    private String location; // Field name remains 'location'
    private String profileImageUrl;

    @ServerTimestamp
    private Date lastUpdatedTimestamp;

    @Exclude
    private String documentId;

    // --- Role Change Request Fields (Added based on your fragment code) ---
    private Boolean interestedInBecomingOrganizer;
    private Boolean interestedInBecomingVolunteer;
    private String roleChangeRequestStatus; // e.g., "PENDING_ORGANIZER", "PENDING_VOLUNTEER", "APPROVED", "REJECTED"


    public EmployerProfile() {
        // Firestore needs this
    }

    public EmployerProfile(String organizationName, String contactEmail, String description, String location, String profileImageUrl) {
        this.organizationName = organizationName;
        this.contactEmail = contactEmail;
        this.description = description;
        this.location = location;
        this.profileImageUrl = profileImageUrl;
        // Role change fields can be initialized to false or null
        this.interestedInBecomingOrganizer = false;
        this.interestedInBecomingVolunteer = false;
        this.roleChangeRequestStatus = null;
    }

    // --- Getters and Setters ---

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // RENAMED GETTER for better Kotlin interop
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public Date getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp(Date lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    @Exclude
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    // --- Getters and Setters for Role Change Request Fields ---

    public Boolean getInterestedInBecomingOrganizer() {
        return interestedInBecomingOrganizer;
    }

    public void setInterestedInBecomingOrganizer(Boolean interestedInBecomingOrganizer) {
        this.interestedInBecomingOrganizer = interestedInBecomingOrganizer;
    }

    public Boolean getInterestedInBecomingVolunteer() {
        return interestedInBecomingVolunteer;
    }

    public void setInterestedInBecomingVolunteer(Boolean interestedInBecomingVolunteer) {
        this.interestedInBecomingVolunteer = interestedInBecomingVolunteer;
    }

    public String getRoleChangeRequestStatus() {
        return roleChangeRequestStatus;
    }

    public void setRoleChangeRequestStatus(String roleChangeRequestStatus) {
        this.roleChangeRequestStatus = roleChangeRequestStatus;
    }


    // --- Helper Methods ---
    @Exclude
    public Map<String, Object> toMapForUpdate() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("organizationName", organizationName);
        result.put("contactEmail", contactEmail);
        result.put("description", description);
        result.put("location", location);
        result.put("profileImageUrl", profileImageUrl);
        // Do not include role change fields here unless you have a specific flow
        // to update them via this map. They are usually updated via specific actions.
        // result.put("lastUpdatedTimestamp", FieldValue.serverTimestamp()); // For manual updates
        return result;
    }

    @Override
    public String toString() {
        return "EmployerProfile{" +
                "documentId='" + documentId + '\'' +
                ", organizationName='" + organizationName + '\'' +
                ", contactEmail='" + contactEmail + '\'' +
                ", description='" + (description != null ? description : "N/A") + '\'' +
                ", location='" + (location != null ? location : "N/A") + '\'' +
                ", profileImageUrl='" + (profileImageUrl != null ? profileImageUrl : "N/A") + '\'' +
                ", lastUpdatedTimestamp=" + (lastUpdatedTimestamp != null ? lastUpdatedTimestamp.toString() : "N/A") +
                ", interestedInBecomingOrganizer=" + interestedInBecomingOrganizer +
                ", interestedInBecomingVolunteer=" + interestedInBecomingVolunteer +
                ", roleChangeRequestStatus='" + roleChangeRequestStatus + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployerProfile that = (EmployerProfile) o;
        if (documentId != null && that.documentId != null) {
            return documentId.equals(that.documentId);
        }
        return Objects.equals(organizationName, that.organizationName) &&
                Objects.equals(contactEmail, that.contactEmail) &&
                Objects.equals(description, that.description) &&
                Objects.equals(location, that.location) &&
                Objects.equals(profileImageUrl, that.profileImageUrl) &&
                Objects.equals(lastUpdatedTimestamp, that.lastUpdatedTimestamp) &&
                Objects.equals(interestedInBecomingOrganizer, that.interestedInBecomingOrganizer) &&
                Objects.equals(interestedInBecomingVolunteer, that.interestedInBecomingVolunteer) &&
                Objects.equals(roleChangeRequestStatus, that.roleChangeRequestStatus);
    }

    @Override
    public int hashCode() {
        if (documentId != null) {
            return Objects.hash(documentId);
        }
        return Objects.hash(organizationName, contactEmail, description, location, profileImageUrl, lastUpdatedTimestamp,
                interestedInBecomingOrganizer, interestedInBecomingVolunteer, roleChangeRequestStatus);
    }
}
