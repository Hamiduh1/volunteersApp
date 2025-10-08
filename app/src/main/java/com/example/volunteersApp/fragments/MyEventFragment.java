/**

 (HostedEventsFragment.kt, OrganizerEventsAdapter.kt,
 and the associated ViewModels), the HostedEventsFragment is the modern,
 Kotlin-based replacement for this older Java-based MyEventFragment.
**/
/**
package com.example.volunteersApp.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
// import android.view.MenuItem; // Not directly used in the provided code snippet's logic
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
import androidx.lifecycle.Observer; // Import the Observer interface
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.EventAdapter;
import com.example.volunteersApp.fragments.MyEventFragmentDirections;
import com.example.volunteersApp.viewmodels.EventViewModel; // Make sure this is the correct ViewModel
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
// import java.util.Set; // Not directly used

public class MyEventFragment extends Fragment {

    private static final String TAG = "MyEventFragment";

    private EventViewModel eventViewModel;
    private EventAdapter eventAdapter;

    // View components (assuming findViewById, not ViewBinding for this example based on provided code)
    private RecyclerView recyclerViewMyEvents;
    private SwipeRefreshLayout swipeRefreshLayoutMyEvents;
    private ProgressBar progressBarMyEvents;
    private TextView textViewStatusMyEvents;

    private FirebaseAuth mAuth;
    private FirebaseStorage firebaseStorage;
    private String currentOrganizerId;

    public MyEventFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();

        // Ensure EventViewModel is the correct ViewModel for fetching events by organizer
        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentOrganizerId = currentUser.getUid();
        } else {
            Log.e(TAG, "User not logged in. Cannot initialize 'My Events' for organizer.");
            // Consider navigating to login or showing a persistent error
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_event, container, false);

        recyclerViewMyEvents = view.findViewById(R.id.recyclerViewMyEvents);
        swipeRefreshLayoutMyEvents = view.findViewById(R.id.swipeRefreshLayoutMyEvents);
        progressBarMyEvents = view.findViewById(R.id.progressBarMyEvents);
        textViewStatusMyEvents = view.findViewById(R.id.textViewStatusMyEvents);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupSwipeRefreshLayout();
        observeViewModel();

        if (currentOrganizerId != null && !currentOrganizerId.isEmpty()) {
            Log.d(TAG, "Fetching 'My Events' for organizer: " + currentOrganizerId);
            // Assuming EventViewModel has a method like fetchEventsByOrganizer
            eventViewModel.fetchEventsByOrganizer(currentOrganizerId);
        } else {
            updateUiForNoUserOrError(getString(R.string.login_required_to_see_my_events));
            Log.w(TAG, "Organizer ID is null or empty. Cannot load 'My Events'.");
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in setupRecyclerView. Cannot initialize EventAdapter.");
            return;
        }

        EventAdapter.OnEventListener myFragmentEventListener = new EventAdapter.OnEventListener() {
            @Override
            public void onEventClick(EventModel event, int position) {
                if (!isAdded() || getContext() == null || event == null) {
                    Log.w(TAG, "onEventClick: Fragment not attached, context null, or event null.");
                    return;
                }
                Log.d(TAG, "Event clicked: " + event.getTitle() + " (ID: " + event.getEventId() + ")");
                if (event.getEventId() != null && !event.getEventId().isEmpty()) {
                    try {
                        // Ensure your nav graph has this action and MyEventFragmentDirections is generated
                        NavDirections action = MyEventFragmentDirections.actionMyEventFragmentToEventDetailFragment(event.getEventId());
                        NavHostFragment.findNavController(MyEventFragment.this).navigate(action);
                    } catch (Exception e) { // Catch broader exceptions for navigation issues
                        Log.e(TAG, "Navigation to EventDetailFragment failed. Check nav_graph.xml and action definition.", e);
                        Toast.makeText(getContext(), R.string.error_navigating_to_event_details, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Cannot navigate to event details: Event ID is null or empty.");
                    Toast.makeText(getContext(), R.string.error_event_details_unavailable, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onApplyClick(EventModel event, int position) {
                // This should not be called if isOrganizerView is true, as the button would be hidden.
                Log.d(TAG, "onApplyClick called in MyEventFragment (Organizer View) for " + (event != null ? event.getTitle() : "null event") + ". This is unexpected.");
            }

            // Inside the onViewApplicantsClick method in MyEventFragment.java

            @Override
            public void onViewApplicantsClick(EventModel event, int position) {
                if (!isAdded() || getContext() == null || event == null) return;
                Log.d(TAG, "View Applicants clicked for: " + event.getTitle() + " ID: " + event.getEventId());

                String eventId = event.getEventId();
                // Get the currentOrganizerId which you already have in this fragment
                String organizerId = currentOrganizerId;

                if (eventId != null && !eventId.isEmpty()) {
                    if (organizerId == null || organizerId.isEmpty()) {
                        Log.e(TAG, "Organizer ID is null or empty. Cannot navigate to view applicants.");
                        Toast.makeText(getContext(), R.string.error_organizer_id_missing, Toast.LENGTH_SHORT).show(); // Add this string resource
                        return;
                    }
                    try {
                        // CORRECTED: Pass both eventId and organizerId
                        NavDirections action = MyEventFragmentDirections.actionMyEventFragmentToViewApplicantsFragment(eventId, organizerId);
                        NavHostFragment.findNavController(MyEventFragment.this).navigate(action);
                    } catch (Exception e) {
                        Log.e(TAG, "Navigation to ViewApplicantsFragment failed.", e);
                        Toast.makeText(getContext(), R.string.error_opening_applicants_list, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Cannot view applicants: Event ID is null or empty.");
                    Toast.makeText(getContext(), R.string.error_event_id_missing_for_applicants, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEventOptionsClicked(EventModel event, View anchorView) {
                if (!isAdded() || getContext() == null || event == null) return;
                Log.d(TAG, "Event options clicked for: " + event.getTitle());

                PopupMenu popup = new PopupMenu(requireContext(), anchorView); // Use requireContext for non-null
                popup.getMenuInflater().inflate(R.menu.organizer_event_item_options, popup.getMenu());

                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_edit_event) {
                        Toast.makeText(getContext(), getString(R.string.edit_event_toast, event.getTitle()), Toast.LENGTH_SHORT).show();
                        // TODO: Navigate to EditEventFragment, e.g.,
                        // NavDirections action = MyEventFragmentDirections.actionMyEventFragmentToEditEventFragment(event.getEventId());
                        // NavHostFragment.findNavController(MyEventFragment.this).navigate(action);
                        return true;
                    } else if (itemId == R.id.action_delete_event) {
                        Toast.makeText(getContext(), getString(R.string.delete_event_toast, event.getTitle()), Toast.LENGTH_SHORT).show();
                        // TODO: Show confirmation dialog, then call eventViewModel.deleteEvent(event.getEventId());
                        return true;
                    } else if (itemId == R.id.action_view_event_analytics) {
                        Toast.makeText(getContext(),getString(R.string.analytics_for_event_toast, event.getTitle()), Toast.LENGTH_SHORT).show();
                        // TODO: Navigate to an analytics screen for this event
                        return true;
                    }
                    return false;
                });
                popup.show();
            }
        };

        eventAdapter = new EventAdapter(
                requireContext(), // Use requireContext for non-null
                true,             // isOrganizerView is true for this fragment
                myFragmentEventListener,
                new HashSet<>(),  // initialAppliedEventIds is empty for organizer's own events list
                firebaseStorage   // Pass FirebaseStorage instance
        );
        recyclerViewMyEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewMyEvents.setAdapter(eventAdapter);
    }

    private void setupSwipeRefreshLayout() {
        swipeRefreshLayoutMyEvents.setOnRefreshListener(() -> {
            Log.d(TAG, "Swipe to refresh triggered for Organizer's 'My Events'.");
            if (currentOrganizerId != null && !currentOrganizerId.isEmpty()) {
                eventViewModel.fetchEventsByOrganizer(currentOrganizerId);
            } else {
                Log.w(TAG, "Cannot refresh 'My Events': Organizer ID is null.");
                swipeRefreshLayoutMyEvents.setRefreshing(false); // Stop the animation
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), getString(R.string.login_required_to_refresh), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void observeViewModel() {
        // Observer for the list of events
        eventViewModel.getEvents().observe(getViewLifecycleOwner(), new Observer<List<EventModel>>() {
            @Override
            public void onChanged(@Nullable List<EventModel> events) {
                if (eventAdapter == null) {
                    Log.e(TAG, "EventAdapter is null in events observer. Cannot update UI.");
                    return;
                }
                if (events != null) {
                    Log.d(TAG, "Organizer 'My Events' LiveData updated. Count: " + events.size());
                    eventAdapter.submitList(new ArrayList<>(events)); // Submit a new list for ListAdapter

                    if (events.isEmpty()) {
                        // Only show "no events" if there isn't an error message already from ViewModel
                        if (eventViewModel.getErrorMessage().getValue() == null ||
                                eventViewModel.getErrorMessage().getValue().isEmpty()) {
                            updateUiForNoUserOrError(getString(R.string.no_events_created_yet));
                        }
                    } else {
                        textViewStatusMyEvents.setVisibility(View.GONE);
                        recyclerViewMyEvents.setVisibility(View.VISIBLE);
                    }
                } else {
                    Log.d(TAG, "Organizer 'My Events' LiveData received null list. Clearing adapter.");
                    eventAdapter.submitList(new ArrayList<>());
                    updateUiForNoUserOrError(getString(R.string.no_events_created_yet));
                }
            }
        });

        // Observer for loading state
        eventViewModel.getIsLoading().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean isLoading) {
                if (isLoading == null) return;
                Log.d(TAG, "Organizer 'My Events' isLoading LiveData updated: " + isLoading);

                swipeRefreshLayoutMyEvents.setRefreshing(isLoading); // Sync SwipeRefreshLayout

                if (isLoading && !swipeRefreshLayoutMyEvents.isRefreshing()) {
                    // Show central ProgressBar only if not already refreshing via swipe
                    progressBarMyEvents.setVisibility(View.VISIBLE);
                } else if (!isLoading) {
                    progressBarMyEvents.setVisibility(View.GONE);
                }
            }
        });

        // Observer for error messages
        eventViewModel.getErrorMessage().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String errorMessage) {
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    Log.e(TAG, "Organizer 'My Events' ErrorMessage LiveData: " + errorMessage);
                    updateUiForNoUserOrError(errorMessage);
                } else {
                    // Error cleared. If list is empty, events observer will handle "no events".
                    // If list has items, ensure status text is hidden.
                    if (textViewStatusMyEvents.getVisibility() == View.VISIBLE &&
                            eventAdapter != null && eventAdapter.getItemCount() > 0) {
                        textViewStatusMyEvents.setVisibility(View.GONE);
                    } else if (eventAdapter != null && eventAdapter.getItemCount() == 0) {
                        // If error cleared and list is still empty, show "no events"
                        updateUiForNoUserOrError(getString(R.string.no_events_created_yet));
                    }
                }
            }
        });
    }

    private void updateUiForNoUserOrError(String message) {
        if (progressBarMyEvents != null) progressBarMyEvents.setVisibility(View.GONE);
        if (swipeRefreshLayoutMyEvents != null) swipeRefreshLayoutMyEvents.setRefreshing(false);
        if (textViewStatusMyEvents != null) {
            textViewStatusMyEvents.setText(message);
            textViewStatusMyEvents.setVisibility(View.VISIBLE);
        }
        if (recyclerViewMyEvents != null) recyclerViewMyEvents.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Nullify views to avoid memory leaks
        if (recyclerViewMyEvents != null) {
            recyclerViewMyEvents.setAdapter(null); // Important for RecyclerView
        }
        recyclerViewMyEvents = null;
        swipeRefreshLayoutMyEvents = null;
        progressBarMyEvents = null;
        textViewStatusMyEvents = null;
        eventAdapter = null; // Also nullify adapter reference
        Log.d(TAG, "onDestroyView called for MyEventFragment (Organizer), views nulled.");
    }
}


**/

