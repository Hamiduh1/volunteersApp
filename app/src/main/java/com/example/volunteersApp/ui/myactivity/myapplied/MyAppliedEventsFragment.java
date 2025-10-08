/** since myvolunteeractivity is gone and


replaced by MyEventsFragment, then this file is not used anywhere


package com.example.volunteersApp.ui.myactivity.myapplied;

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
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment; // Correct for finding NavController in Fragment
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.EventAdapter;
import com.example.volunteersApp.models.EventModel;
// At the top of MyAppliedEventsFragment.java
import com.example.volunteersApp.ui.myactivity.MyVolunteerActivityFragmentDirections; // Or .kt if it's Kotlin
// Import your new ViewModel if you create one:
// import com.example.volunteersApp.viewmodels.MyAppliedEventsViewModel;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage; // Keep if EventAdapter uses it

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MyAppliedEventsFragment extends Fragment implements EventAdapter.OnEventListener {

    private static final String TAG = "MyAppliedEventsFragment";

    private RecyclerView recyclerViewAppliedEvents;
    private ProgressBar progressBarAppliedEvents;
    private TextView textViewStatusAppliedEvents;
    private EventAdapter eventAdapter;
    private SwipeRefreshLayout swipeRefreshLayoutAppliedEvents;

    // Consider moving these to a ViewModel
    private FirebaseFirestore db;
    private FirebaseStorage storage; // Needed if EventAdapter loads images directly
    private FirebaseAuth mAuth;
    private String currentUserId;
    private Set<String> appliedEventIdsForAdapter = new HashSet<>();

    // private MyAppliedEventsViewModel viewModel; // Example if using ViewModel

    public MyAppliedEventsFragment() {
        // Required empty public constructor
    }

    // Static constructor pattern
    public static MyAppliedEventsFragment newInstance() {
        return new MyAppliedEventsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            Log.w(TAG, "User not logged in during onCreate.");
        }

        // Example: Initialize ViewModel
        // viewModel = new ViewModelProvider(this).get(MyAppliedEventsViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Ensure you have R.layout.fragment_applied_events
        View view = inflater.inflate(R.layout.fragment_my_applied_events, container, false);

        recyclerViewAppliedEvents = view.findViewById(R.id.recyclerViewAppliedEvents);
        progressBarAppliedEvents = view.findViewById(R.id.progressBarAppliedEvents);
        textViewStatusAppliedEvents = view.findViewById(R.id.textViewStatusAppliedEvents);
        swipeRefreshLayoutAppliedEvents = view.findViewById(R.id.swipeRefreshLayoutAppliedEvents);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        setupRecyclerView();
        setupSwipeRefreshLayout();

        // If using ViewModel, observe LiveData here
        // observeViewModel();

        if (currentUserId != null && !currentUserId.isEmpty()) {
            Log.d(TAG, "User " + currentUserId + " found. Loading applied events.");
            loadAppliedEvents(); // Or trigger ViewModel to load
        } else {
            Log.w(TAG, "No current user. Cannot load applied events.");
            if (isAdded()) {
                updateUIVisibility(true, R.string.please_log_in_to_see_applied_events, false);
                if (swipeRefreshLayoutAppliedEvents != null) swipeRefreshLayoutAppliedEvents.setEnabled(false);
            }
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null || recyclerViewAppliedEvents == null) {
            Log.e(TAG, "Context or RecyclerView is null in setupRecyclerView.");
            return;
        }
        eventAdapter = new EventAdapter(
                requireContext(),
                false, // isOrganizerView = false
                this,
                appliedEventIdsForAdapter, // Initially empty, populated by loadAppliedEvents
                storage
        );
        recyclerViewAppliedEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewAppliedEvents.setAdapter(eventAdapter);
        Log.d(TAG, "RecyclerView setup.");
    }

    private void setupSwipeRefreshLayout() {
        if (swipeRefreshLayoutAppliedEvents == null) {
            Log.w(TAG, "SwipeRefreshLayout is null.");
            return;
        }
        swipeRefreshLayoutAppliedEvents.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent, R.color.colorPrimaryDark);
        swipeRefreshLayoutAppliedEvents.setOnRefreshListener(() -> {
            Log.d(TAG, "Swipe to refresh triggered.");
            if (currentUserId != null && !currentUserId.isEmpty()) {
                loadAppliedEvents(); // Or viewModel.refreshAppliedEvents();
            } else {
                swipeRefreshLayoutAppliedEvents.setRefreshing(false);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), getString(R.string.login_required_to_refresh), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // This method would be in the ViewModel if you refactor
    private void loadAppliedEvents() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            if (isAdded()) updateUIVisibility(true, R.string.error_user_not_logged_in_applied, false);
            return;
        }
        if (!isAdded()) return;
        Log.d(TAG, "Loading applied events for user: " + currentUserId);
        updateUIVisibility(false, 0, true);

        // Assuming structure users/{userId}/appliedToEvents which stores eventIds or full application details
        db.collection("users").document(currentUserId).collection("appliedToEvents")
                // .orderBy("applicationTimestamp", Query.Direction.DESCENDING) // Optional: order by when they applied
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return;

                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> eventIdsFromUserApplications = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Assuming 'appliedToEvents' documents directly store 'eventId'
                            String eventId = document.getString("eventId");
                            if (eventId == null || eventId.isEmpty()) {
                                // Fallback: if 'appliedToEvents' uses the eventId as the document ID
                                eventId = document.getId();
                                Log.w(TAG, "appliedToEvents doc " + document.getId() + " missing 'eventId', using doc ID.");
                            }
                            if (eventId != null && !eventId.isEmpty()) {
                                eventIdsFromUserApplications.add(eventId);
                            }
                        }

                        this.appliedEventIdsForAdapter.clear();
                        this.appliedEventIdsForAdapter.addAll(eventIdsFromUserApplications);
                        if (eventAdapter != null) {
                            eventAdapter.setAppliedEventIds(new HashSet<>(this.appliedEventIdsForAdapter));
                        }

                        if (eventIdsFromUserApplications.isEmpty()) {
                            Log.d(TAG, "No applied events found for user " + currentUserId);
                            if (eventAdapter != null) eventAdapter.submitList(new ArrayList<>());
                            updateUIVisibility(true, R.string.no_events_applied_for_yet, false);
                        } else {
                            Log.d(TAG, "Found " + eventIdsFromUserApplications.size() + " applied event IDs. Fetching details.");
                            fetchEventDetailsByIds(eventIdsFromUserApplications);
                        }
                    } else {
                        Log.e(TAG, "Error loading applied event IDs: ", task.getException());
                        updateUIVisibility(true, R.string.failed_to_load_applied_events, false);
                    }
                });
    }

    // This method would be in the ViewModel
    private void fetchEventDetailsByIds(List<String> eventIds) {
        if (!isAdded() || eventIds.isEmpty()) {
            if (isAdded()) updateUIVisibility(true, R.string.no_events_applied_for_yet, false);
            return;
        }
        Log.d(TAG, "Fetching details for " + eventIds.size() + " event IDs: " + eventIds);
        // Note: View already shows progress bar from loadAppliedEvents(), no need to set again
        // unless this is a separate loading phase after IDs are fetched.

        final List<EventModel> eventsList = new ArrayList<>();
        // Firestore 'in' query supports up to 30 elements (was 10 in older SDKs)
        final int CHUNK_SIZE = 30;
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < eventIds.size(); i += CHUNK_SIZE) {
            chunks.add(new ArrayList<>(eventIds.subList(i, Math.min(i + CHUNK_SIZE, eventIds.size()))));
        }
        if (chunks.isEmpty()) {
            processFetchedEvents(eventsList); // Should not happen if eventIds was not empty
            return;
        }

        AtomicInteger tasksCompleted = new AtomicInteger(0);
        int totalTasks = chunks.size();

        for (List<String> chunk : chunks) {
            if (chunk.isEmpty()) { // Defensive check
                if (tasksCompleted.incrementAndGet() == totalTasks) processFetchedEvents(eventsList);
                continue;
            }
            db.collection("events").whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (!isAdded()) return;
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                try {
                                    EventModel event = document.toObject(EventModel.class);
                                    // Assuming EventModel has @DocumentId eventId or setter
                                    // if (event.getEventId() == null) event.setEventId(document.getId());
                                    eventsList.add(event);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error converting event doc " + document.getId(), e);
                                }
                            }
                        } else {
                            Log.e(TAG, "Error fetching event details chunk: ", task.getException());
                        }
                        if (tasksCompleted.incrementAndGet() == totalTasks) {
                            processFetchedEvents(eventsList);
                        }
                    });
        }
    }

    private void processFetchedEvents(List<EventModel> events) {
        if (!isAdded()) return;
        Log.d(TAG, "Processing " + events.size() + " fetched event details.");
        // Sort if needed, e.g., by date
        Collections.sort(events, (e1, e2) -> {
            if (e1.getEventDateTime() == null && e2.getEventDateTime() == null) return 0;
            if (e1.getEventDateTime() == null) return 1;
            if (e2.getEventDateTime() == null) return -1;
            return e1.getEventDateTime().compareTo(e2.getEventDateTime()); // Ascending by date
        });

        if (eventAdapter != null) {
            eventAdapter.submitList(new ArrayList<>(events));
        }
        updateUIVisibility(events.isEmpty(),
                events.isEmpty() ? R.string.no_events_applied_for_yet : 0,
                false);
    }

    private void updateUIVisibility(boolean showStatusText, int statusTextResId, boolean showProgress) {
        if (!isAdded() || getView() == null) return;

        if (progressBarAppliedEvents != null) {
            progressBarAppliedEvents.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        }
        if (swipeRefreshLayoutAppliedEvents != null) {
            swipeRefreshLayoutAppliedEvents.setRefreshing(showProgress && swipeRefreshLayoutAppliedEvents.isRefreshing()); // Keep refreshing if it was a swipe
            if (!showProgress) swipeRefreshLayoutAppliedEvents.setRefreshing(false);
        }

        if (showStatusText && !showProgress) {
            if (textViewStatusAppliedEvents != null) {
                textViewStatusAppliedEvents.setText(statusTextResId != 0 ? getString(statusTextResId) : "");
                textViewStatusAppliedEvents.setVisibility(View.VISIBLE);
            }
            if (recyclerViewAppliedEvents != null) recyclerViewAppliedEvents.setVisibility(View.GONE);
        } else if (!showProgress) { // List has items or no specific status to show AND not loading
            if (textViewStatusAppliedEvents != null) textViewStatusAppliedEvents.setVisibility(View.GONE);
            if (recyclerViewAppliedEvents != null) recyclerViewAppliedEvents.setVisibility(View.VISIBLE);
        }
        // If showProgress is true, list and status text are implicitly hidden or managed by progress bar visibility
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        // Optionally refresh data if needed, e.g., if coming back from event detail
        // where application status might have changed.
        // For now, relies on initial load or swipe-to-refresh.
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        if (recyclerViewAppliedEvents != null) {
            recyclerViewAppliedEvents.setAdapter(null);
        }
        // Nullify other view references
        recyclerViewAppliedEvents = null;
        progressBarAppliedEvents = null;
        textViewStatusAppliedEvents = null;
        if (swipeRefreshLayoutAppliedEvents != null) {
            swipeRefreshLayoutAppliedEvents.setOnRefreshListener(null);
        }
        swipeRefreshLayoutAppliedEvents = null;
        eventAdapter = null;
    }

    // --- EventAdapter.OnEventListener Implementation ---
    @Override
    public void onEventClick(EventModel event, int position) {
        if (!isAdded() || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            if (getContext() != null) Toast.makeText(getContext(), getString(R.string.error_opening_event_details_short), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Event clicked from AppliedEvents: " + event.getTitle());
        // Navigate to EventDetailFragment.
        // The action ID here depends on how this fragment is included in the nav graph.
        // If it's a child of MyVolunteerActivityFragment, the action might be defined on the parent.
        // For now, let's assume direct navigation if this fragment were standalone.
        try {
            // This assumes MyAppliedEventsFragment is directly in a graph that has this action.
            // If it's a child in ViewPager, the parent (MyVolunteerActivityFragment) might handle navigation.
            // Or use NavHostFragment.findNavController(this) which should work from child fragments.
           // NavDirections action = MyAppliedEventsFragmentDirections.actionAppliedEventsFragmentToEventDetailFragment(event.getEventId());
            //NavHostFragment.findNavController(this).navigate(action);


            // Use the Directions class of the parent fragment in the navigation graph
            // which is MyVolunteerActivityFragment in this case.
            // The action ID from mobile_navigation.xml is "action_nav_my_activity_to_event_detail"
            NavDirections action = MyVolunteerActivityFragmentDirections.actionNavMyActivityToEventDetail(event.getEventId());
            NavHostFragment.findNavController(this).navigate(action); // 'this' refers to MyAppliedEventsFragment
            //


        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Navigation action not found or incorrect for MyAppliedEventsFragment. Check nav graph.", e);
            // Fallback or alternative navigation if direct action isn't found
            // This might happen if it's used in a ViewPager and the action is on the parent.
            // One option: Use a shared ViewModel or interface to ask the parent fragment to navigate.
            Bundle args = new Bundle();
            args.putString("eventId", event.getEventId());
            // Attempt to navigate using a global action if available, or find parent NavController
            try {
                // Try navigating from parent fragment if this is in a ViewPager.
                // This requires the parent (MyVolunteerActivityFragment) to handle this.
                // For simplicity here, we assume a direct action or a global one.
                NavHostFragment.findNavController(requireParentFragment().requireParentFragment()) // If nested twice (e.g. ViewPager in a Fragment)
                        .navigate(R.id.eventDetailFragment, args); // R.id.eventDetailFragment is the destination ID
            } catch (Exception navEx) {
                Log.e(TAG, "Fallback navigation also failed.", navEx);
                if (getContext() != null) Toast.makeText(getContext(), getString(R.string.error_navigating_to_details_general), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onApplyClick(EventModel event, int position) {
        if (!isAdded() || getContext() == null) return;
        // In this fragment, all events are already "applied".
        // This button in the adapter should ideally show "Applied" or "View Status".
        // Or, it could trigger a "Withdraw EventApplication" flow.
        Log.d(TAG, "Apply button clicked for (already applied) event: " + event.getTitle());
        Toast.makeText(getContext(), getString(R.string.already_applied_to_this_event), Toast.LENGTH_SHORT).show();
        // TODO: Implement "Withdraw" or "View Detailed Status" logic if needed.
        // Example: showWithdrawConfirmationDialog(event);
    }

    @Override
    public void onViewApplicantsClick(EventModel event, int position) {
        if (!isAdded() || getContext() == null) return;
        Toast.makeText(getContext(), R.string.action_for_event_organizers_only, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEventOptionsClicked(EventModel event, View anchorView) {
        if (!isAdded() || getContext() == null) return;
        // Options for an event the user has applied to.
        // Could include "Withdraw EventApplication", "View Details", "Share".
        Toast.makeText(getContext(), getString(R.string.options_for_applied_event, event.getTitle()), Toast.LENGTH_SHORT).show();
        // Example for a PopupMenu:
        // PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        // popup.getMenuInflater().inflate(R.menu.applied_event_item_options_menu, popup.getMenu()); // Define this menu
        // popup.setOnMenuItemClickListener(item -> {return true; });
        // popup.show();
    }

    // Add necessary string resources to strings.xml like:
    // R.string.please_log_in_to_see_applied_events
    // R.string.login_required_to_refresh
    // R.string.error_user_not_logged_in_applied
    // R.string.no_events_applied_for_yet
    // R.string.failed_to_load_applied_events
    // R.string.error_opening_event_details_short
    // R.string.error_navigating_to_details_general
    // R.string.already_applied_to_this_event (can be reused)
    // R.string.status_for_event_prefix (e.g., "Status for")
    // R.string.already_applied_status (e.g., "Applied")
    // R.string.action_for_event_organizers_only (can be reused)
    // R.string.options_for_applied_event (e.g., "Options for %1$s")
}
**/

