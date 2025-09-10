package com.example.volunteersApp.models; // Or your specific package name

import com.google.firebase.firestore.ServerTimestamp; // If using Firestore timestamps
import java.util.Date;
import java.util.List;
import java.io.Serializable;


// Add @IgnoreExtraProperties if using Firebase Realtime Database and you want to ignore unknown fields
// import com.google.firebase.database.IgnoreExtraProperties;
// @IgnoreExtraProperties
public class Organization implements Serializable { // Implement Serializable if passed via Intent


    private String uid; // Unique ID, typically Firebase Auth UID for the organization's admin/ic_account_vector.xml
    private String organizationName;
    private String organizationType; // e.g., "Non-profit", "Community Group", "Charity"
    private String description;
    private String email; // Contact email
    private String phoneNumber; // Contact phone
    private String website;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince; // State or Province
    private String postalCode;
    private String country;
    private String profileImageUrl; // URL for the organization's logo or profile picture
    private String headerImageUrl;  // URL for a larger banner/header image for their profile
    private List<String> focusAreas; // e.g., ["Environment", "Education", "Healthcare"]
    private boolean isVerified;      // If you have a verification system
    private String contactPersonName;

    @ServerTimestamp // For Firestore: automatically populates with server timestamp on creation
    private Date dateRegistered;

    @ServerTimestamp // For Firestore: automatically populates with server timestamp on last update
    private Date lastUpdated;

    // Required empty public constructor for Firebase Firestore/Realtime Database
    public Organization() {
    }

    // Constructor with essential fields (optional, but can be helpful)
    public Organization(String uid, String organizationName, String email) {
        this.uid = uid;
        this.organizationName = organizationName;
        this.email = email;
        // Consider initializing focusAreas to an empty list
        // this.focusAreas = new ArrayList<>();
    }

    // --- Getters and Setters for all fields ---

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getOrganizationType() {
        return organizationType;
    }

    public void setOrganizationType(String organizationType) {
        this.organizationType = organizationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStateProvince() {
        return stateProvince;
    }

    public void setStateProvince(String stateProvince) {
        this.stateProvince = stateProvince;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getHeaderImageUrl() {
        return headerImageUrl;
    }

    public void setHeaderImageUrl(String headerImageUrl) {
        this.headerImageUrl = headerImageUrl;
    }

    public List<String> getFocusAreas() {
        return focusAreas;
    }

    public void setFocusAreas(List<String> focusAreas) {
        this.focusAreas = focusAreas;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getContactPersonName() {
        return contactPersonName;
    }

    public void setContactPersonName(String contactPersonName) {
        this.contactPersonName = contactPersonName;
    }

    public Date getDateRegistered() {
        return dateRegistered;
    }

    public void setDateRegistered(Date dateRegistered) {
        this.dateRegistered = dateRegistered;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // You might also want to add:
    // - toString() method (for debugging)
    // - equals() and hashCode() methods (if you plan to store these in Sets or use them as Map keys)
}