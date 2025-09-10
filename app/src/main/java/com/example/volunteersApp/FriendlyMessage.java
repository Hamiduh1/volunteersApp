package com.example.volunteersApp;

import com.google.firebase.firestore.ServerTimestamp; // For server-side timestamp
import java.util.Date; // For the timestamp field

public class FriendlyMessage {

    private String text;
    private String name;
    private String photoUrl; // Made non-final to allow setting by Firestore if needed
    private String userId;   // To identify the sender
    private Date timestamp;  // For message ordering and display

    // **IMPORTANT: A public no-argument constructor is required by Firestore**
    public FriendlyMessage() {
        // Default constructor required for calls to DataSnapshot.getValue(FriendlyMessage.class)
        // and for Firestore's automatic data mapping.
    }

    // Constructor for creating new messages programmatically in your app
    public FriendlyMessage(String text, String name, String photoUrl, String userId) {
        this.text = text;
        this.name = name;
        this.photoUrl = photoUrl;
        this.userId = userId;
        // Timestamp will be set by Firestore using @ServerTimestamp or you can set it manually
    }

    // Getters
    public String getText() {
        return text;
    }

    public String getTitle() {
        return name;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getUserId() {
        return userId;
    }

    // Use @ServerTimestamp to get a server-generated timestamp when the message is written to Firestore.
    // The local value will be null until the server confirms the write,
    // or it will be an estimate if hasPendingWrites is true.
    @ServerTimestamp
    public Date getTimestamp() {
        return timestamp;
    }

    // Setters (Firestore can use these during deserialization)
    public void setText(String text) {
        this.text = text;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
