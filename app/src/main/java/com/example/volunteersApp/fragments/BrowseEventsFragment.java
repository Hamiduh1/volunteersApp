package com.example.volunteersApp.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer; // Import Observer
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation; // Keep this if you use it directly
import androidx.navigation.fragment.NavHostFragment; // Recommended for fragment navigation
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.EventAdapter;
import com.example.volunteersApp.viewmodels.EventViewModel; // Ensure this is your comprehensive ViewModel
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
// Make sure BrowseEventsFragmentDirections is generated
// import com.example.volunteersApp.fragments.BrowseEventsFragmentDirections;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BrowseEventsFragment extends Fragment implements EventAdapter.OnEventListener {

    private static final String TAG = "BrowseEventsFragment";

    private RecyclerView recyclerViewEvents;
    private EventAdapter eventAdapter;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private Set<String> currentUserAppliedEventIds;

    private ProgressBar progressBar;
    private TextView textViewStatus;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText editTextSearch;
    private Button buttonSearch;

    private EventViewModel eventViewModel;

    private static final String ARG_CATEGORY_FILTER = "category_filter";

    public static BrowseEventsFragment newInstance(String categoryFilter) {
        BrowseEventsFragment fragment = new BrowseEventsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_FILTER, categoryFilter);
        fragment.setArguments(args);
        return fragment;
    }

    public BrowseEventsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Initializing.");

        eventViewModel = new ViewModelProvider(this).get(EventViewModel.class);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUserAppliedEventIds = new HashSet<>();

        if (getArguments() != null) {
            String category = getArguments().getString(ARG_CATEGORY_FILTER);
            if (category != null && !category.isEmpty()) {
                Log.d(TAG, "Category filter from arguments: " + category);
                eventViewModel.setCategoryFilter(category); // ViewModel handles fetching
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: Inflating layout.");
        View view = inflater.inflate(R.layout.fragment_browse_events, container, false);

        // Initialize views
        recyclerViewEvents = view.findViewById(R.id.recyclerViewBrowseEvents);
        progressBar = view.findViewById(R.id.progressBarBrowseEvents);
        textViewStatus = view.findViewById(R.id.textViewStatusBrowseEvents);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayoutBrowseEvents);
        editTextSearch = view.findViewById(R.id.editTextSearchBrowseEvents);
        buttonSearch = view.findViewById(R.id.buttonSearchBrowseEvents);

        if (recyclerViewEvents == null || progressBar == null || textViewStatus == null ||
                swipeRefreshLayout == null || editTextSearch == null || buttonSearch == null) {
            Log.e(TAG, "One or more views were not found. Check fragment_browse_events.xml IDs.");
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error initializing view components.", Toast.LENGTH_LONG).show();
            }
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: Setting up UI and observers.");

        if (recyclerViewEvents == null) { // Double check after inflation
            Log.e(TAG, "RecyclerView is null in onViewCreated, cannot proceed.");
            if (textViewStatus != null) {
                textViewStatus.setText(R.string.error_loading_events); // General error
                textViewStatus.setVisibility(View.VISIBLE);
            }
            return;
        }

        setupRecyclerView();
        setupUIListeners();
        setupObservers();

        loadCurrentUserApplications(); // Load which events the user has applied to

        // Initial data fetch if not already triggered by category filter in onCreate
        if (getArguments() == null || getArguments().getString(ARG_CATEGORY_FILTER) == null) {
            LiveData<List<EventModel>> eventsLiveData = eventViewModel.getEvents();
            if (eventsLiveData.getValue() == null || eventsLiveData.getValue().isEmpty()) {
                Log.d(TAG, "No category from args, and no initial events. Fetching general events.");
                eventViewModel.fetchEvents(); // ViewModel fetches with its current default/empty filters
            }
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in setupRecyclerView. Cannot initialize EventAdapter.");
            return;
        }
        // Pass empty set initially, it will be updated by loadCurrentUserApplications
        eventAdapter = new EventAdapter(requireContext(), false, this, new HashSet<>(), storage);
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewEvents.setHasFixedSize(true); // Optimization
        recyclerViewEvents.setAdapter(eventAdapter);
        Log.d(TAG, "setupRecyclerView: RecyclerView setup complete.");
    }

    private void setupUIListeners() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                Log.d(TAG, "Swipe to refresh triggered.");
                eventViewModel.refreshEvents(); // ViewModel handles fetching with current filters
                loadCurrentUserApplications(); // Refresh applied status
            });
        }

        if (buttonSearch != null) {
            buttonSearch.setOnClickListener(v -> performSearch());
        }

        if (editTextSearch != null) {
            editTextSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
                    return true;
                }
                return false;
            });
        }
    }

    private void performSearch() {
        if (editTextSearch == null) {
            Log.w(TAG, "Cannot perform search, EditText is null.");
            return;
        }
        String query = editTextSearch.getText().toString().trim();
        Log.d(TAG, "Performing search for query: '" + query + "'");
        eventViewModel.setSearchQuery(query); // ViewModel handles fetching with new query
        hideKeyboard();
    }

    private void hideKeyboard() {
        if (!isAdded() || getActivity() == null) return; // Check if fragment is added
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void loadCurrentUserApplications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "No current user, clearing applied event IDs and updating adapter.");
            currentUserAppliedEventIds.clear();
            if (eventAdapter != null) {
                eventAdapter.setAppliedEventIds(new HashSet<>()); // Pass a new empty set
            }
            return;
        }
        String volunteerUid = currentUser.getUid();
        Log.d(TAG, "Loading applications for user: " + volunteerUid);

        // TODO: Ensure your Firestore structure for applications is correct.
        // Assuming "applications" is a top-level collection or collection group.
        // If it's a subcollection under each event, the query needs to change or be done differently.
        // This example uses a collectionGroup query.
        db.collectionGroup("applications")
                .whereEqualTo("volunteerUid", volunteerUid)
                // .whereEqualTo("status", "pending") // Or whatever status means "applied"
                .get()
                .addOnCompleteListener(task -> {
                    Set<String> newAppliedEventIds = new HashSet<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Assuming "jobPostId" or "eventId" field in application document
                            String eventId = document.getString("jobPostId");
                            if (eventId == null) {
                                eventId = document.getString("eventId"); // Try alternative field name
                            }

                            if (eventId != null && !eventId.isEmpty()) {
                                newAppliedEventIds.add(eventId);
                            } else {
                                Log.w(TAG, "Application document " + document.getId() + " missing eventId/jobPostId.");
                            }
                        }
                        Log.d(TAG, "Fetched " + newAppliedEventIds.size() + " applied event IDs for user " + volunteerUid);
                    } else {
                        Log.e(TAG, "Error fetching user's applications: ", task.getException());
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.error_fetching_application_status, Toast.LENGTH_SHORT).show();
                        }
                    }
                    currentUserAppliedEventIds = newAppliedEventIds;
                    if (eventAdapter != null) {
                        eventAdapter.setAppliedEventIds(new HashSet<>(currentUserAppliedEventIds)); // Update adapter
                    }
                });
    }

    private void setupObservers() {
        // Observer for events list
        eventViewModel.getEvents().observe(getViewLifecycleOwner(), new Observer<List<EventModel>>() {
            @Override
            public void onChanged(@Nullable List<EventModel> events) {
                if (eventAdapter == null || textViewStatus == null || recyclerViewEvents == null) {
                    Log.w(TAG, "Events observer: UI components are null. Cannot update.");
                    return;
                }

                if (events != null) {
                    Log.d(TAG, "Events LiveData updated. Count: " + events.size());
                    eventAdapter.submitList(new ArrayList<>(events)); // Submit a new list

                    if (events.isEmpty()) {
                        // Show "no events" only if there isn't an error message from ViewModel
                        // and not currently loading
                        String currentError = eventViewModel.getErrorMessage().getValue();
                        Boolean isLoading = eventViewModel.getIsLoading().getValue();
                        if ((currentError == null || currentError.isEmpty()) && (isLoading == null || !isLoading)) {
                            textViewStatus.setText(R.string.no_upcoming_events_found_matching_criteria);
                            textViewStatus.setVisibility(View.VISIBLE);
                            recyclerViewEvents.setVisibility(View.GONE);
                        }
                    } else {
                        textViewStatus.setVisibility(View.GONE);
                        recyclerViewEvents.setVisibility(View.VISIBLE);
                    }
                } else { // events list is null
                    Log.w(TAG, "Events LiveData received null. Clearing adapter.");
                    eventAdapter.submitList(new ArrayList<>());
                    // Show "no events" only if no error and not loading
                    String currentError = eventViewModel.getErrorMessage().getValue();
                    Boolean isLoading = eventViewModel.getIsLoading().getValue();
                    if ((currentError == null || currentError.isEmpty()) && (isLoading == null || !isLoading)) {
                        textViewStatus.setText(R.string.no_upcoming_events_found);
                        textViewStatus.setVisibility(View.VISIBLE);
                        recyclerViewEvents.setVisibility(View.GONE);
                    }
                }
            }
        });

        // Observer for loading state
        eventViewModel.getIsLoading().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean isLoading) {
                if (isLoading == null || progressBar == null || swipeRefreshLayout == null) return;
                Log.d(TAG, "IsLoading LiveData updated: " + isLoading);

                if (isLoading) {
                    // Show progress bar only if swipe refresh is not already active
                    if (!swipeRefreshLayout.isRefreshing()) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    textViewStatus.setVisibility(View.GONE); // Hide status text when loading
                } else {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        // Observer for error messages
        eventViewModel.getErrorMessage().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String errorMessage) {
                if (textViewStatus == null || recyclerViewEvents == null || eventAdapter == null || progressBar == null) return;

                if (errorMessage != null && !errorMessage.isEmpty()) {
                    Log.e(TAG, "ErrorMessage LiveData updated: " + errorMessage);
                    textViewStatus.setText(errorMessage);
                    textViewStatus.setVisibility(View.VISIBLE);
                    recyclerViewEvents.setVisibility(View.GONE);
                    eventAdapter.submitList(new ArrayList<>()); // Clear adapter on error
                    progressBar.setVisibility(View.GONE); // Hide progress bar on error
                    swipeRefreshLayout.setRefreshing(false); // Stop swipe refresh on error
                } else {
                    // Error cleared.
                    // The events observer will handle showing the list or "no events found" message.
                    // If no error, but list is empty, events observer should show the "no events"
                    // If there's data, events observer makes list visible and hides status.
                    // This block might not need to do much if events observer is robust.
                    List<EventModel> currentEvents = eventViewModel.getEvents().getValue();
                    if (currentEvents != null && !currentEvents.isEmpty()){
                        textViewStatus.setVisibility(View.GONE); // Hide if it was showing error & now data
                    }
                }
            }
        });
    }


    @Override
    public void onEventClick(EventModel event, int position) {
        if (event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            Log.w(TAG, "onEventClick received null event or event with null/empty ID.");
            if (getContext() != null) Toast.makeText(getContext(), R.string.cannot_open_event_details_no_id, Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Event clicked: " + event.getTitle() + " (ID: " + event.getEventId() + ")");
        try {
            // Ensure BrowseEventsFragmentDirections is correctly generated and action exists
            NavDirections action =
                    BrowseEventsFragmentDirections.actionBrowseEventsFragmentToEventDetailFragment(event.getEventId());
            NavHostFragment.findNavController(this).navigate(action); // Use NavHostFragment for fragments
        } catch (Exception e) { // Catch general exceptions for navigation issues
            Log.e(TAG, "Navigation failed for onEventClick. Check NavController/Action/Args.", e);
            if (getContext() != null) Toast.makeText(getContext(), R.string.error_navigating_to_details, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onApplyClick(EventModel event, int position) {
        if (getContext() == null || !isAdded()) {
            Log.w(TAG, "Context is null or fragment not added in onApplyClick.");
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), R.string.must_be_logged_in_to_apply, Toast.LENGTH_SHORT).show();
            // TODO: Consider navigating to login: NavHostFragment.findNavController(this).navigate(R.id.action_to_login);
            return;
        }
        if (event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            Toast.makeText(getContext(), R.string.event_data_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        if (event.getOrganizerId() == null || event.getOrganizerId().isEmpty()) {
            Toast.makeText(getContext(), R.string.event_data_incomplete_cannot_apply, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Apply failed: Organizer ID is null for event: " + event.getTitle());
            return;
        }

        if (currentUserAppliedEventIds.contains(event.getEventId())) {
            Toast.makeText(getContext(), R.string.already_applied_to_this_event, Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Apply clicked for event: " + event.getTitle());
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE); // Show progress during application

        String volunteerUid = currentUser.getUid();
        String eventId = event.getEventId();
        // Generate a new ID for the application document
        String applicationId = db.collection("events").document(eventId)
                .collection("applications").document().getId();

        Map<String, Object> applicationData = new HashMap<>();
        applicationData.put("applicationId", applicationId); // Good to store the ID itself
        applicationData.put("eventId", eventId); // Use 'eventId' for consistency
        applicationData.put("volunteerUid", volunteerUid);
        applicationData.put("volunteerName", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Volunteer");
        applicationData.put("volunteerEmail", currentUser.getEmail());
        applicationData.put("organizerUid", event.getOrganizerId());
        applicationData.put("eventTitle", event.getTitle()); // Use 'eventTitle'
        applicationData.put("status", "pending"); // Initial status
        applicationData.put("applicationTimestamp", FieldValue.serverTimestamp());

        // Store application in subcollection of the event
        db.collection("events").document(eventId)
                .collection("applications").document(applicationId)
                .set(applicationData)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return; // Check if fragment is still added
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), R.string.application_submitted_success, Toast.LENGTH_SHORT).show();

                    currentUserAppliedEventIds.add(eventId);
                    if (eventAdapter != null) {
                        eventAdapter.setAppliedEventIds(new HashSet<>(currentUserAppliedEventIds));
                    }

                    // Increment slotsFilled in the event document.
                    // This is still better done via Cloud Function for atomicity at scale.
                    db.collection("events").document(eventId)
                            .update("slotsFilled", FieldValue.increment(1))
                            .addOnSuccessListener(success -> Log.d(TAG, "slotsFilled incremented for event: " + eventId))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to increment slotsFilled for event: " + eventId, e));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), getString(R.string.error_submitting_application, e.getMessage()), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error submitting application for event " + eventId, e);
                });
    }

    @Override
    public void onViewApplicantsClick(EventModel event, int position) {
        // This is a volunteer view, so this method should ideally not be triggered by the adapter.
        Log.w(TAG, "onViewApplicantsClick called in BrowseEventsFragment. This button should be hidden for volunteers.");
        if (getContext() != null && isAdded()) {
            Toast.makeText(getContext(), "This action is for event organizers.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEventOptionsClicked(EventModel event, View anchorView) {
        // This is a volunteer view, so this method should ideally not be triggered by the adapter.
        Log.w(TAG, "onEventOptionsClicked called in BrowseEventsFragment. This button should be hidden for volunteers.");
        if (getContext() != null && isAdded()) {
            Toast.makeText(getContext(), "Options not available for volunteers in this view.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Cleaning up UI components.");
        if (recyclerViewEvents != null) {
            recyclerViewEvents.setAdapter(null); // Important for RecyclerView
        }
        // Nullify views to help GC and prevent leaks if not using ViewBinding strictly.
        // If using ViewBinding, the binding instance itself should be set to null.
        eventAdapter = null;
        recyclerViewEvents = null;
        progressBar = null;
        textViewStatus = null;
        swipeRefreshLayout = null;
        editTextSearch = null;
        buttonSearch = null;
        // if (binding != null) binding = null; // Example for ViewBinding
    }
}

