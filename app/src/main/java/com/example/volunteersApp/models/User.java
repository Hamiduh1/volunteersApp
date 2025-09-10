package com.example.volunteersApp.models;

import androidx.annotation.Keep; // For ProGuard/R8
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Objects; // For Objects.equals and Objects.hash

@Keep // Keep the class and its members for ProGuard/R8
@IgnoreExtraProperties
public class User {

    private String uid;             // Unique User ID (consistent with Firebase Auth UID)
    private String name;            // User's full name
    private String email;           // User's email address
    private String profileImageUrl; // URL for the user's profile picture

    @Exclude
    private String applicationStatus; // Transient field, not stored in Firestore for this User object

    // Required empty public constructor for Firestore deserialization
    public User() {
    }

    // Constructor
    public User(String uid, String name, String email, String profileImageUrl) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    // --- Standard Getters ---
    public String getUid() {
        return uid;
    }

    // Getter for the 'name' field, which also serves as 'title' conceptually for some use cases
    public String getName() {
        return name;
    }

    // Alias getter if some parts of your UI expect getTitle()
    // Firestore will primarily look for getName() or a public 'name' field
    // or a setName() to map to the 'name' property.
    public String getTitle() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
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
                (applicationStatus != null ? ", applicationStatus='" + applicationStatus + '\'' : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        // Primary equality based on UID
        return Objects.equals(uid, user.uid);
    }

    @Override
    public int hashCode() {
        // Hash code based on UID
        return Objects.hash(uid);
    }
}




