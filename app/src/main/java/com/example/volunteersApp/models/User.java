package com.example.volunteersApp.models;

import androidx.annotation.Keep;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import java.util.Objects;

@Keep
@IgnoreExtraProperties
public class User {

    private String uid;
    private String name;
    private String email;
    private String profileImageUrl;
    private String phoneNumber; // ADDED phoneNumber field

    @Exclude
    private String applicationStatus; // Transient field, not stored in Firestore for this User object

    public User() {
        // Required empty public constructor for Firestore deserialization
    }

    // Constructor updated to include phoneNumber
    public User(String uid, String name, String email, String profileImageUrl, String phoneNumber) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.phoneNumber = phoneNumber; // Initialize phoneNumber
    }

    // Constructor without phoneNumber for backward compatibility or if phone is optional
    public User(String uid, String name, String email, String profileImageUrl) {
        this(uid, name, email, profileImageUrl, null); // Call main constructor with null phone
    }


    // --- Standard Getters ---
    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getTitle() { // Alias getter
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getPhoneNumber() { // ADDED getter for phoneNumber
        return phoneNumber;
    }

    @Exclude
    public String getApplicationStatus() {
        return applicationStatus;
    }

    // --- Alias Getters (Excluded from Firestore) ---
    @Exclude
    public String getUserId() {
        return uid;
    }

    @Exclude
    public String getFullName() {
        return name;
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

    public void setPhoneNumber(String phoneNumber) { // ADDED setter for phoneNumber
        this.phoneNumber = phoneNumber;
    }

    @Exclude
    public void setApplicationStatus(String applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    // --- Alias Setters (Excluded from Firestore) ---
    @Exclude
    public void setUserId(String userId) {
        this.uid = userId;
    }

    @Exclude
    public void setFullName(String fullName) {
        this.name = fullName;
    }

    @Override
    public String toString() {
        return "User{" +
                "uid='" + uid + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", profileImageUrl='" + (profileImageUrl != null && !profileImageUrl.isEmpty() ? "present" : "null_or_empty") + '\'' +
                ", phoneNumber='" + (phoneNumber != null ? phoneNumber : "null") + '\'' + // ADDED to toString
                (applicationStatus != null ? ", applicationStatus='" + applicationStatus + '\'' : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        // For equality, primarily rely on UID if present.
        // If UIDs are not the primary concern for a specific use case,
        // you might need to adjust this logic.
        return Objects.equals(uid, user.uid);
        // If UID is null for both (e.g., new objects not yet persisted),
        // you might fall back to content comparison:
        // if (uid == null && user.uid == null) {
        //     return Objects.equals(name, user.name) &&
        //            Objects.equals(email, user.email) &&
        //            Objects.equals(profileImageUrl, user.profileImageUrl) &&
        //            Objects.equals(phoneNumber, user.phoneNumber);
        // }
        // return false; // If one UID is null and the other isn't, they are different
    }

    @Override
    public int hashCode() {
        // Hash code based primarily on UID for consistency with equals.
        return Objects.hash(uid);
        // If falling back to content for null UIDs:
        // return Objects.hash(uid, name, email, profileImageUrl, phoneNumber);
    }
}
