package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem; // For onSupportNavigateUp
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
// import android.widget.TextView; // For empty state message if you add one

import androidx.annotation.NonNull; // Keep for @NonNull if used in method params not shown
import androidx.annotation.Nullable; // For SnapshotListener callback
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat; // For getString with parameters

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore Imports
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestoreException; // Keep if directly catching it
import com.google.firebase.firestore.QuerySnapshot; // For SnapshotListener lambda
import com.example.volunteersApp.models.EventModel;
// Assuming ApprovedEvent class is defined correctly
// import com.example.volunteersApp.models.ApprovedEvent; // If it's in models package

import java.util.ArrayList;
import java.util.List;

public class ApprovedEventsActivity extends AppCompatActivity {

    private static final String TAG = "ApprovedEventsActivity"; // Standardized TAG

    // Firebase Auth
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Firestore
    private FirebaseFirestore db;
    private CollectionReference eventsCollectionRef;
    private ListenerRegistration eventsListenerRegistration;

    // UI Elements
    private ListView listViewApprovedEvents;
    // private TextView textViewEmptyApproved; // Optional: For showing "No approved events" message

    private EventList4 adapter; // Assuming EventList4 and ApprovedEvent are compatible
    private List<ApprovedEvent> approvedEventList;

    // Firestore constants
    // Using string resources for collection and field names for better maintainability if needed
    private String firestoreEventsCollection;
    private String firestoreApprovedUserIdsField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.approved); // Ensure this layout exists and has correct IDs

        // Initialize string resources for Firestore paths
        firestoreEventsCollection = getString(R.string.firestore_collection_events);
        firestoreApprovedUserIdsField = getString(R.string.firestore_field_approved_user_ids);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_approved_events); // Use string resource
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Button buttonApproved = findViewById(R.id.button_approved);
        Button buttonWaitlisted = findViewById(R.id.button_waitlisted);
        // textViewEmptyApproved = findViewById(R.id.textView_empty_approved_events); // Initialize if you add this view

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        db = FirebaseFirestore.getInstance();
        eventsCollectionRef = db.collection(firestoreEventsCollection);

        listViewApprovedEvents = findViewById(R.id.listView_Approved);
        approvedEventList = new ArrayList<>();

        adapter = new EventList4(this, approvedEventList);
        listViewApprovedEvents.setAdapter(adapter);
        // Optional: Set empty view for the ListView
        // if (textViewEmptyApproved != null) {
        //     listViewApprovedEvents.setEmptyView(textViewEmptyApproved);
        // }

        if (buttonApproved != null) {
            buttonApproved.setOnClickListener(v -> {
                Toast.makeText(ApprovedEventsActivity.this,
                        R.string.toast_already_viewing_approved, Toast.LENGTH_SHORT).show(); // Use string resource
            });
        }

        if (buttonWaitlisted != null) {
            buttonWaitlisted.setOnClickListener(v -> {
                Intent intent = new Intent(ApprovedEventsActivity.this, Waitlisted.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Good for navigation stack
                startActivity(intent);
            });
        }

        if (currentUser == null) {
            Toast.makeText(this, R.string.toast_sign_in_to_see_approved, Toast.LENGTH_LONG).show(); // Use string resource
            Log.w(TAG, "User is not signed in. Cannot load approved events.");
            // finish(); // Consider finishing or redirecting to login
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUser != null) {
            attachFirestoreReadListener();
        } else {
            // This case should ideally be handled by the check in onCreate
            approvedEventList.clear();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            Log.w(TAG, "onStart: User is null, approved events list cleared (should have been caught in onCreate).");
            updateEmptyStateVisibility(); // Update UI if user somehow becomes null after onCreate
        }
    }

    private void attachFirestoreReadListener() {
        if (currentUser == null) { // Defensive check
            Log.w(TAG, "Cannot attach listener, current user is null.");
            return;
        }

        // Only attach listener if it's not already active
        if (eventsListenerRegistration == null) {
            String currentUserId = currentUser.getUid();

            Query query = eventsCollectionRef.whereArrayContains(firestoreApprovedUserIdsField, currentUserId);
            // Optional: Order results, e.g., by event date (ensure "eventDateTime" or similar field exists)
            // query = query.orderBy("eventDateTime", Query.Direction.DESCENDING);

            Log.d(TAG, "Attaching Firestore listener for approved events for user: " + currentUserId);
            eventsListenerRegistration = query.addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Log.e(TAG, "Firestore listen failed:", e);
                    Toast.makeText(ApprovedEventsActivity.this,
                            getString(R.string.toast_failed_to_load_events_param, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                approvedEventList.clear(); // Clear old data before adding new snapshot data

                if (snapshots == null || snapshots.isEmpty()) {
                    Log.i(TAG, "No approved events found for user: " + currentUserId);
                } else {
                    Log.d(TAG, "Received " + snapshots.size() + " approved event(s) in snapshot.");
                    for (QueryDocumentSnapshot document : snapshots) {
                        try {
                            EventModel eventModel = document.toObject(EventModel.class);

                            // The @DocumentId on EventModel should set its eventId.
                            // If not, or for belt-and-suspenders:
                            // if (eventModel.getEventId() == null) eventModel.setEventId(document.getId());


                            // Perform null checks on necessary fields from eventModel
                            if (eventModel != null && eventModel.getEventId() != null &&
                                    eventModel.getTitle() != null && eventModel.getOrganizerId() != null) {

                                approvedEventList.add(new ApprovedEvent(
                                        eventModel.getEventId(),
                                        eventModel.getTitle(),
                                        eventModel.getOrganizerId(),
                                        eventModel
                                ));
                                Log.d(TAG, "Added approved event: " + eventModel.getTitle() + " (ID: " + eventModel.getEventId() + ")");
                            } else {
                                String missingInfo = " (EventModel is null)";
                                if (eventModel != null) {
                                    if (eventModel.getEventId() == null) missingInfo = " (EventId is null)";
                                    else if (eventModel.getTitle() == null) missingInfo = " (Title is null)";
                                    else if (eventModel.getOrganizerId() == null) missingInfo = " (OrganizerId is null)";
                                    else missingInfo = " (Unknown issue with EventModel fields)";
                                }
                                Log.w(TAG, "Cannot add approved event from document: " + document.getId() + "." + missingInfo);
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error processing document: " + document.getId(), ex);
                        }
                    }
                    Log.d(TAG, "Total approved events loaded: " + approvedEventList.size());
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                updateEmptyStateVisibility(); // Update UI based on list content
            });
        } else {
            Log.d(TAG, "Firestore listener already attached.");
        }
    }

    private void updateEmptyStateVisibility() {
        // Optional: Manage a TextView that shows when the list is empty
        // if (textViewEmptyApproved != null) {
        //     if (approvedEventList.isEmpty()) {
        //         textViewEmptyApproved.setText(R.string.no_approved_events_found); // Add this string resource
        //         textViewEmptyApproved.setVisibility(View.VISIBLE);
        //         listViewApprovedEvents.setVisibility(View.GONE);
        //     } else {
        //         textViewEmptyApproved.setVisibility(View.GONE);
        //         listViewApprovedEvents.setVisibility(View.VISIBLE);
        //     }
        // }
        // If not using a dedicated empty view, you might show a Toast if the list is empty after load
        if (approvedEventList.isEmpty() && eventsListenerRegistration != null) { // Only show toast if listener is active and list is empty
            // Toast.makeText(this, R.string.no_approved_events_found, Toast.LENGTH_SHORT).show(); // Avoid toast on every update
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventsListenerRegistration != null) {
            eventsListenerRegistration.remove();
            eventsListenerRegistration = null; // Important to allow re-attachment in onStart
            Log.d(TAG, "Removed Firestore SnapshotListener.");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // onBackPressed(); // Default behavior
        finish(); // Ensures this activity is removed from the back stack if that's the desired UX
        return true;
    }

    // Example String Resources to add to res/values/strings.xml:
    // <string name="title_approved_events">Approved Events</string>
    // <string name="toast_already_viewing_approved">Already viewing approved events.</string>
    // <string name="toast_sign_in_to_see_approved">Please sign in to see approved events.</string>
    // <string name="toast_failed_to_load_events_param">Failed to load events: %1$s</string>
    // <string name="no_approved_events_found">No approved events found.</string>
    // <string name="firestore_collection_events">events</string> // Your actual collection name
    // <string name="firestore_field_approved_user_ids">approvedUserIds</string> // Your actual field name
}

