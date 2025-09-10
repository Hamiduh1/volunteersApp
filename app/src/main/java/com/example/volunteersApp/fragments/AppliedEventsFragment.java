package com.example.volunteersApp.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
// import android.widget.PopupMenu; // Keep if you plan to use options
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

// CRITICAL: Ensure this is the correct EventModel import
import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.adapters.EventAdapter;
import com.example.volunteersApp.R; // For string resources

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// DocumentSnapshot not directly used, QueryDocumentSnapshot is for results
// import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException; // For error handling if needed
import com.google.firebase.firestore.Query; // For ordering if you fetch directly
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fragment to display a list of events the user has applied for.
 */
public class AppliedEventsFragment extends Fragment implements EventAdapter.OnEventListener {

    private static final String TAG = "AppliedEventsFragment";

    private RecyclerView recyclerViewAppliedEvents;
    private ProgressBar progressBarAppliedEvents;
    private TextView textViewStatusAppliedEvents;
    private EventAdapter eventAdapter;
    private SwipeRefreshLayout swipeRefreshLayoutAppliedEvents;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth mAuth; // Added for consistency, though only currentUserId used from it directly
    private String currentUserId;

    // This set is passed to the adapter to help it determine the "applied" state of buttons.
    // In "Applied Events" view, all events shown are technically "applied".
    private Set<String> appliedEventIdsForAdapter = new HashSet<>();

    public AppliedEventsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance(); // Initialize FirebaseAuth
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            Log.w(TAG, "User not logged in during onCreate. Applied events may not load.");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_applied_events, container, false);

        recyclerViewAppliedEvents = view.findViewById(R.id.recyclerViewAppliedEvents);
        progressBarAppliedEvents = view.findViewById(R.id.progressBarAppliedEvents);
        textViewStatusAppliedEvents = view.findViewById(R.id.textViewStatusAppliedEvents);
        swipeRefreshLayoutAppliedEvents = view.findViewById(R.id.swipeRefreshLayoutAppliedEvents);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupSwipeRefreshLayout();

        if (currentUserId != null && !currentUserId.isEmpty()) {
            Log.d(TAG, "User " + currentUserId + " found. Loading applied events.");
            loadAppliedEvents();
        } else {
            Log.w(TAG, "No current user found in onViewCreated. Cannot load applied events.");
            if (isAdded()) {
                updateUIVisibility(true, R.string.please_log_in_to_see_applied_events, false);
                if (swipeRefreshLayoutAppliedEvents != null) swipeRefreshLayoutAppliedEvents.setEnabled(false);
            }
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in setupRecyclerView. Cannot initialize EventAdapter.");
            return;
        }

        // For AppliedEventsFragment, isOrganizerView is false.
        // All events in this list are ones the user has applied to, so they are all "applied".
        // The appliedEventIdsForAdapter will contain all event IDs shown in this list.
        eventAdapter = new EventAdapter(
                requireContext(),
                false, // isOrganizerView = false for applied events
                this,  // OnEventListener (this fragment)
                appliedEventIdsForAdapter, // Initially empty, will be populated
                storage
        );
        recyclerViewAppliedEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewAppliedEvents.setAdapter(eventAdapter);
    }

    private void setupSwipeRefreshLayout() {
        if (swipeRefreshLayoutAppliedEvents == null) {
            Log.w(TAG, "SwipeRefreshLayout is null. Cannot set listener.");
            return;
        }
        swipeRefreshLayoutAppliedEvents.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent, R.color.colorPrimaryDark);
        swipeRefreshLayoutAppliedEvents.setOnRefreshListener(() -> {
            Log.d(TAG, "Swipe to refresh triggered for Applied Events.");
            if (currentUserId != null && !currentUserId.isEmpty()) {
                loadAppliedEvents();
            } else {
                Log.w(TAG, "Cannot refresh: User ID is null.");
                swipeRefreshLayoutAppliedEvents.setRefreshing(false);
                if (getContext() != null && isAdded()) {
                    Toast.makeText(getContext(), getString(R.string.login_required_to_refresh), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadAppliedEvents() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "CurrentUser ID is null or empty, cannot fetch applied events.");
            if (isAdded()) {
                updateUIVisibility(true, R.string.error_user_not_logged_in_applied, false);
            }
            return;
        }
        if (!isAdded()) {
            Log.w(TAG, "Fragment not added. Cannot load applied events.");
            return;
        }

        updateUIVisibility(false, 0, true); // Show progress bar

        // Assuming structure users/{userId}/appliedToEvents/{eventId_or_docId_containing_eventId}
        db.collection("users").document(currentUserId).collection("appliedToEvents")
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return; // Check if fragment is still attached

                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> fetchedEventIdsFromApplications = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Determine how eventId is stored in 'appliedToEvents' documents
                            String eventId = document.getString("eventId"); // Preferred if this field exists
                            if (eventId == null || eventId.isEmpty()) {
                                // Fallback: If eventId field is missing, assume the document ID *is* the eventId
                                eventId = document.getId();
                                Log.w(TAG, "Document " + document.getId() + " in appliedToEvents missing 'eventId' field, using document ID as fallback.");
                            }
                            if (eventId != null && !eventId.isEmpty()) {
                                fetchedEventIdsFromApplications.add(eventId);
                            }
                        }

                        // Update the set used by the adapter. All events here are "applied".
                        this.appliedEventIdsForAdapter.clear();
                        this.appliedEventIdsForAdapter.addAll(fetchedEventIdsFromApplications);
                        if (eventAdapter != null) {
                            // Ensure the adapter uses this updated set.
                            // The adapter's logic for "hasApplied" will mark these events accordingly.
                            eventAdapter.setAppliedEventIds(new HashSet<>(this.appliedEventIdsForAdapter));
                        }

                        if (fetchedEventIdsFromApplications.isEmpty()) {
                            Log.d(TAG, "User " + currentUserId + " has not applied to any events.");
                            if (eventAdapter != null) eventAdapter.submitList(new ArrayList<>());
                            updateUIVisibility(true, R.string.no_events_applied_for_yet, false);
                        } else {
                            Log.d(TAG, "User " + currentUserId + " applied to " + fetchedEventIdsFromApplications.size() + " events. Fetching details.");
                            fetchEventDetailsByIds(fetchedEventIdsFromApplications);
                        }
                    } else {
                        Log.e(TAG, "Error loading applied event IDs for user " + currentUserId + ": ", task.getException());
                        updateUIVisibility(true, R.string.failed_to_load_applied_events, false);
                    }
                });
    }

    private void fetchEventDetailsByIds(List<String> eventIds) {
        if (!isAdded()) {
            Log.w(TAG, "Fragment not added. Cannot fetch event details.");
            return;
        }
        if (eventIds.isEmpty()) {
            if (eventAdapter != null) eventAdapter.submitList(new ArrayList<>());
            updateUIVisibility(true, R.string.no_events_applied_for_yet, false);
            Log.d(TAG, "fetchEventDetailsByIds called with empty list.");
            return;
        }

        Log.d(TAG, "Fetching details for event IDs: " + eventIds);
        final List<EventModel> fetchedEventsList = new ArrayList<>();
        // Firestore 'in' query supports up to 30 elements in an array for 'whereIn' (as of Firestore SDK 24.x)
        // For older SDKs or very large lists, it was 10. Max is 30.
        final int CHUNK_SIZE = 30;
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < eventIds.size(); i += CHUNK_SIZE) {
            chunks.add(new ArrayList<>(eventIds.subList(i, Math.min(i + CHUNK_SIZE, eventIds.size()))));
        }

        if (chunks.isEmpty()) { // Should not happen if eventIds was not empty
            if (eventAdapter != null) eventAdapter.submitList(new ArrayList<>());
            updateUIVisibility(true, R.string.no_events_applied_for_yet, false);
            return;
        }

        AtomicInteger tasksCompletedCounter = new AtomicInteger(0);
        int totalTasks = chunks.size();

        for (List<String> chunk : chunks) {
            if (chunk.isEmpty()) { // Should not happen with the chunking logic
                if (tasksCompletedCounter.incrementAndGet() == totalTasks) {
                    processFetchedEvents(fetchedEventsList);
                }
                continue;
            }

            // Querying 'events' collection. Assuming eventId in the 'chunk' list corresponds to
            // the document ID in the 'events' collection OR a field named 'eventId'.
            // If @DocumentId is used in EventModel, matching document ID is simpler.
            // Otherwise, ensure your 'events' documents have a field matching the IDs from 'appliedToEvents'.
            // Using FieldPath.documentId() is safer if IDs in 'chunk' are Firestore document IDs.
            db.collection("events")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk) // Query by document ID
                    .get()
                    .addOnCompleteListener(task -> {
                        if (!isAdded()) return;

                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                try {
                                    EventModel event = document.toObject(EventModel.class);
                                    // Event ID should be set by @DocumentId in EventModel
                                    // if (event.getEventId() == null || event.getEventId().isEmpty()) {
                                    //     event.setEventId(document.getId());
                                    // }
                                    fetchedEventsList.add(event);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error converting document " + document.getId() + " to EventModel: ", e);
                                }
                            }
                        } else {
                            Log.e(TAG, "Error fetching chunk of event details: ", task.getException());
                        }

                        if (tasksCompletedCounter.incrementAndGet() == totalTasks) {
                            processFetchedEvents(fetchedEventsList);
                        }
                    });
        }
    }

    private void processFetchedEvents(List<EventModel> finalFetchedEventsList) {
        if (!isAdded()) {
            Log.w(TAG, "Fragment not added. Cannot process fetched events.");
            return;
        }
        Log.d(TAG, "Processing " + finalFetchedEventsList.size() + " fetched event details.");

        // Sort events, for example, by their event date (soonest first or last)
        // CORRECTED: Use getEventDateTime()
        Collections.sort(finalFetchedEventsList, (e1, e2) -> {
            if (e1.getEventDateTime() == null && e2.getEventDateTime() == null) return 0;
            if (e1.getEventDateTime() == null) return 1; // Nulls last
            if (e2.getEventDateTime() == null) return -1; // Nulls last
            // For descending order (newest first), reverse e1 and e2
            // return e2.getEventDateTime().compareTo(e1.getEventDateTime());
            // For ascending order (oldest/soonest first)
            return e1.getEventDateTime().compareTo(e2.getEventDateTime());
        });

        if (eventAdapter != null) {
            eventAdapter.submitList(new ArrayList<>(finalFetchedEventsList)); // Submit a new list
        }

        updateUIVisibility(finalFetchedEventsList.isEmpty(),
                finalFetchedEventsList.isEmpty() ? R.string.no_events_applied_for_yet : 0,
                false); // False because loading is complete
    }

    private void updateUIVisibility(boolean listIsEmpty, int emptyListMessageResId, boolean showProgressBar) {
        if (!isAdded() || getView() == null) { // Check getView() as well
            Log.w(TAG, "Fragment not fully initialized. Cannot update UI visibility.");
            return;
        }

        if (progressBarAppliedEvents != null) {
            progressBarAppliedEvents.setVisibility(showProgressBar ? View.VISIBLE : View.GONE);
        }

        // Manage SwipeRefreshLayout's refreshing state
        if (swipeRefreshLayoutAppliedEvents != null) {
            if (showProgressBar) { // If explicitly showing progress bar, swipe might not be active
                // swipeRefreshLayoutAppliedEvents.setRefreshing(false); // Or true if this call implies refresh
            } else {
                swipeRefreshLayoutAppliedEvents.setRefreshing(false); // Always stop if not showing central progress
            }
        }


        if (listIsEmpty && !showProgressBar) {
            if (textViewStatusAppliedEvents != null) {
                textViewStatusAppliedEvents.setText(emptyListMessageResId != 0 ? getString(emptyListMessageResId) : "");
                textViewStatusAppliedEvents.setVisibility(View.VISIBLE);
            }
            if (recyclerViewAppliedEvents != null) recyclerViewAppliedEvents.setVisibility(View.GONE);
        } else if (!showProgressBar) { // List is not empty AND not showing progress bar
            if (textViewStatusAppliedEvents != null) textViewStatusAppliedEvents.setVisibility(View.GONE);
            if (recyclerViewAppliedEvents != null) recyclerViewAppliedEvents.setVisibility(View.VISIBLE);
        }
        // If showProgressBar is true, the list and status text are typically hidden by the progress bar's presence.
    }

    @Override
    public void onResume() {
        super.onResume();
        // Consider if a refresh is needed, e.g., if user navigated away and back
        // and an application status might have changed.
        // For simplicity, current logic relies on swipe-to-refresh or initial load.
        // if (currentUserId != null && !currentUserId.isEmpty() && eventAdapter != null && eventAdapter.getItemCount() == 0) {
        //     loadAppliedEvents(); // Re-load if list is empty and user is logged in
        // }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView called for AppliedEventsFragment.");
        if (recyclerViewAppliedEvents != null) {
            recyclerViewAppliedEvents.setAdapter(null); // Important for RecyclerView
        }
        // Nullify views to help GC and prevent memory leaks
        recyclerViewAppliedEvents = null;
        progressBarAppliedEvents = null;
        textViewStatusAppliedEvents = null;
        if (swipeRefreshLayoutAppliedEvents != null) {
            swipeRefreshLayoutAppliedEvents.setOnRefreshListener(null);
        }
        swipeRefreshLayoutAppliedEvents = null;
        eventAdapter = null;
    }

    // --- Implementation of EventAdapter.OnEventListener ---

    @Override
    public void onEventClick(EventModel event, int position) {
        if (!isAdded() || getContext() == null || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            Log.w(TAG, "onEventClick: prerequisites not met or eventId is null/empty.");
            if (getContext() != null) Toast.makeText(getContext(), getString(R.string.error_opening_event_details_short), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Clicked on applied event: " + event.getTitle() + " (ID: " + event.getEventId() + ")");

        try {
            NavDirections action = AppliedEventsFragmentDirections.actionAppliedEventsFragmentToEventDetailFragment(event.getEventId());
            NavHostFragment.findNavController(this).navigate(action);
        } catch (Exception e) { // Catch general exceptions
            Log.e(TAG, "Navigation to event details failed for event: " + event.getEventId(), e);
            if (getContext() != null) Toast.makeText(getContext(), getString(R.string.error_navigating_to_details_general), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onApplyClick(EventModel event, int position) {
        if (!isAdded() || getContext() == null || event == null) return;
        Log.d(TAG, "Apply button clicked for (already applied) event: " + event.getTitle());
        // In "Applied Events", this button is shown as "Applied" and disabled by the adapter.
        // If a different action is desired (e.g., "View Status" or "Withdraw"),
        // the EventAdapter logic and this callback would need adjustment.
        Toast.makeText(getContext(), getString(R.string.status_for_event_prefix) + " " + event.getTitle() + ": " + getString(R.string.already_applied_status), Toast.LENGTH_SHORT).show();
        // TODO: Could navigate to a detailed application status page if one exists.
    }

    @Override
    public void onViewApplicantsClick(EventModel event, int position) {
        if (!isAdded() || event == null) return;
        Log.d(TAG, "View Applicants clicked for " + event.getTitle() + " in AppliedEventsFragment - this is for organizers.");
        if (getContext() != null) {
            Toast.makeText(getContext(), R.string.action_for_event_organizers_only, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEventOptionsClicked(EventModel event, View anchorView) {
        if (!isAdded() || getContext() == null || event == null) return;
        Log.d(TAG, "Event options clicked for (applied event): " + event.getTitle());
        // The adapter has isOrganizerView=false, so this options icon shouldn't be visible by default.
        // If you intended options here (e.g., "Withdraw Application"):
        // 1. Ensure the options icon is visible in item_event.xml even for non-organizers OR
        // 2. Add a different button/icon for volunteer-specific actions.
        // Example:
        // PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        // popup.getMenuInflater().inflate(R.menu.applied_event_item_options_menu, popup.getMenu()); // Create this menu
        // popup.setOnMenuItemClickListener(item -> {
        //     if (item.getItemId() == R.id.action_withdraw_application_applied_menu) { // Example ID
        //         // showWithdrawConfirmationDialog(event);
        //         Toast.makeText(getContext(), "Withdraw option selected for " + event.getTitle(), Toast.LENGTH_SHORT).show();
        //         return true;
        //     }
        //     return false;
        // });
        // popup.show();
        Toast.makeText(getContext(),getString(R.string.options_for_applied_event, event.getTitle()), Toast.LENGTH_SHORT).show();
    }
}


