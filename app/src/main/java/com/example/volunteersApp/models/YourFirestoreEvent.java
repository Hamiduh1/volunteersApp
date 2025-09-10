// com/example/volunteersApp/models/YourFirestoreEvent.java
package com.example.volunteersApp.models;

import androidx.annotation.Keep; // For ProGuard/R8 to keep this class and its members

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp; // Keep import if you might use the annotation

import java.util.Objects; // For equals() and hashCode()

@Keep // Ensure ProGuard/R8 doesn't remove this class or its members used by Firestore via reflection
@IgnoreExtraProperties // Good practice to prevent crashes if Firestore has extra fields not in your POJO
public class YourFirestoreEvent {

    @DocumentId // This field will be automatically populated with the document's ID by Firestore
    private String documentId;

    // Fields must match the keys in your Firestore document (case-sensitive)
    // Or use @PropertyName("firestore_field_name") if names differ
    private String eventName;
    private String organizerId;
    private String description;
    private String location;
    private String category;
    private double payment;
    private int participantsNeeded;
    private String imageUrl;

    // Use com.google.firebase.Timestamp for date/time fields
    // If you want Firestore to automatically set this on the server upon creation/update,
    // you would annotate it with @ServerTimestamp.
    // @ServerTimestamp
    private Timestamp eventTimestamp;

    // **IMPORTANT: A public no-argument constructor is required by Firestore**
    public YourFirestoreEvent() {
        // Firestore needs this to deserialize documents back into objects
    }

    // Optional: Constructor for easy object creation programmatically (not used by Firestore deserialization)
    /*
    public YourFirestoreEvent(String eventName, String organizerId, String description, String location,
                              String category, double payment, int participantsNeeded,
                              String imageUrl, Timestamp eventTimestamp) {
        this.eventName = eventName;
        this.organizerId = organizerId;
        this.description = description;
        this.location = location;
        this.category = category;
        this.payment = payment;
        this.participantsNeeded = participantsNeeded;
        this.imageUrl = imageUrl;
        this.eventTimestamp = eventTimestamp;
    }
    */

    // --- Getters ---
    public String getDocumentId() {
        return documentId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getCategory() {
        return category;
    }

    public double getPayment() {
        return payment;
    }

    public int getParticipantsNeeded() {
        return participantsNeeded;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Timestamp getEventTimestamp() {
        return eventTimestamp;
    }

    // --- Setters ---
    // Setter for documentId is useful if you want to set it manually after object creation,
    // though Firestore populates it automatically when reading.
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPayment(double payment) {
        this.payment = payment;
    }

    public void setParticipantsNeeded(int participantsNeeded) {
        this.participantsNeeded = participantsNeeded;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setEventTimestamp(Timestamp eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    // --- equals() and hashCode() ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YourFirestoreEvent that = (YourFirestoreEvent) o;

        // If documentId is available and non-null for both, it's the primary means of equality for persisted entities.
        if (documentId != null && that.documentId != null) {
            return Objects.equals(documentId, that.documentId);
        }
        // If one or both documentIds are null (e.g., new objects not yet saved),
        // compare all other significant fields.
        // This makes equality meaningful even before persistence or if documentId isn't the sole focus.
        return Double.compare(that.payment, payment) == 0 &&
                participantsNeeded == that.participantsNeeded &&
                Objects.equals(eventName, that.eventName) &&
                Objects.equals(organizerId, that.organizerId) &&
                Objects.equals(description, that.description) &&
                Objects.equals(location, that.location) &&
                Objects.equals(category, that.category) &&
                Objects.equals(imageUrl, that.imageUrl) &&
                Objects.equals(eventTimestamp, that.eventTimestamp);
    }

    @Override
    public int hashCode() {
        // If documentId is considered the primary identifier, base the hash code on it primarily.
        if (documentId != null) {
            return Objects.hash(documentId);
        }
        // Fallback hash code based on other fields if documentId is null.
        return Objects.hash(eventName, organizerId, description, location, category,
                payment, participantsNeeded, imageUrl, eventTimestamp);
    }

    // --- toString() for debugging ---
    @Override
    public String toString() {
        return "YourFirestoreEvent{" +
                "documentId='" + documentId + '\'' +
                ", eventName='" + eventName + '\'' +
                ", organizerId='" + organizerId + '\'' +
                ", description='" + (description != null ? description.substring(0, Math.min(description.length(), 30)) + "..." : "null") + '\'' + // Show a snippet
                ", location='" + location + '\'' +
                ", category='" + category + '\'' +
                ", payment=" + payment +
                ", participantsNeeded=" + participantsNeeded +
                ", imageUrl='" + (imageUrl != null && !imageUrl.isEmpty() ? "present" : "absent_or_empty") + '\'' +
                ", eventTimestamp=" + (eventTimestamp != null ? eventTimestamp.toDate() : "null") + // Convert to Date for readability
                '}';
    }
}

