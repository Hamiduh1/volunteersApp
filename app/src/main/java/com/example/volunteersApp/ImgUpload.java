package com.example.volunteersApp; // Ensure this matches your package structure

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude; // To exclude fields from being saved
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date; // Required for @ServerTimestamp

public class ImgUpload {

    @DocumentId // Firestore will automatically populate this with the document ID
    private String documentId;

    private String name;        // Name associated with the image (e.g., event name or user-defined)
    private String imageUrl;    // The downloadable URL of the image from Firebase Storage
    private String imagePathInStorage; // Full path in Firebase Storage (for deletion)

    private String uploaderId;  // UID of the user who uploaded the image
    private String eventId;     // Optional: If this image is specifically for an event

    @ServerTimestamp // Firestore will set this to the server's timestamp when the document is written
    private Date uploadedAt;

    // Default constructor (no-argument) - REQUIRED for Firebase deserialization
    public ImgUpload() {
        // Firebase needs this to create objects when retrieving data.
    }

    // Constructor to create new ImgUpload objects (example, adjust as needed)
    public ImgUpload(String name, String imageUrl, String imagePathInStorage, String uploaderId, String eventId) {
        setName(name); // Use setter to apply trimming logic
        this.imageUrl = imageUrl;
        this.imagePathInStorage = imagePathInStorage;
        this.uploaderId = uploaderId;
        this.eventId = eventId;
        // 'documentId' and 'uploadedAt' will be handled by Firestore
    }

    // --- Getters ---

    // Use @Exclude if you have a getter for a field you don't want to store directly in Firestore
    // (e.g., if it's derived or only for client-side use), though documentId is fine to store.
    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getImagePathInStorage() {
        return imagePathInStorage;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getEventId() {
        return eventId;
    }

    public Date getUploadedAt() {
        return uploadedAt;
    }

    // --- Setters ---

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public void setName(String name) {
        if (name != null && name.trim().isEmpty()) {
            this.name = "No Name"; // Default if name is provided but empty after trimming
        } else if (name == null) {
            this.name = "No Name"; // Default if name is null
        } else {
            this.name = name.trim(); // Trim and assign
        }
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setImagePathInStorage(String imagePathInStorage) {
        this.imagePathInStorage = imagePathInStorage;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setUploadedAt(Date uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    // Optional: toString() method for easier debugging
    @Override
    public String toString() {
        return "ImgUpload{" +
                "documentId='" + documentId + '\'' +
                ", name='" + name + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", imagePathInStorage='" + imagePathInStorage + '\'' +
                ", uploaderId='" + uploaderId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", uploadedAt=" + uploadedAt +
                '}';
    }
}