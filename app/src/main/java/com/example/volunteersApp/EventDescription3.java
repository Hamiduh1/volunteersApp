package com.example.volunteersApp;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.volunteersApp.databinding.Eventdescription3Binding;
// Firestore Imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

// FirebaseAuth can be kept if you plan to use user-specific logic later
// import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class EventDescription3 extends AppCompatActivity {

    private static final String TAG = "EventDescription3FS"; // FS for Firestore

    // Firestore Collection/Field Constants
    // Option 1: Top-level 'events' collection
    private static final String COLLECTION_EVENTS = "events";

    // Option 2: Nested structure (if you keep organizerId in the path)
    // private static final String COLLECTION_HOSTS = "hosts";
    // private static final String SUBCOLLECTION_EVENTS = "events";


    // Event document fields (ensure these match your Firestore document structure)
    private static final String FIELD_NAME = "name";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_EVENT_DATE = "eventDate"; // Or "date" if that's your field name
    private static final String FIELD_PAYMENT = "payment";
    private static final String FIELD_DESCRIPTION = "description";

    private Eventdescription3Binding binding;

    // private FirebaseAuth mAuth; // Initialize in onCreate if needed
    private FirebaseFirestore db;
    private DocumentReference eventDocRef;
    private ListenerRegistration eventDetailsListenerRegistration;

    private String eventId;
    // organizerId might be used for constructing the path if you have a nested structure,
    // or it might be a field within the event document for authorization/filtering.
    private String organizerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = Eventdescription3Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // mAuth = FirebaseAuth.getInstance(); // If needed
        db = FirebaseFirestore.getInstance();

        eventId = getIntent().getStringExtra("EventId");

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "EventId is null or empty. Cannot load event details.");
            Toast.makeText(this, R.string.error_event_id_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // --- Determine Firestore Path ---
        // Option 1: Top-level 'events' collection. organizerId might be a field in the document.
        eventDocRef = db.collection(COLLECTION_EVENTS).document(eventId);
        Log.d(TAG, "Using top-level 'events' collection. Path: " + eventDocRef.getPath());

        // Option 2: Nested structure /hosts/{organizerId}/events/{eventId}
        // organizerId = getIntent().getStringExtra("organizerId");
        // if (organizerId == null || organizerId.isEmpty()) {
        //     Log.e(TAG, "organizerId is null or empty. Cannot construct Firestore path for nested structure.");
        //     Toast.makeText(this, R.string.error_event_host_missing, Toast.LENGTH_LONG).show();
        //     finish();
        //     return;
        // }
        // eventDocRef = db.collection(COLLECTION_HOSTS).document(organizerId)
        //                 .collection(SUBCOLLECTION_EVENTS).document(eventId);
        // Log.d(TAG, "Using nested 'hosts/{organizerId}/events' structure. Path: " + eventDocRef.getPath());
        // --- End Determine Firestore Path ---


        // For debugging if you pass organizerId via Intent even with top-level collection
        organizerId = getIntent().getStringExtra("organizerId");
        if (organizerId != null && !organizerId.isEmpty()) {
            Log.d(TAG, "organizerId passed via Intent (for reference/debug): " + organizerId);
        } else {
            Log.d(TAG, "organizerId not passed via Intent or is empty.");
        }


        // --- Toolbar Setup (Optional) ---
        // if (binding.toolbarEventDescription3 != null) { // Check if toolbar exists
        //    setSupportActionBar(binding.toolbarEventDescription3);
        //    if (getSupportActionBar() != null) {
        //        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //        getSupportActionBar().setTitle(R.string.title_event_details);
        //    }
        // }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventDocRef != null) {
            attachEventDetailsListener();
        } else {
            // This case should ideally be caught in onCreate if eventDocRef fails to initialize
            Log.e(TAG, "eventDocRef is null in onStart. Cannot attach listener.");
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
            eventDetailsListenerRegistration.remove(); // Remove existing listener
        }
        eventDetailsListenerRegistration = eventDocRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "Listen failed for event " + eventId, e);
                    Toast.makeText(EventDescription3.this, R.string.error_could_not_load_event_details, Toast.LENGTH_SHORT).show();
                    clearEventDetailsUI();
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Event data received for EventId: " + eventId);
                    // Populate UI using field names.
                    // Assumes your EventModel or equivalent POJO has these fields,
                    // or you're directly accessing them.
                    binding.textViewEventName.setText(snapshot.getString(FIELD_NAME));
                    binding.textViewLocationValue.setText(snapshot.getString(FIELD_LOCATION));
                    binding.textViewDateValue.setText(snapshot.getString(FIELD_EVENT_DATE)); // Ensure this field name is correct
                    binding.textViewPaymentValue.setText(snapshot.getString(FIELD_PAYMENT));
                    binding.textViewDescriptionValue.setText(snapshot.getString(FIELD_DESCRIPTION));

                    // Alternatively, if you have an Event POJO (e.g., EventModel)
                    // EventModel event = snapshot.toObject(EventModel.class);
                    // if (event != null) {
                    //     binding.textViewEventName.setText(event.getTitle());
                    //     binding.textViewLocationValue.setText(event.getLocationName());
                    //     // ... and so on
                    // }

                } else {
                    Log.w(TAG, "Event data snapshot does not exist for EventId: " + eventId + " at path: " + eventDocRef.getPath());
                    Toast.makeText(EventDescription3.this, R.string.error_event_details_not_found, Toast.LENGTH_SHORT).show();
                    clearEventDetailsUI();
                }
            }
        });
    }

    // The getStringValue helper method is less common with Firestore when directly accessing fields
    // from DocumentSnapshot or converting to a POJO.
    // DocumentSnapshot.getString("fieldName") handles non-existence by returning null.
    // If you use a POJO, ensure it has default values or handle nulls after conversion.

    private void clearEventDetailsUI() {
        String na = getString(R.string.default_na); // Make sure R.string.default_na exists
        if (binding == null) return;

        binding.textViewEventName.setText(na);
        binding.textViewLocationValue.setText(na);
        binding.textViewDateValue.setText(na);
        binding.textViewPaymentValue.setText(na);
        binding.textViewDescriptionValue.setText(na);
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