package com.example.volunteersApp;

import com.google.firebase.firestore.PropertyName; // Import this

public class RatingReview {

    private String userId; // Changed to private for typical encapsulation
    private float ratingValue;
    private String reviewText;

    // Default constructor is required by Firestore
    public RatingReview() {
    }

    public RatingReview(String userId, float ratingValue, String reviewText) {
        this.userId = userId;
        this.ratingValue = ratingValue;
        this.reviewText = reviewText;
    }

    @PropertyName("user_id") // Explicitly names the field in Firestore
    public String getUserId() {
        return userId;
    }

    @PropertyName("user_id")
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @PropertyName("rating") // Explicitly names the field in Firestore
    public float getRatingValue() {
        return ratingValue;
    }

    @PropertyName("rating")
    public void setRatingValue(float ratingValue) {
        this.ratingValue = ratingValue;
    }

    @PropertyName("review_text") // Explicitly names the field in Firestore
    public String getReviewText() {
        return reviewText;
    }

    @PropertyName("review_text")
    public void setReviewText(String reviewText) {
        this.reviewText = reviewText;
    }

    // You might also want to add @Exclude for fields you DON'T want to save to Firestore.
    // For example, if you had a temporary local variable:
    // @Exclude
    // public boolean isBeingEdited;
}