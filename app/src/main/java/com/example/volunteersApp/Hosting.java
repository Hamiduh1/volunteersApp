package com.example.volunteersApp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast; // Added for user feedback

import androidx.activity.result.ActivityResultLauncher; // For activity result
import androidx.activity.result.contract.ActivityResultContracts; // For activity result
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.example.volunteersApp.organizer.CreateEventActivity;
import com.google.firebase.firestore.QuerySnapshot; // Already correctly imported

import java.util.ArrayList;
import java.util.List;

// TODO: 1. Create/Uncomment your Event model class (e.g., models/Event.java)
// import com.example.volunteersApp.models.Event;

// TODO: 2. Create/Uncomment your RecyclerView adapter (e.g., adapters/HostedEventsAdapter.java)
// import com.example.volunteersApp.adapters.HostedEventsAdapter;


public class Hosting extends Fragment {

    private static final String TAG = "HostingFragment";
    private static final String EVENTS_COLLECTION = "events"; // Your Firestore collection name

    private Button buttonHostNewEvent;
    private RecyclerView recyclerViewHostedEvents;
    private ProgressBar progressBar;
    private TextView textViewNoEvents;

    // TODO: 3. Uncomment and initialize these when Event model and Adapter are ready
    // private HostedEventsAdapter eventsAdapter;
    // private List<Event> hostedEventList;

    // Firebase Auth
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Firestore
    private FirebaseFirestore db;
    private CollectionReference eventsCollectionRef;
    private ListenerRegistration hostedEventsListenerRegistration;

    // ActivityResultLauncher for when CreateEventActivity finishes
    // This can be used to refresh the list if a new event was created,
    // though the snapshot listener might handle this automatically if it's broad enough
    // or if CreateEventActivity signals a change.
    private final ActivityResultLauncher<Intent> createEventLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Event was successfully created and CreateEventActivity finished.
                    // The Firestore snapshot listener should ideally pick up the new event.
                    // If not, you might need to explicitly re-fetch or refresh here.
                    Log.d(TAG, "Returned from CreateEventActivity with RESULT_OK. List should update via listener.");
                    // Optionally, show a toast or trigger a manual refresh if needed.
                    // e.g., if (currentUser != null) loadHostedEvents(); // Could cause duplicate listeners if not handled carefully
                } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    Log.d(TAG, "Returned from CreateEventActivity with RESULT_CANCELED.");
                }
            });

    public Hosting() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_hosting, container, false); // Ensure this layout exists

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        // currentUser will be checked in onStart and when loading data

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        eventsCollectionRef = db.collection(EVENTS_COLLECTION);

        // --- Find Views ---
        buttonHostNewEvent = view.findViewById(R.id.button_host_new_event);
        recyclerViewHostedEvents = view.findViewById(R.id.recycler_view_hosted_events);
        progressBar = view.findViewById(R.id.progressBar_hosting);
        textViewNoEvents = view.findViewById(R.id.textView_no_hosted_events);

        // --- Setup RecyclerView ---
        // TODO: 4. Complete RecyclerView setup
        // hostedEventList = new ArrayList<>();
        // if (getContext() != null) {
        //     eventsAdapter = new HostedEventsAdapter(getContext(), hostedEventList, eventId -> {
        //         // Handle click on a hosted event - e.g., navigate to HostEventDetailsActivity_Firestore
        //         Intent detailIntent = new Intent(getActivity(), HostEventDetailsActivity_Firestore.class);
        //         detailIntent.putExtra(HostEventDetailsActivity_Firestore.EXTRA_EVENT_ID, eventId);
        //         startActivity(detailIntent);
        //     });
        //     recyclerViewHostedEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        //     recyclerViewHostedEvents.setAdapter(eventsAdapter);
        // } else {
        //     Log.e(TAG, "Context was null during RecyclerView setup.");
        // }


        // --- Button Listener ---
        buttonHostNewEvent.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() != null) { // Check current user again before navigating
                // MODIFIED: Start CreateEventActivity instead of HostAnEvent
                Intent intent = new Intent(getActivity(), CreateEventActivity.class);
                createEventLauncher.launch(intent); // Use launcher to potentially get a result
            } else {
                Log.w(TAG, "Attempted to host event while not logged in.");
                if (isAdded() && getContext() != null) { // Check fragment is added
                    Toast.makeText(getContext(), getString(R.string.please_log_in_to_host_event), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initial UI state based on login status will be more robustly handled in onStart/loadHostedEvents
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        currentUser = mAuth.getCurrentUser(); // Refresh current user status
        if (currentUser != null) {
            buttonHostNewEvent.setEnabled(true);
            textViewNoEvents.setText(getString(R.string.loading_hosted_events)); // Initial loading message
            progressBar.setVisibility(View.VISIBLE);
            loadHostedEvents();
        } else {
            Log.w(TAG, "User not logged in. Cannot fetch hosted events.");
            buttonHostNewEvent.setEnabled(false);
            if (isAdded() && getContext() != null) {
                textViewNoEvents.setText(getString(R.string.please_log_in_to_see_events));
            }
            textViewNoEvents.setVisibility(View.VISIBLE);
            recyclerViewHostedEvents.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            // if (hostedEventList != null) hostedEventList.clear();
            // if (eventsAdapter != null) eventsAdapter.notifyDataSetChanged();
        }
    }

    private void loadHostedEvents() {
        if (currentUser == null || eventsCollectionRef == null) {
            Log.e(TAG, "Cannot load events: User or Firestore collection ref is null.");
            progressBar.setVisibility(View.GONE);
            if (isAdded() && getContext() != null) {
                textViewNoEvents.setText(getString(R.string.error_loading_events));
            }
            textViewNoEvents.setVisibility(View.VISIBLE);
            recyclerViewHostedEvents.setVisibility(View.GONE);
            return;
        }

        // Detach any existing listener before attaching a new one to prevent multiple listeners
        // This is important if loadHostedEvents() can be called multiple times (e.g. on resume or refresh)
        detachFirestoreListener();

        progressBar.setVisibility(View.VISIBLE);
        textViewNoEvents.setVisibility(View.GONE); // Hide "no events" / "loading" message initially
        recyclerViewHostedEvents.setVisibility(View.GONE); // Hide recycler view until data is ready

        // Query to get events where 'organizerId' matches the current user's UID
        // Consider ordering by a timestamp or event date for better UX
        Query hostedEventsQuery = eventsCollectionRef
                .whereEqualTo("organizerId", currentUser.getUid());
        // .orderBy("eventTimestamp", Query.Direction.DESCENDING); // Example: if you have an 'eventTimestamp' field

        hostedEventsListenerRegistration = hostedEventsQuery.addSnapshotListener((snapshots, e) -> {
            progressBar.setVisibility(View.GONE); // Stop progress bar once data is received or error occurs

            if (e != null) {
                Log.e(TAG, "Error loading hosted events from Firestore: ", e);
                if (isAdded() && getContext() != null) {
                    textViewNoEvents.setText(getString(R.string.failed_to_load_hosted_events));
                }
                textViewNoEvents.setVisibility(View.VISIBLE);
                recyclerViewHostedEvents.setVisibility(View.GONE);
                return;
            }

            // TODO: 5. Process snapshots with your Event model and update Adapter
            // if (hostedEventList == null) hostedEventList = new ArrayList<>();
            // hostedEventList.clear();

            if (snapshots != null && !snapshots.isEmpty()) {
                // List<Event> tempList = new ArrayList<>();
                // for (DocumentSnapshot doc : snapshots.getDocuments()) {
                //     Event event = doc.toObject(Event.class); // Make sure Event class is a proper POJO
                //     if (event != null) {
                //         event.setEventId(doc.getId()); // If Event model has a setter for ID and @DocumentId is not used/working
                //         tempList.add(event);
                //         Log.d(TAG, "Event loaded: " + event.getTitle() + " (ID: " + event.getEventId() + ")");
                //     }
                // }
                // Collections.sort(tempList, (e1, e2) -> e2.getEventTimestamp().compareTo(e1.getEventTimestamp())); // Example sort

                // hostedEventList.addAll(tempList);

                // --- TEMPORARY PLACEHOLDER FOR UI UPDATE (Remove when Adapter is ready) ---
                int eventCount = snapshots.size();
                Log.d(TAG, "Number of hosted events (Firestore): " + eventCount);
                if (isAdded() && getContext() != null) {
                    textViewNoEvents.setText(getString(R.string.loaded_events_count_firestore, eventCount));
                    // When adapter is ready, you'll hide textViewNoEvents and show recyclerViewHostedEvents
                    textViewNoEvents.setVisibility(View.VISIBLE); // Keep visible for placeholder
                    recyclerViewHostedEvents.setVisibility(View.GONE); // Keep recycler hidden
                }
                // --- END TEMPORARY PLACEHOLDER ---

                // if (eventsAdapter != null) {
                //     eventsAdapter.notifyDataSetChanged();
                // }
                // textViewNoEvents.setVisibility(View.GONE);
                // recyclerViewHostedEvents.setVisibility(View.VISIBLE);

            } else {
                Log.d(TAG, "No hosted events found for user: " + currentUser.getUid());
                if (isAdded() && getContext() != null) {
                    textViewNoEvents.setText(getString(R.string.no_hosted_events_yet));
                }
                textViewNoEvents.setVisibility(View.VISIBLE);
                recyclerViewHostedEvents.setVisibility(View.GONE);
                // if (hostedEventList != null) hostedEventList.clear();
                // if (eventsAdapter != null) eventsAdapter.notifyDataSetChanged();
            }
        });
        Log.d(TAG, "Attached Firestore listener for hosted events for user: " + currentUser.getUid());
    }

    private void detachFirestoreListener() {
        if (hostedEventsListenerRegistration != null) {
            hostedEventsListenerRegistration.remove();
            hostedEventsListenerRegistration = null;
            Log.d(TAG, "Detached Firestore hostedEventsListener.");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Important: Remove the Firestore listener to prevent memory leaks and unwanted updates
        // when the fragment is not visible.
        detachFirestoreListener();
    }

    // TODO: 6. Define or uncomment your Event model class (POJO for Firestore).
    // Example: (Ensure this is in its own file, e.g., com/example/volunteersApp/models/Event.java)
    /*
    import com.google.firebase.firestore.DocumentId;
    import com.google.firebase.firestore.ServerTimestamp; // For server-side timestamping
    import java.util.Date; // Or com.google.firebase.Timestamp;

    public static class Event { // Should be a public class, not static inner if in its own file
        @DocumentId
        private String eventId; // Field to hold the document ID

        private String organizerId;
        private String eventName;
        // @ServerTimestamp // Automatically populates with server time on creation
        private Date eventTimestamp; // Use Date or com.google.firebase.Timestamp
        private String description;
        private String location;
        // Add other fields: address, category, requiredVolunteers, imageUrl etc.

        public Event() {
            // Default constructor required for calls to DocumentSnapshot.toObject(Event.class)
        }

        // Getters
        public String getEventId() { return eventId; }
        public String getOrganizerId() { return organizerId; } // Corrected getter name
        public String getTitle() { return eventName; }
        public Date getEventTimestamp() { return eventTimestamp; }
        public String getDescription() { return description; }
        public String getLocationName() { return location; }

        // Setters (useful for @DocumentId mapping or manual creation)
        public void setEventId(String eventId) { this.eventId = eventId; }
        public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
        public void setEventName(String eventName) { this.eventName = eventName; }
        public void setEventTimestamp(Date eventTimestamp) { this.eventTimestamp = eventTimestamp; }
        public void setDescription(String description) { this.description = description; }
        public void setLocation(String location) { this.location = location; }
    }
    */
}
