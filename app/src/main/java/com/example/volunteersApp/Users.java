package com.example.volunteersApp;

import com.google.firebase.firestore.PropertyName;

/**
 * Represents a User object.
 * This class is designed to be used as a POJO (Plain Old Java Object)
 * for serializing and deserializing data with Cloud Firestore.
 */
public class Users {

    // It's generally better practice to make fields private and use public getters/setters.
    // However, for direct mapping with Firestore using toObject(), public fields can work
    // if you don't need complex logic in getters/setters.
    // For consistency with typical Java POJOs and better encapsulation,
    // let's make them private and ensure public getters/setters.

    private String email; // Changed from 'mail' for clarity and common Firestore usage
    private String phone;
    private String username; // Changed from 'name' to 'username' for clarity and common Firestore usage
    private String profilePictureUrl; // Changed from 'url' for clarity
    private int wallet;

    // Default constructor is required for Firestore's toObject() method.
    public Users() {
        // Firestore needs a public no-argument constructor
    }

    // Constructor for creating a Users object with initial data
    public Users(String email, String phone, String username, String profilePictureUrl, int wallet) {
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.profilePictureUrl = profilePictureUrl;
        this.wallet = wallet;
    }

    // --- Getters ---
    // Firestore uses getters (or public fields) to serialize data.
    // If your field names in Firestore are different from your getter names (e.g., field "user_email", getter "getEmail"),
    // you can use @PropertyName("user_email") annotation on the getter.

    @PropertyName("email") // Maps to 'email' field in Firestore
    public String getEmail() {
        return email;
    }

    @PropertyName("phone") // Maps to 'phone' field in Firestore
    public String getPhone() {
        return phone;
    }

    @PropertyName("username") // Maps to 'username' field in Firestore
    public String getUsername() {
        return username;
    }

    @PropertyName("profilePictureUrl") // Maps to 'profilePictureUrl' field in Firestore
    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    @PropertyName("wallet") // Maps to 'wallet' field in Firestore
    public int getWallet() {
        return wallet;
    }

    // --- Setters ---
    // Firestore uses setters (or public fields) to deserialize data when calling toObject().
    // If your field names in Firestore are different, use @PropertyName on the setter as well.

    @PropertyName("email")
    public void setEmail(String email) {
        this.email = email;
    }

    @PropertyName("phone")
    public void setPhone(String phone) {
        this.phone = phone;
    }

    @PropertyName("username")
    public void setUsername(String username) {
        this.username = username;
    }

    @PropertyName("profilePictureUrl")
    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    @PropertyName("wallet")
    public void setWallet(int wallet) {
        this.wallet = wallet;
    }

    // The methods setWallet() (with no arguments) and seturl() (with no arguments and lowercase 'u')
    // are not standard setters in the Java Bean convention and might not be automatically
    // used by Firestore unless you explicitly call them after creating an object.
    // It's better to handle default values during object creation or through dedicated logic
    // elsewhere rather than in parameter-less setters if they are meant to be default initializers.

    // If you need a method to set a default wallet value, it's better to do it
    // during construction or through a specific initialization method.
    // For example:
    // public void initializeDefaultWallet() { this.wallet = 50; }

    // Similarly for the URL:
    // public void initializeDefaultProfilePictureUrl() {
    //    this.profilePictureUrl = "https://mir-s3-cdn-cf.behance.net/project_modules/1400/a6938438650505.598fa5ee0cdf7.jpg";
    // }
}