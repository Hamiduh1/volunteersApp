package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

// Firestore Imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

public class EventDescription extends AppCompatActivity {

    private static final String TAG = "EventDescriptionFS";

    // Intent Extras (primarily for Event ID now)
    public static final String EXTRA_EVENT_ID = "EventId"; // Key to pass the document ID from Firestore
    // public static final String EXTRA_HOST_ID = "organizerId"; // Keep if using nested structure /hosts/{organizerId}/events/{eventId}

    // Firestore Collection and Field Constants (adjust to your Firestore structure)
    private static final String COLLECTION_EVENTS = "events"; // Top-level collection for events
    // Event document fields
    private static final String FIELD_EVENT_NAME = "name";        // Or whatever you named it, e.g., "eventName"
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_DATE = "eventDate";       // Or "date"
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_PAYMENT = "payment";
    private static final String FIELD_TYPE = "type";
    // private static final String FIELD_HOST_ID = "organizerId"; // If host ID is a field in the event document


    // UI Elements
    private TextView textViewEventNameDisplay;
    private TextView textViewLocationDisplay;
    private TextView textViewDateDisplay;
    private TextView textViewDescriptionDisplay;
    private TextView textViewPaymentDisplay;
    private TextView textViewTypeDisplay;
    // private TextView textViewHostInfo;

    private FirebaseFirestore db;
    private DocumentReference eventDocRef;
    private ListenerRegistration eventListenerRegistration;

    private String eventId;
    // private String organizerId; // From intent, if needed for Firestore path

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_description);

        db = FirebaseFirestore.getInstance();

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_event_description);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Event Details"); // Default title, will be updated
        }

        // Initialize UI elements
        textViewEventNameDisplay = findViewById(R.id.textViewEventNameDisplay);
        textViewLocationDisplay = findViewById(R.id.textViewLocationDisplay);
        textViewDateDisplay = findViewById(R.id.textViewDateDisplay);
        textViewDescriptionDisplay = findViewById(R.id.textViewDescriptionDisplay);
        textViewPaymentDisplay = findViewById(R.id.textViewPaymentDisplay);
        textViewTypeDisplay = findViewById(R.id.textViewTypeDisplay);
        // textViewHostInfo = findViewById(R.id.textViewHostInfo);

        // Get EventId from Intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_EVENT_ID)) {
            eventId = intent.getStringExtra(EXTRA_EVENT_ID);
            // organizerId = intent.getStringExtra(EXTRA_HOST_ID); // Get organizerId if you use it for path

            if (eventId == null || eventId.isEmpty()) {
                Log.e(TAG, "EventId from intent is null or empty.");
                Toast.makeText(this, "Error: Event ID is missing.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Construct Firestore document reference
            // Assuming a top-level 'events' collection:
            eventDocRef = db.collection(COLLECTION_EVENTS).document(eventId);
            Log.d(TAG, "Fetching event details from: " + eventDocRef.getPath());

            // If using a nested structure like /hosts/{organizerId}/events/{eventId}:
            // if (organizerId == null || organizerId.isEmpty()) {
            //     Log.e(TAG, "organizerId is missing for nested Firestore path.");
            //     Toast.makeText(this, "Error: Host ID is missing.", Toast.LENGTH_LONG).show();
            //     finish();
            //     return;
            // }
            // eventDocRef = db.collection("hosts").document(organizerId).collection(COLLECTION_EVENTS).document(eventId);

        } else {
            Log.e(TAG, "Intent is null or EventId extra is missing.");
            Toast.makeText(this, "Could not load event details: Event ID missing.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventDocRef != null) {
            attachFirestoreListener();
        } else {
            // This case should be handled in onCreate by finishing the activity
            Log.e(TAG, "eventDocRef is null in onStart, cannot attach listener.");
            clearUI(); // Clear UI just in case
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventListenerRegistration != null) {
            eventListenerRegistration.remove();
        }
    }

    private void attachFirestoreListener() {
        if (eventListenerRegistration != null) {
            eventListenerRegistration.remove(); // Remove previous listener if any
        }
        eventListenerRegistration = eventDocRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "Firestore listen failed for event " + eventId, e);
                    Toast.makeText(EventDescription.this, "Error loading event data.", Toast.LENGTH_SHORT).show();
                    clearUI();
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Event data received: " + snapshot.getData());
                    populateUI(snapshot);
                } else {
                    Log.w(TAG, "Event document " + eventId + " does not exist.");
                    Toast.makeText(EventDescription.this, "Event details not found.", Toast.LENGTH_SHORT).show();
                    clearUI();
                    // Optionally finish(); if event not found is a critical error
                }
            }
        });
    }

    private void populateUI(DocumentSnapshot snapshot) {
        String eventName = snapshot.getString(FIELD_EVENT_NAME);
        String location = snapshot.getString(FIELD_LOCATION);
        String date = snapshot.getString(FIELD_DATE);
        String description = snapshot.getString(FIELD_DESCRIPTION);
        // Assuming payment is stored as a string. If it's a number, convert it.
        String paymentString = snapshot.getString(FIELD_PAYMENT);
        String type = snapshot.getString(FIELD_TYPE);
        // String organizerIdentity = snapshot.getString(FIELD_HOST_ID); // If you fetch host ID

        // Set Toolbar title
        if (getSupportActionBar() != null && eventName != null) {
            getSupportActionBar().setTitle(eventName);
        } else if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Event Details"); // Fallback
        }

        setTextViewText(textViewEventNameDisplay, eventName, "Not Available");
        setTextViewText(textViewLocationDisplay, location, "Location: Not specified");
        setTextViewText(textViewDateDisplay, date, "Date: Not specified");
        setTextViewText(textViewDescriptionDisplay, description, "No description provided.");
        setTextViewText(textViewPaymentDisplay, paymentString != null ? "Payment: " + paymentString : "Payment: N/A", "Payment: N/A");
        setTextViewText(textViewTypeDisplay, type != null ? "Type: " + type : "Type: Not specified", "Type: Not specified");

        // if (organizerIdentity != null && textViewHostInfo != null) {
        //     setTextViewText(textViewHostInfo, "Host ID: " + organizerIdentity, "");
        // }
    }

    private void clearUI() {
        String defaultText = "Loading...";
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Event Details");
        }
        setTextViewText(textViewEventNameDisplay, null, defaultText);
        setTextViewText(textViewLocationDisplay, null, defaultText);
        setTextViewText(textViewDateDisplay, null, defaultText);
        setTextViewText(textViewDescriptionDisplay, null, defaultText);
        setTextViewText(textViewPaymentDisplay, null, defaultText);
        setTextViewText(textViewTypeDisplay, null, defaultText);
        // if (textViewHostInfo != null) setTextViewText(textViewHostInfo, null, "");
    }

    // Helper method to set text or a default value if the provided text is null/empty
    private void setTextViewText(TextView textView, String text, String defaultText) {
        if (textView != null) {
            if (text != null && !text.isEmpty()) {
                textView.setText(text);
            } else {
                textView.setText(defaultText);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}