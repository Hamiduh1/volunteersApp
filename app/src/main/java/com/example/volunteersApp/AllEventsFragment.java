/**  You don't need AllEventsFragment.java in its current form. Its core
purpose (showing a list of events) is already handled by the VolunteeringFragment.
 java we just updated. The AllEventsFragment is a redundant and outdated file.


package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// EventDetailActivity, EventModel, and VolunteerEventItem are necessary
// VolunteerEventAdapter is also necessary
import com.example.volunteersApp.ui.eventdetails.EventDetailActivity;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.volunteersApp.models.EventModel;

import java.util.ArrayList;
import java.util.List;

// Changed to implement OnItemClickListener with VolunteerEventItem
public class AllEventsFragment extends Fragment implements VolunteerEventAdapter.OnItemClickListener<VolunteerEventItem> {
    private static final String TAG = "AllEventsFragment";

    private RecyclerView recyclerViewAllEvents;
    private ProgressBar progressBarAllEvents;
    private TextView textViewStatusAllEvents;
    // Changed: Adapter declaration is no longer generic here as the Adapter class itself is typed
    private VolunteerEventAdapter volunteerEventAdapter;

    private FirebaseFirestore db;
    private CollectionReference eventsCollectionRef;
    private ListenerRegistration eventsListenerRegistration;

    private static final String EVENTS_COLLECTION_NAME = "events";
    private static final String FIELD_ORDER_BY = "eventTimestamp";
    private static final Query.Direction ORDER_DIRECTION = Query.Direction.DESCENDING;

    public AllEventsFragment() {
        // Required empty public constructor
    }

    public static AllEventsFragment newInstance() {
        return new AllEventsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Initializing Firestore instance.");
        db = FirebaseFirestore.getInstance();
        eventsCollectionRef = db.collection(EVENTS_COLLECTION_NAME);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: Inflating layout fragment_all_events.");
        View view = inflater.inflate(R.layout.fragment_all_events, container, false);

        recyclerViewAllEvents = view.findViewById(R.id.recyclerViewAllEventsList);
        progressBarAllEvents = view.findViewById(R.id.progressBarAllEventsList);
        textViewStatusAllEvents = view.findViewById(R.id.textViewStatusAllEventsList);

        if (recyclerViewAllEvents == null || progressBarAllEvents == null || textViewStatusAllEvents == null) {
            Log.e(TAG, "onCreateView: One or more views not found. Check layout IDs in fragment_all_events.xml.");
            // Consider returning view or throwing an exception to prevent further execution with null views
        }

        setupRecyclerView();
        return view;
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "setupRecyclerView: Context is null. Cannot initialize adapter.");
            return;
        }
        // Changed: Instantiating the non-generic adapter.
        // VolunteerEventAdapter is designed to work with VolunteerEventItem.
        Log.d(TAG, "setupRecyclerView: Initializing VolunteerEventAdapter for VolunteerEventItem.");
        volunteerEventAdapter = new VolunteerEventAdapter(); // No type parameter or diamond operator needed here
        volunteerEventAdapter.setOnItemClickListener(this); // 'this' now matches OnItemClickListener<VolunteerEventItem>

        recyclerViewAllEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewAllEvents.setAdapter(volunteerEventAdapter);
        Log.d(TAG, "setupRecyclerView: RecyclerView adapter set.");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Starting to listen for all events.");
        startListeningForAllEvents();
    }

    private void startListeningForAllEvents() {
        if (eventsCollectionRef == null) {
            Log.e(TAG, "startListeningForAllEvents: Firestore collection reference is null.");
            if (isAdded()) { // Check if fragment is added to an activity
                showError(getString(R.string.error_loading_events_ref_null));
            }
            return;
        }

        showLoading();

        if (eventsListenerRegistration != null) {
            Log.d(TAG, "startListeningForAllEvents: Removing previous listener.");
            eventsListenerRegistration.remove();
        }

        Query firestoreQuery = eventsCollectionRef.orderBy(FIELD_ORDER_BY, ORDER_DIRECTION);

        Log.d(TAG, "startListeningForAllEvents: Attaching snapshot listener.");
        eventsListenerRegistration = firestoreQuery.addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (!isAdded()) { // Check if fragment is still added before processing
                Log.w(TAG, "Snapshot listener callback received but fragment is not attached.");
                return;
            }
            if (e != null) {
                Log.w(TAG, "startListeningForAllEvents: Listen error", e);
                showError(getString(R.string.failed_to_load_events_from_db) + ": " + e.getMessage());
                return;
            }

            if (queryDocumentSnapshots != null) {
                Log.d(TAG, "startListeningForAllEvents: Received snapshot with " + queryDocumentSnapshots.size() + " documents.");
                List<EventModel> schemaList = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        EventModel eventSchema = document.toObject(EventModel.class);
                        if (eventSchema != null) { // Basic null check for the deserialized object
                            // Ensure eventId is set, crucial for DiffUtil and click handling.
                            if (eventSchema.getEventId() == null || eventSchema.getEventId().trim().isEmpty()) {
                                eventSchema.setEventId(document.getId());
                            }

                            // Basic validation (optional, can be more complex)
                            if (eventSchema.getTitle() == null || eventSchema.getTitle().trim().isEmpty()) {
                                Log.w(TAG, "Event with ID " + document.getId() + " has a missing or empty name. Processing anyway or skipping.");
                                // Consider `continue;` if such items should be skipped
                            }
                            schemaList.add(eventSchema);
                            Log.v(TAG, "Processed event schema: " + eventSchema.getTitle() + " (ID: " + eventSchema.getEventId() + ")");
                        } else {
                            Log.w(TAG, "Document " + document.getId() + " deserialized to null EventModel.");
                        }
                    } catch (Exception parseEx) {
                        Log.e(TAG, "Error parsing document to EventModel: " + document.getId(), parseEx);
                    }
                }

                // Convert List<EventModel> to List<VolunteerEventItem>
                List<VolunteerEventItem> eventItemList = new ArrayList<>();
                for (EventModel schema : schemaList) {
                    String eventId = schema.getEventId();
                    String organizerId = schema.getOrganizerId(); // Or determine appropriately for "All Events"

                    // It's critical that VolunteerEventItem receives non-null IDs if your DiffUtil relies on them.
                    if (eventId == null) {
                        Log.e(TAG, "Cannot create VolunteerEventItem: eventId is null for event named: " + schema.getTitle());
                        continue; // Skip this item
                    }
                    if (organizerId == null) {
                        // Decide how to handle null organizerId. DiffUtil might need a consistent value.
                        // Using eventId as a fallback or a generic placeholder.
                        organizerId = "all_events_placeholder_host";
                        Log.w(TAG, "organizerId is null for eventId: " + eventId + ". Using placeholder.");
                    }

                    eventItemList.add(new VolunteerEventItem(eventId, organizerId, schema));
                }

                if (volunteerEventAdapter != null) {
                    volunteerEventAdapter.submitList(eventItemList); // Submit the correct list type
                    Log.d(TAG, "Submitted " + eventItemList.size() + " VolunteerEventItems to Adapter.");
                } else {
                    Log.e(TAG, "VolunteerEventAdapter is null, cannot submit list.");
                }
                updateUIVisibility(eventItemList.isEmpty());
            } else {
                Log.d(TAG, "startListeningForAllEvents: Query snapshot was null.");
                if (volunteerEventAdapter != null) {
                    volunteerEventAdapter.submitList(new ArrayList<>()); // Submit empty list of VolunteerEventItem
                }
                updateUIVisibility(true);
            }
        });
    }

    private void showLoading() {
        if (!isAdded()) return;
        Log.d(TAG, "showLoading: Displaying progress bar.");
        if (progressBarAllEvents != null) progressBarAllEvents.setVisibility(View.VISIBLE);
        if (textViewStatusAllEvents != null) textViewStatusAllEvents.setVisibility(View.GONE);
        if (recyclerViewAllEvents != null) recyclerViewAllEvents.setVisibility(View.GONE);
    }

    private void updateUIVisibility(boolean listIsEmpty) {
        if (!isAdded()) return;
        Log.d(TAG, "updateUIVisibility: List is empty: " + listIsEmpty);
        if (progressBarAllEvents != null) progressBarAllEvents.setVisibility(View.GONE);

        if (listIsEmpty) {
            if (textViewStatusAllEvents != null) {
                textViewStatusAllEvents.setText(getString(R.string.no_events_available_global));
                textViewStatusAllEvents.setVisibility(View.VISIBLE);
            }
            if (recyclerViewAllEvents != null) recyclerViewAllEvents.setVisibility(View.GONE);
        } else {
            if (textViewStatusAllEvents != null) textViewStatusAllEvents.setVisibility(View.GONE);
            if (recyclerViewAllEvents != null) recyclerViewAllEvents.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message) {
        if (!isAdded()) return;
        Log.e(TAG, "showError: " + message);
        if (progressBarAllEvents != null) progressBarAllEvents.setVisibility(View.GONE);
        if (textViewStatusAllEvents != null) {
            textViewStatusAllEvents.setText(message);
            textViewStatusAllEvents.setVisibility(View.VISIBLE);
        }
        if (recyclerViewAllEvents != null) recyclerViewAllEvents.setVisibility(View.GONE);
    }

    // Changed: Parameter is now VolunteerEventItem
    @Override
    public void onItemClick(VolunteerEventItem volunteerEventItem) {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "onItemClick: Fragment not attached or context is null.");
            return;
        }

        if (volunteerEventItem == null || volunteerEventItem.getEventDetails() == null) {
            Log.w(TAG, "onItemClick: Clicked on a null VolunteerEventItem or item with null details.");
            Toast.makeText(getContext(), R.string.error_opening_event_details_null, Toast.LENGTH_SHORT).show();
            return;
        }

        EventModel eventDetails = volunteerEventItem.getEventDetails(); // Extract EventModel

        Log.d(TAG, "onItemClick: Clicked on event: " + eventDetails.getTitle() + " (ID: " + eventDetails.getEventId() + ")");

        if (eventDetails.getEventId() == null || eventDetails.getEventId().trim().isEmpty()) {
            Log.e(TAG, "onItemClick: Event ID is null or empty, cannot open details for event: " + eventDetails.getTitle());
            Toast.makeText(getContext(), R.string.event_id_missing_error, Toast.LENGTH_SHORT).show(); // Use a string resource
            return;
        }

        Toast.makeText(getContext(),getString(R.string.opening_event_toast, eventDetails.getTitle()), Toast.LENGTH_SHORT).show(); // Use a string resource

        Intent intent = new Intent(getActivity(), EventDetailActivity.class);
        intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, eventDetails.getEventId());
        // If EventModel is Parcelable and EventDetailActivity is set up to receive it:
        // intent.putExtra(EventDetailActivity.EXTRA_EVENT_OBJECT, eventDetails);
        startActivity(intent);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Removing Firestore events listener if active.");
        if (eventsListenerRegistration != null) {
            eventsListenerRegistration.remove();
            eventsListenerRegistration = null; // Important to nullify to prevent reuse of removed listener
            Log.d(TAG, "onStop: Firestore events listener removed.");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Releasing view references.");
        // It's good practice to null out views and adapter in onDestroyView
        recyclerViewAllEvents = null;
        progressBarAllEvents = null;
        textViewStatusAllEvents = null;
        if (volunteerEventAdapter != null) {
            // If your adapter holds any resources that need explicit release, do it here.
            // For ListAdapter, typically not much is needed beyond nulling the reference.
            volunteerEventAdapter = null;
        }
        Log.d(TAG, "onDestroyView: Views and adapter reference nulled.");
    }
}
**/