// File: SupportItem.java
package com.example.volunteersApp;

import com.google.firebase.firestore.Exclude;
import java.util.Objects; // <<< Import for Objects.equals and Objects.hash

public class SupportItem {
    private String text;        // To be stored in and retrieved from Firestore
    private String iconName;    // To be stored in and retrieved from Firestore (e.g., "feedback_icon")
    private int order;          // Optional: For ordering items if fetched from a list in Firestore

    @Exclude
    private int imageResourceId; // Populated client-side

    // --- Constructors ---
    public SupportItem() {
        // No-argument constructor REQUIRED for Firestore deserialization
    }

    // Primarily for local use or testing, or if iconName is directly mapped to imageResourceId at creation
    public SupportItem(String text, int imageResourceId, String iconName, int order) {
        this.text = text;
        this.imageResourceId = imageResourceId;
        this.iconName = iconName;
        this.order = order;
    }

    // Constructor more aligned with Firestore data
    public SupportItem(String text, String iconName, int order) {
        this.text = text;
        this.iconName = iconName;
        this.order = order;
        // imageResourceId would be set later via resolveImageResourceId
    }

    // --- Getters and Setters ---
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Exclude
    public int getImageResourceId() {
        return imageResourceId;
    }

    public void setImageResourceId(int imageResourceId) {
        this.imageResourceId = imageResourceId;
    }

    // --- Helper Method ---
    public void resolveImageResourceId(android.content.Context context) {
        if (context != null && this.iconName != null && !this.iconName.isEmpty()) {
            try {
                int resolvedId = context.getResources().getIdentifier(
                        this.iconName,
                        "drawable",
                        context.getPackageName()
                );
                if (resolvedId == 0) { // 0 means not found
                    android.util.Log.w("SupportItem", "Drawable resource not found for: " + this.iconName + ". Using default.");
                    // Ensure R.drawable.ic_default_placeholder exists in your drawables
                    this.imageResourceId = R.drawable.ic_default_placeholder;
                } else {
                    this.imageResourceId = resolvedId;
                }
            } catch (Exception e) {
                android.util.Log.e("SupportItem", "Error resolving drawable resource ID for: " + this.iconName, e);
                this.imageResourceId = R.drawable.ic_default_placeholder;
            }
        } else if (context != null) { // iconName is null or empty, but context is not
            android.util.Log.w("SupportItem", "iconName is null or empty. Using default placeholder.");
            this.imageResourceId = R.drawable.ic_default_placeholder;
        }
        // If context is null, imageResourceId remains unchanged (or 0 if never set)
        // which the adapter should handle by showing a default.
    }

    // --- equals() and hashCode() for DiffUtil ---

    /**
     * For DiffUtil's areItemsTheSame, a combination of text and iconName
     * might serve as a unique key if no Firestore document ID is directly part of this model.
     * If you add a String documentId from Firestore to this model, that would be the best
     * candidate for a unique ID in areItemsTheSame.
     * <p>
     * For areContentsTheSame, this equals method checks all relevant data fields.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportItem that = (SupportItem) o;
        return order == that.order &&
                imageResourceId == that.imageResourceId && // Important as it's resolved client-side
                Objects.equals(text, that.text) &&
                Objects.equals(iconName, that.iconName);
    }

    @Override
    public int hashCode() {
        // Use the same fields as in equals() for consistency.
        return Objects.hash(text, iconName, order, imageResourceId);
    }

    /**
     * Call this method after fetching from Firestore and setting text, iconName, order.
     * This will ensure imageResourceId is populated before the item is displayed
     * or compared by DiffUtil if imageResourceId is part of the content check.
     *
     * @param context Context to resolve resources
     * @return The same SupportItem instance, for chaining if desired.
     */
    public SupportItem process(android.content.Context context) {
        resolveImageResourceId(context);
        return this;
    }
}