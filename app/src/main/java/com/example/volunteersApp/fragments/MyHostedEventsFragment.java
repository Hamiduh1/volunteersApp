package com.example.volunteersApp.fragments;

// Correct imports for EventModel and EventAdapter
import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.EventAdapter;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
// import android.view.MenuItem; // Not directly used in current logic flow
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.Collections; // For potential client-side sorting if needed later
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyHostedEventsFragment extends Fragment {

    private static final String TAG = "MyHostedEventsFragment";

    private RecyclerView recyclerViewMyHostedEvents;
    private ProgressBar progressBarMyHostedEvents;
    private TextView textViewStatusMyHostedEvents;
    private EventAdapter eventAdapter;
    private SwipeRefreshLayout swipeRefreshLayoutMyHostedEvents;
    private FloatingActionButton fabAddEvent;

    private FirebaseFirestore db;
    private FirebaseStorage firebaseStorage;
    private FirebaseAuth mAuth;
    private String currentUserId;

    // This is passed to the EventAdapter. For MyHostedEvents, it's typically empty
    // as the organizer isn't "applying" to their own events in this view.
    private Set<String> appliedEventIdsForAdapter = new HashSet<>();

    public MyHostedEventsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            Log.w(TAG, "User not logged in during onCreate of MyHostedEventsFragment.");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_hosted_events, container, false);

        recyclerViewMyHostedEvents = view.findViewById(R.id.recyclerViewMyHostedEvents);
        progressBarMyHostedEvents = view.findViewById(R.id.progressBarMyHostedEvents);
        textViewStatusMyHostedEvents = view.findViewById(R.id.textViewStatusMyHostedEvents);
        swipeRefreshLayoutMyHostedEvents = view.findViewById(R.id.swipeRefreshLayoutMyHostedEvents);
        fabAddEvent = view.findViewById(R.id.fabAddEvent);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupSwipeRefreshLayout();
        setupFab();

        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadHostedEvents();
        } else {
            Log.w(TAG, "No current user ID in onViewCreated. Cannot load hosted events.");
            if (isAdded() && getContext() != null) {
                updateUIVisibility(true, R.string.please_log_in_to_see_hosted_events, false);
                if (swipeRefreshLayoutMyHostedEvents != null) swipeRefreshLayoutMyHostedEvents.setEnabled(false);
                if (fabAddEvent != null) fabAddEvent.setVisibility(View.GONE);
            }
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in setupRecyclerView. Cannot initialize EventAdapter.");
            return;
        }

        EventAdapter.OnEventListener eventListener = new EventAdapter.OnEventListener() {
            @Override
            public void onEventClick(EventModel event, int position) {
                if (!isAdded() || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
                    Log.e(TAG, "Cannot navigate to details: Event data invalid or fragment not added.");
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_opening_event_details, Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.d(TAG, "Clicked on hosted event: " + event.getTitle());
                try {
                    NavDirections action = MyHostedEventsFragmentDirections.actionMyHostedEventsFragmentToEventDetailFragment(event.getEventId());
                    NavHostFragment.findNavController(MyHostedEventsFragment.this).navigate(action);
                } catch (Exception e) { // Catch broader exceptions
                    Log.e(TAG, "Navigation to event details failed: ", e);
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_navigating_to_details_general, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onApplyClick(EventModel event, int position) {
                // This button should be hidden by the adapter logic when isOrganizerView is true.
                Log.d(TAG, "onApplyClick (unexpected for hosted event): " + (event != null ? event.getTitle() : "null event"));
                if (event == null || event.getEventId() == null || event.getEventId().isEmpty() || !isAdded()) {
                    Log.e(TAG, "Cannot edit from onApplyClick: Event data missing or fragment not added.");
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_event_data_missing_for_edit, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Fallback: If adapter shows this, treat as "Edit"
                try {
                    NavDirections action = MyHostedEventsFragmentDirections.actionMyHostedEventsFragmentToNavHostEvent(event.getEventId());
                    NavHostFragment.findNavController(MyHostedEventsFragment.this).navigate(action);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation to edit event screen (from onApplyClick) failed: ", e);
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_navigating_to_edit_screen, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onViewApplicantsClick(EventModel event, int position) {
                if (!isAdded() || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
                    Log.e(TAG, "Cannot view applicants: Event data invalid or fragment not added.");
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_event_data_missing_for_applicants, Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.d(TAG, "View Applicants clicked for: " + event.getTitle());
                try {
                    NavDirections action = MyHostedEventsFragmentDirections.actionMyHostedEventsFragmentToViewApplicantsFragment(event.getEventId());
                    NavHostFragment.findNavController(MyHostedEventsFragment.this).navigate(action);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation to view applicants failed: ", e);
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_opening_applicants_screen, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEventOptionsClicked(EventModel event, View anchorView) {
                if (!isAdded() || getContext() == null || event == null) {
                    Log.w(TAG, "onEventOptionsClicked: Fragment not attached, context null, or event null.");
                    return;
                }
                Log.d(TAG, "Event options clicked for (hosted event): " + event.getTitle());

                PopupMenu popup = new PopupMenu(requireContext(), anchorView);
                popup.getMenuInflater().inflate(R.menu.organizer_event_item_options, popup.getMenu());

                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (event.getEventId() == null || event.getEventId().isEmpty()) {
                        Toast.makeText(getContext(), R.string.error_event_id_missing, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    if (itemId == R.id.action_edit_event) {
                        Log.d(TAG, "Edit option selected for event: " + event.getTitle());
                        try {
                            NavDirections action = MyHostedEventsFragmentDirections.actionMyHostedEventsFragmentToNavHostEvent(event.getEventId());
                            NavHostFragment.findNavController(MyHostedEventsFragment.this).navigate(action);
                        } catch (Exception e) {
                            Log.e(TAG, "Navigation to edit event screen failed from options menu: ", e);
                            Toast.makeText(getContext(), R.string.error_opening_edit_screen, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    } else if (itemId == R.id.action_delete_event) {
                        Log.d(TAG, "Delete option selected for event: " + event.getTitle());
                        // TODO: Implement delete confirmation dialog and actual deletion logic.
                        // Example: new AlertDialog.Builder(requireContext())... show();
                        // Then call a method in a ViewModel: eventViewModel.deleteHostedEvent(event.getEventId());
                        // And then refresh the list: loadHostedEvents();
                        Toast.makeText(getContext(),getString(R.string.delete_for_event_implement, event.getTitle()), Toast.LENGTH_LONG).show();
                        return true;
                    } else if (itemId == R.id.action_view_event_analytics) {
                        Log.d(TAG, "View analytics option selected for event: " + event.getTitle());
                        // TODO: Navigate to an analytics screen for this event
                        Toast.makeText(getContext(),getString(R.string.analytics_for_event_implement, event.getTitle()), Toast.LENGTH_LONG).show();
                        return true;
                    }
                    return false;
                });
                popup.show();
            }
        };

        eventAdapter = new EventAdapter(
                requireContext(),
                true,               // isOrganizerView is true for this fragment
                eventListener,
                appliedEventIdsForAdapter, // Pass the empty set
                firebaseStorage
        );
        recyclerViewMyHostedEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewMyHostedEvents.setAdapter(eventAdapter);
    }

    private void setupSwipeRefreshLayout() {
        if (swipeRefreshLayoutMyHostedEvents == null) {
            Log.w(TAG, "SwipeRefreshLayout is null. Cannot set listener.");
            return;
        }
        swipeRefreshLayoutMyHostedEvents.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent, R.color.colorPrimaryDark);
        swipeRefreshLayoutMyHostedEvents.setOnRefreshListener(() -> {
            Log.d(TAG, "Swipe to refresh triggered for My Hosted Events.");
            if (currentUserId != null && !currentUserId.isEmpty()) {
                loadHostedEvents();
            } else {
                Log.w(TAG, "Cannot refresh hosted events: User ID is null.");
                swipeRefreshLayoutMyHostedEvents.setRefreshing(false);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), R.string.login_required_to_refresh, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupFab() {
        if (fabAddEvent == null) {
            Log.w(TAG, "FloatingActionButton is null. Cannot set listener.");
            return;
        }
        fabAddEvent.setOnClickListener(v -> {
            Log.d(TAG, "FAB clicked to add a new event.");
            if (currentUserId != null && !currentUserId.isEmpty()) {
                try {
                    // Pass null for eventId to indicate creating a new event
                    NavDirections action = MyHostedEventsFragmentDirections.actionMyHostedEventsFragmentToNavHostEvent(null);
                    NavHostFragment.findNavController(MyHostedEventsFragment.this).navigate(action);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation to host event screen failed: ", e);
                    if(getContext() != null) Toast.makeText(getContext(), R.string.error_opening_event_creation_screen, Toast.LENGTH_SHORT).show();
                }
            } else {
                if(getContext() != null) Toast.makeText(getContext(), R.string.please_log_in_to_host_event, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadHostedEvents() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "CurrentUser ID is null or empty, cannot fetch hosted events.");
            if(isAdded()) { // Check isAdded before updating UI
                updateUIVisibility(true, R.string.error_user_not_logged_in_hosted, false);
            }
            if (swipeRefreshLayoutMyHostedEvents != null) swipeRefreshLayoutMyHostedEvents.setRefreshing(false);
            return;
        }
        if(!isAdded()) {
            Log.w(TAG, "Fragment not added. Cannot load hosted events.");
            if (swipeRefreshLayoutMyHostedEvents != null) swipeRefreshLayoutMyHostedEvents.setRefreshing(false);
            return;
        }

        // Show progress indicator based on whether it's a swipe refresh or initial load
        if (swipeRefreshLayoutMyHostedEvents != null && !swipeRefreshLayoutMyHostedEvents.isRefreshing()) {
            updateUIVisibility(false, 0, true); // Show central progress bar
        }
        // If it IS swipe refreshing, its own indicator is visible.

        // CORRECTED: orderBy "eventDateTime" to match EventModel
        db.collection("events")
                .whereEqualTo("organizerId", currentUserId)
                .orderBy("eventDateTime", Query.Direction.DESCENDING) // Use the correct Timestamp field
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment detached during Firebase call. Ignoring results.");
                        return;
                    }

                    // Always ensure loading indicators are turned off after the call
                    if (swipeRefreshLayoutMyHostedEvents != null) swipeRefreshLayoutMyHostedEvents.setRefreshing(false);
                    // progressBarMyHostedEvents.setVisibility(View.GONE); // Handled by updateUIVisibility

                    List<EventModel> hostedEvents = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                EventModel event = document.toObject(EventModel.class);
                                // @DocumentId in EventModel should set event.eventId
                                hostedEvents.add(event);
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting document " + document.getId() + " to EventModel: ", e);
                            }
                        }
                        Log.d(TAG, "Successfully loaded " + hostedEvents.size() + " hosted events.");
                        if (eventAdapter != null) {
                            // Sort here if Firestore doesn't provide the exact order needed due to multiple orderBy constraints
                            // For example, if also sorting by another field client-side:
                            // Collections.sort(hostedEvents, (e1, e2) -> e2.getEventDateTime().compareTo(e1.getEventDateTime()));
                            eventAdapter.submitList(new ArrayList<>(hostedEvents)); // Submit new list
                        }
                        updateUIVisibility(hostedEvents.isEmpty(),
                                hostedEvents.isEmpty() ? R.string.no_events_hosted_yet : 0, false);
                    } else {
                        Log.e(TAG, "Error loading hosted events: ", task.getException());
                        if (eventAdapter != null) {
                            eventAdapter.submitList(new ArrayList<>()); // Clear adapter on error
                        }
                        updateUIVisibility(true, R.string.failed_to_load_hosted_events, false);
                    }
                });
    }

    private void updateUIVisibility(boolean listIsEmpty, int emptyListMessageResId, boolean isLoading) {
        if (!isAdded() || getContext() == null) { // Check getContext() as well
            Log.w(TAG, "Fragment not fully initialized or context is null. Cannot update UI visibility.");
            return;
        }

        if (isLoading) {
            if (progressBarMyHostedEvents != null) progressBarMyHostedEvents.setVisibility(View.VISIBLE);
            if (textViewStatusMyHostedEvents != null) textViewStatusMyHostedEvents.setVisibility(View.GONE);
            if (recyclerViewMyHostedEvents != null) recyclerViewMyHostedEvents.setVisibility(View.GONE);
        } else {
            if (progressBarMyHostedEvents != null) progressBarMyHostedEvents.setVisibility(View.GONE);
            if (listIsEmpty) {
                if (textViewStatusMyHostedEvents != null) {
                    textViewStatusMyHostedEvents.setText(getString(emptyListMessageResId));
                    textViewStatusMyHostedEvents.setVisibility(View.VISIBLE);
                }
                if (recyclerViewMyHostedEvents != null) recyclerViewMyHostedEvents.setVisibility(View.GONE);
            } else { // List is not empty and not loading
                if (textViewStatusMyHostedEvents != null) textViewStatusMyHostedEvents.setVisibility(View.GONE);
                if (recyclerViewMyHostedEvents != null) recyclerViewMyHostedEvents.setVisibility(View.VISIBLE);
            }
        }

        // Ensure swipe refresh animation is stopped if we are not in a loading state
        if (swipeRefreshLayoutMyHostedEvents != null && !isLoading) {
            swipeRefreshLayoutMyHostedEvents.setRefreshing(false);
        }

        // FAB visibility based on login status and if UI components are available
        if (fabAddEvent != null) {
            fabAddEvent.setVisibility((currentUserId != null && !currentUserId.isEmpty()) ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Smart refresh: If not using ViewModel with LiveData, you might consider reloading
        // if data could have changed significantly while the fragment was paused.
        // For now, swipe-to-refresh or initial load handles updates.
        // A common pattern with ViewModels is that LiveData automatically updates the UI.
        // If currentUserId became available after being null, try loading.
        if (currentUserId != null && !currentUserId.isEmpty() &&
                eventAdapter != null && eventAdapter.getItemCount() == 0 &&
                (textViewStatusMyHostedEvents != null && textViewStatusMyHostedEvents.getVisibility() == View.VISIBLE)) {
            Log.d(TAG, "onResume: List is empty and user is logged in, attempting to reload hosted events.");
            loadHostedEvents();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView called for MyHostedEventsFragment.");
        if (recyclerViewMyHostedEvents != null) {
            recyclerViewMyHostedEvents.setAdapter(null); // Important for RecyclerView
        }
        // Nullify views and listeners to prevent memory leaks
        recyclerViewMyHostedEvents = null;
        progressBarMyHostedEvents = null;
        textViewStatusMyHostedEvents = null;
        if (swipeRefreshLayoutMyHostedEvents != null) {
            swipeRefreshLayoutMyHostedEvents.setOnRefreshListener(null);
        }
        swipeRefreshLayoutMyHostedEvents = null;
        fabAddEvent = null;
        eventAdapter = null; // Also nullify adapter reference
    }
}


