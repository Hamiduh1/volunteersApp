package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.volunteersApp.databinding.Eventdescription4Binding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore Imports
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EventDescription4 extends AppCompatActivity {

    private static final String TAG = "EventDescription4FS"; // FS for Firestore

    // Firestore Collection/Field Constants
    private static final String COLLECTION_EVENTS = "events"; // Top-level collection for events
    private static final String SUBCOLLECTION_REVIEWS = "reviews"; // Subcollection for reviews under an event

    // Event document fields (assuming these are fields in your EventModel or similar POJO)
    private static final String FIELD_NAME = "name";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_EVENT_DATE = "eventDate"; // Changed to match common POJO naming
    private static final String FIELD_PAYMENT = "payment";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_AVERAGE_RATING = "avgRating";
    // Host ID might be a field within the event document itself if not part of the path
    // private static final String FIELD_HOST_ID = "organizerId";


    private Eventdescription4Binding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private DocumentReference eventDocRef; // Reference to the specific event document
    private ListenerRegistration eventDetailsListenerRegistration;
    // No separate averageRatingListenerRegistration if avgRating is part of the main event document

    private String eventId;
    // organizerId might not be needed for the Firestore path if events are in a top-level collection
    // private String organizerId;

    // Model for RatingReview (ensure this class exists and is Firestore compatible)
    // Needs a public no-argument constructor and public getters for Firestore deserialization.
    public static class RatingReview {
        private String userId; // Or UId, keep consistent
        private float rating; // Or Rating
        private String reviewText; // Or Review
        // Firestore also supports @ServerTimestamp for adding timestamps automatically

        public RatingReview() {} // Default constructor for Firebase Firestore

        public RatingReview(String userId, float rating, String reviewText) {
            this.userId = userId;
            this.rating = rating;
            this.reviewText = reviewText;
        }

        public String getUserId() { return userId; }
        public float getRating() { return rating; }
        public String getReviewText() { return reviewText; }

        // Optional: setters if you ever need to modify these locally before saving
        public void setUserId(String userId) { this.userId = userId; }
        public void setRating(float rating) { this.rating = rating; }
        public void setReviewText(String reviewText) { this.reviewText = reviewText; }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = Eventdescription4Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        eventId = getIntent().getStringExtra("EventId");
        // organizerId = getIntent().getStringExtra("organizerId"); // May not be needed for Firestore path

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "EventId is null or empty. Cannot load event details.");
            Toast.makeText(this,  R.string.error_event_id_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // With Firestore, if 'events' is a top-level collection, organizerId is not part of the path to the event.
        // It might be a field within the event document itself, used for authorization or filtering.
        // If your Firestore structure is /hosts/{organizerId}/events/{eventId}, then you'd still need organizerId.
        // Assuming top-level 'events' collection for this migration:
        eventDocRef = db.collection(COLLECTION_EVENTS).document(eventId);

        Log.d(TAG, "Firestore path for event: " + eventDocRef.getPath());

        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.buttonChat.setOnClickListener(v -> {
            Intent intent = new Intent(EventDescription4.this, ChatBox.class);
            intent.putExtra("EventId", eventId); // EventId remains the same
            startActivity(intent);
        });

        binding.buttonSubmitReview.setOnClickListener(v -> submitReview());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventDocRef != null) {
            attachEventDetailsListener();
        } else {
            Log.e(TAG, "eventDocRef is null in onStart.");
            Toast.makeText(this, R.string.error_event_data_unavailable, Toast.LENGTH_SHORT).show();
            clearEventDetailsUI();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventDetailsListenerRegistration != null) {
            eventDetailsListenerRegistration.remove();
        }
    }

    private void attachEventDetailsListener() {
        if (eventDetailsListenerRegistration != null) {
            eventDetailsListenerRegistration.remove(); // Remove existing listener first
        }
        // Listen to the main event document
        eventDetailsListenerRegistration = eventDocRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "Error fetching event details: ", e);
                    Toast.makeText(EventDescription4.this, R.string.error_could_not_load_event_details, Toast.LENGTH_SHORT).show();
                    clearEventDetailsUI();
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    // Assuming your event data is directly in the document,
                    // or you have a POJO like EventModel to map it.
                    // For direct field access:
                    binding.textViewEventName.setText(snapshot.getString(FIELD_NAME));
                    binding.textViewLocationValue.setText(snapshot.getString(FIELD_LOCATION));
                    binding.textViewDateValue.setText(snapshot.getString(FIELD_EVENT_DATE)); // Use the correct date field name
                    binding.textViewPaymentValue.setText(snapshot.getString(FIELD_PAYMENT));
                    binding.textViewDescriptionValue.setText(snapshot.getString(FIELD_DESCRIPTION));

                    // Display average rating if it's stored in the main event document
                    Double avgRatingDouble = snapshot.getDouble(FIELD_AVERAGE_RATING);
                    if (avgRatingDouble != null) {
                        // binding.textViewCurrentAverageRating.setText(String.format("%.1f", avgRatingDouble.floatValue()));
                        Log.d(TAG, "Current average rating from event doc: " + avgRatingDouble);
                    } else {
                        // binding.textViewCurrentAverageRating.setText(R.string.rating_not_available);
                        Log.d(TAG, "No average rating found in event doc.");
                    }

                } else {
                    Log.w(TAG, "Event data snapshot does not exist for EventId: " + eventId);
                    Toast.makeText(EventDescription4.this, R.string.error_event_details_not_found, Toast.LENGTH_SHORT).show();
                    clearEventDetailsUI();
                }
            }
        });
    }


    private void submitReview() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.error_must_be_signed_in_to_review, Toast.LENGTH_SHORT).show();
            return;
        }
        if (eventDocRef == null) {
            Toast.makeText(this, R.string.error_cannot_submit_review, Toast.LENGTH_SHORT).show();
            return;
        }

        float ratingValue = binding.ratingBarReview.getRating();
        String reviewText = binding.editTextReviewText.getText().toString().trim();

        if (ratingValue == 0) {
            Toast.makeText(this, R.string.error_please_provide_rating, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(reviewText)) {
            Toast.makeText(this, R.string.error_please_provide_review_text, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.buttonSubmitReview.setEnabled(false);

        RatingReview ratingReview = new RatingReview(currentUser.getUid(), ratingValue, reviewText);

        // Add the review to the 'reviews' subcollection of the event document
        CollectionReference reviewsRef = eventDocRef.collection(SUBCOLLECTION_REVIEWS);

        reviewsRef.add(ratingReview) // Firestore generates a unique ID for the review document
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(EventDescription4.this, R.string.review_submitted_successfully, Toast.LENGTH_SHORT).show();
                    binding.editTextReviewText.setText("");
                    binding.ratingBarReview.setRating(0);
                    Log.d(TAG, "Review submitted with ID: " + documentReference.getId());
                    calculateAndSetAverageRating(); // Recalculate average after new review
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EventDescription4.this, getString(R.string.review_submission_failed_with_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to submit review: ", e);
                })
                .addOnCompleteListener(task -> binding.buttonSubmitReview.setEnabled(true));
    }

    private void calculateAndSetAverageRating() {
        if (eventDocRef == null) return;

        CollectionReference reviewsRef = eventDocRef.collection(SUBCOLLECTION_REVIEWS);

        reviewsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                if (querySnapshot == null || querySnapshot.isEmpty()) {
                    Log.d(TAG, "No reviews yet to calculate average.");
                    // Update the main event document's avgRating field
                    eventDocRef.update(FIELD_AVERAGE_RATING, 0.0) // Or FieldValue.delete() to remove
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Average rating set to 0 as no reviews exist."))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to set average rating to 0: ", e));
                    // binding.textViewCurrentAverageRating.setText(R.string.rating_not_available);
                    return;
                }

                float totalRating = 0;
                int reviewCount = 0;

                for (DocumentSnapshot reviewSnapshot : querySnapshot.getDocuments()) {
                    RatingReview review = reviewSnapshot.toObject(RatingReview.class); // Map to POJO
                    if (review != null) {
                        totalRating += review.getRating();
                        reviewCount++;
                    }
                }

                if (reviewCount > 0) {
                    double average = (double) totalRating / reviewCount;
                    // Update the main event document's avgRating field
                    eventDocRef.update(FIELD_AVERAGE_RATING, average)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Average rating updated in Firestore to: " + average))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update average rating in Firestore: ", e));
                    // The eventDetailsListener will pick up this change and update the UI if it's listening to avgRating.
                } else {
                    eventDocRef.update(FIELD_AVERAGE_RATING, 0.0)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Average rating set to 0 (no valid ratings found)."))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to set average rating to 0: ", e));
                }

            } else {
                Log.e(TAG, "Error calculating average rating: ", task.getException());
                Toast.makeText(EventDescription4.this, R.string.error_calculating_average_rating, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // getStringValue helper might not be needed if directly accessing fields from DocumentSnapshot
    // or using a POJO (e.g., eventDocRef.get().toObject(Event.class))

    private void clearEventDetailsUI() {
        String na = getString(R.string.default_na);
        if (binding == null) return;
        binding.textViewEventName.setText(na);
        binding.textViewLocationValue.setText(na);
        binding.textViewDateValue.setText(na);
        binding.textViewPaymentValue.setText(na);
        binding.textViewDescriptionValue.setText(na);
        // Clear review section
        binding.ratingBarReview.setRating(0);
        binding.editTextReviewText.setText("");
        // binding.textViewCurrentAverageRating.setText(na); // If you have this TextView
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // Or finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}