package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
// import android.view.View; // Keep if needed for other UI elements
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // For SnapshotListener callback
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat; // Needed for parameterized string in onEvent

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore specific imports
import com.google.firebase.firestore.EventListener; // Explicit import
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException; // Explicit import
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration; // For managing listeners
import com.google.firebase.firestore.QuerySnapshot; // For SnapshotListener callback
import com.example.volunteersApp.models.EventModel;

import java.util.ArrayList;
import java.util.List;
// Assuming WaitlistedEvent and EventModel are correctly defined elsewhere
// Assuming WaitlistedEventAdapterListView is correctly defined

public class Waitlisted extends AppCompatActivity {

    private static final String TAG = "WaitlistedActivity";
    private static final String EVENTS_COLLECTION = "events";
    private static final String FIELD_UNAPPROVED_USER_IDS = "unApprovedUserIds";


    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration eventsListenerRegistration;

    private Button buttonApproved;
    // private Button buttonWaitlistedRefresh; // Renamed for clarity if it's for refresh
    private ListView listViewWaitlistedEvents;

    private WaitlistedEventAdapterListView adapter;
    private List<WaitlistedEvent> waitlistedEventList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.waitlisted);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_waitlisted_events); // Use string resource
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        buttonApproved = findViewById(R.id.button_approved);
        Button buttonWaitlistedRefresh = findViewById(R.id.button_waitlisted); // Assuming this is the refresh button

        listViewWaitlistedEvents = findViewById(R.id.listView_UnApproved); // Ensure ID matches your XML

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "User not logged in. Finishing WaitlistedActivity.");
            Toast.makeText(this, R.string.toast_please_log_in_to_view, Toast.LENGTH_LONG).show(); // Use string resource
            finish();
            return;
        }

        waitlistedEventList = new ArrayList<>();
        adapter = new WaitlistedEventAdapterListView(this, waitlistedEventList);
        listViewWaitlistedEvents.setAdapter(adapter);

        listViewWaitlistedEvents.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < waitlistedEventList.size()) { // Bounds check
                WaitlistedEvent clickedEvent = waitlistedEventList.get(position);
                if (clickedEvent != null && clickedEvent.getEventDetails() != null) {
                    String eventTitle = clickedEvent.getEventDetails().getTitle();
                    if (eventTitle == null) {
                        eventTitle = getString(R.string.default_event_title_placeholder); // Add this string resource
                    }
                    // --- UPDATED TO USE STRING RESOURCE ---
                    Toast.makeText(Waitlisted.this,
                            getString(R.string.toast_event_clicked, eventTitle),
                            Toast.LENGTH_SHORT).show();

                    // Example: Navigate to EventDetailActivity or a similar detail screen
                    // if (clickedEvent.getEventDetails().getEventId() != null) {
                    //     Intent detailIntent = new Intent(Waitlisted.this, EventDetailActivity.class);
                    //     detailIntent.putExtra("EVENT_ID", clickedEvent.getEventDetails().getEventId());
                    //     startActivity(detailIntent);
                    // }
                }
            }
        });


        buttonApproved.setOnClickListener(v -> {
            Intent intent = new Intent(Waitlisted.this, ApprovedEventsActivity.class);
            startActivity(intent);
        });

        if (buttonWaitlistedRefresh != null) {
            buttonWaitlistedRefresh.setOnClickListener(v -> {
                Toast.makeText(Waitlisted.this, R.string.toast_refreshing_waitlisted_events, Toast.LENGTH_SHORT).show(); // Use string resource
                fetchWaitlistedEvents();
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        fetchWaitlistedEvents();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventsListenerRegistration != null) {
            eventsListenerRegistration.remove();
            eventsListenerRegistration = null;
            Log.d(TAG, "Firestore listener removed in onStop.");
        }
    }

    private void fetchWaitlistedEvents() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "Cannot fetch events, user not logged in.");
            if (!waitlistedEventList.isEmpty()) {
                waitlistedEventList.clear();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateEmptyStateVisibility();
            }
            return;
        }
        final String currentUserId = currentUser.getUid();

        if (eventsListenerRegistration != null) {
            eventsListenerRegistration.remove();
            eventsListenerRegistration = null;
        }

        Query query = db.collection(EVENTS_COLLECTION)
                .whereArrayContains(FIELD_UNAPPROVED_USER_IDS, currentUserId);

        Log.d(TAG, "Fetching waitlisted events for user: " + currentUserId);
        eventsListenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots,
                                @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "Firestore listen failed for waitlisted events.", e);
                  //  Toast.makeText(Waitlisted.this,
                          //  ContextCompat.getString(getApplicationContext(), R.string.toast_failed_to_load_events_param,  e.getMessage()),
                          //  Toast.LENGTH_LONG).show();

                    // Option A: If the string is NOT parameterized, and you want to append the message
                    String baseMessage = ContextCompat.getString(getApplicationContext(), R.string.toast_failed_to_load_events_param);
                    String fullMessage = baseMessage + " " + e.getMessage(); // Simple concatenation
                    Toast.makeText(Waitlisted.this, fullMessage, Toast.LENGTH_LONG).show();

                    // Option B: OR, change the string resource to be parameterized (RECOMMENDED if you want to include e.getMessage())
                    // In strings.xml: <string name="toast_failed_to_load_events_param">Failed to load events: %1$s</string>
                    // Then your original Java code is correct.
                    return;
                }

                waitlistedEventList.clear();

                if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            EventModel eventDetails = document.toObject(EventModel.class);
                            // EventModel's @DocumentId should handle setting eventId if used
                            // if (eventDetails.getEventId() == null && document.getId() != null) eventDetails.setEventId(document.getId());

                            String eventId = document.getId();
                            String organizerId = eventDetails.getOrganizerId();

                            if (organizerId == null) {
                                Log.w(TAG, "OrganizerId is null for eventId: " + eventId + ". Using placeholder or skipping.");
                                // organizerId = "UNKNOWN_ORGANIZER"; // Example placeholder
                            }
                            waitlistedEventList.add(new WaitlistedEvent(eventDetails, eventId, organizerId));
                        } catch (Exception ex) {
                            Log.e(TAG, "Error parsing document to EventModel: " + document.getId(), ex);
                        }
                    }
                    Log.d(TAG, "Successfully loaded " + waitlistedEventList.size() + " waitlisted events.");
                } else {
                    Log.d(TAG, "No waitlisted events found for the current user in snapshot.");
                }

                if (adapter != null) adapter.notifyDataSetChanged();
                updateEmptyStateVisibility();
            }
        });
    }

    private void updateEmptyStateVisibility() {
        // Ensure you have a TextView for empty state in your R.layout.waitlisted if you use this
        // TextView textViewEmpty = findViewById(R.id.textViewEmptyListWaitlisted);

        if (waitlistedEventList.isEmpty()) {
            Toast.makeText(Waitlisted.this, R.string.toast_no_events_on_waitlist, Toast.LENGTH_SHORT).show();
            // if (textViewEmpty != null) {
            //     textViewEmpty.setText(R.string.empty_waitlist_message); // Add this string resource
            //     textViewEmpty.setVisibility(View.VISIBLE);
            //     listViewWaitlistedEvents.setVisibility(View.GONE);
            // }
        } else {
            // if (textViewEmpty != null) {
            //     textViewEmpty.setVisibility(View.GONE);
            //     listViewWaitlistedEvents.setVisibility(View.VISIBLE);
            // }
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
