package com.example.volunteersApp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
// import androidx.core.content.ContextCompat; // Not directly used in the current logic
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer; // Import for explicit Observers
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.volunteersApp.VolunteeringFragmentDirections;
import com.example.volunteersApp.adapters.EventAdapter;
import com.example.volunteersApp.databinding.FragmentVolunteeringBinding;
// CRITICAL: Ensure this is the only EventModel import in both this file AND VolunteeringViewModel
import com.example.volunteersApp.models.EventModel;
// You will need to create/verify VolunteeringViewModel.java
import com.example.volunteersApp.viewmodels.VolunteeringViewModel;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VolunteeringFragment extends Fragment implements EventAdapter.OnEventListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "VolunteeringFragment";
    public static final String ARG_CATEGORY_FILTER = "category_filter";

    private FragmentVolunteeringBinding binding; // ViewBinding
    private VolunteeringViewModel viewModel; // Ensure this ViewModel is correctly defined
    private EventAdapter eventAdapter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Set<String> currentUserAppliedEventIds;

    public VolunteeringFragment() {
        // Default constructor
    }

    public static VolunteeringFragment newInstance(String categoryFilter) {
        VolunteeringFragment fragment = new VolunteeringFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_FILTER, categoryFilter);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        // CRITICAL: Ensure VolunteeringViewModel is correctly defined and uses
        // LiveData<List<com.example.volunteersApp.models.EventModel>>
        viewModel = new ViewModelProvider(this).get(VolunteeringViewModel.class);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUserAppliedEventIds = new HashSet<>();

        if (getArguments() != null) {
            String category = getArguments().getString(ARG_CATEGORY_FILTER);
            if (category != null && !category.isEmpty()) {
                Log.d(TAG, "Category filter from arguments: " + category);
                viewModel.setCategoryFilter(category); // Assumes ViewModel handles fetching
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        binding = FragmentVolunteeringBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        setupRecyclerView();
        setupObservers(); // Setup observers before initial fetch trigger from ViewModel
        setupUIListeners();

        loadCurrentUserApplications(); // Load user's application statuses

        // Initial data fetch logic
        // If a category was set in onCreate, the ViewModel should have triggered a fetch.
        // If not, trigger a general fetch.
        if (getArguments() == null || getArguments().getString(ARG_CATEGORY_FILTER) == null) {
            // Check if ViewModel already has data (e.g., due to screen rotation or prior fetch)
            if (viewModel.getOpportunities().getValue() == null || viewModel.getOpportunities().getValue().isEmpty()) {
                Log.d(TAG, "No category from args, and no initial opportunities. Fetching general opportunities.");
                viewModel.fetchOpportunities(false); // false for initial load
            }
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in setupRecyclerView. Cannot initialize adapter.");
            return;
        }
        // Pass empty set, will be updated by loadCurrentUserApplications
        eventAdapter = new EventAdapter(
                requireContext(),
                false, // isOrganizerView = false for volunteers
                this,  // OnEventListener
                new HashSet<>(), // Initial empty set for appliedEventIds
                storage
        );
        binding.recyclerViewOpportunities.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewOpportunities.setAdapter(eventAdapter);
        Log.d(TAG, "RecyclerView setup complete.");
    }

    private void loadCurrentUserApplications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "No current user, clearing applied event IDs and updating adapter.");
            updateAdapterWithAppliedIds(new HashSet<>());
            return;
        }
        String volunteerUid = currentUser.getUid();
        Log.d(TAG, "Fetching applications for user: " + volunteerUid);

        // Using collection "applications" (ensure it's structured correctly)
        db.collection("applications") // Or "users/{userId}/appliedToEvents" if that's your structure
                .whereEqualTo("volunteerUid", volunteerUid)
                .get()
                .addOnCompleteListener(task -> {
                    Set<String> appliedIds = new HashSet<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Using "jobPostId" as per original code for event ID in application document
                            String eventId = document.getString("jobPostId");
                            if (eventId == null) { // Fallback if field name is different
                                eventId = document.getString("eventId");
                            }
                            if (eventId != null && !eventId.isEmpty()) {
                                appliedIds.add(eventId);
                            }
                        }
                        Log.d(TAG, "Fetched " + appliedIds.size() + " applied event IDs: " + appliedIds);
                    } else {
                        Log.e(TAG, "Error fetching user's applications: ", task.getException());
                        if (getContext() != null && isAdded()) {
                            Toast.makeText(getContext(), R.string.error_loading_application_status, Toast.LENGTH_SHORT).show();
                        }
                    }
                    updateAdapterWithAppliedIds(appliedIds);
                });
    }


    private void updateAdapterWithAppliedIds(Set<String> appliedIds) {
        if (!isAdded() || eventAdapter == null) {
            Log.w(TAG, "Cannot update adapter with applied IDs: Fragment not added or adapter is null.");
            return;
        }
        currentUserAppliedEventIds.clear();
        if (appliedIds != null) { // Add null check for safety
            currentUserAppliedEventIds.addAll(appliedIds);
        }
        // Pass a new copy to ensure the adapter's diffing works correctly
        eventAdapter.setAppliedEventIds(new HashSet<>(currentUserAppliedEventIds));
        Log.d(TAG, "Adapter updated with " + currentUserAppliedEventIds.size() + " applied event IDs.");
    }

    private void setupObservers() {
        if (viewModel == null) {
            Log.e(TAG, "ViewModel is null in setupObservers. Cannot observe LiveData.");
            return;
        }

        // Observer for opportunities (List<EventModel>) from ViewModel
        viewModel.getOpportunities().observe(getViewLifecycleOwner(), new Observer<List<EventModel>>() {
            @Override
            public void onChanged(@Nullable List<EventModel> opportunities) {
                if (!isAdded() || binding == null || eventAdapter == null) {
                    Log.w(TAG, "Opportunities Observer: Fragment not fully ready or components null.");
                    return;
                }

                if (opportunities != null) {
                    Log.d(TAG, "Opportunities LiveData updated with " + opportunities.size() + " items.");
                    eventAdapter.submitList(new ArrayList<>(opportunities)); // Submit new list

                    binding.textViewNoOpportunities.setVisibility(opportunities.isEmpty() ? View.VISIBLE : View.GONE);
                    if (opportunities.isEmpty()) {
                        // Show specific message if search query led to no results
                        String currentSearchQuery = viewModel.getSearchQueryValue(); // Assumes ViewModel has this method
                        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                            binding.textViewNoOpportunities.setText(getString(R.string.no_results_for_search, currentSearchQuery));
                        } else {
                            binding.textViewNoOpportunities.setText(R.string.no_opportunities_found);
                        }
                    }
                } else {
                    Log.d(TAG, "Opportunities LiveData is null. Clearing list.");
                    eventAdapter.submitList(new ArrayList<>()); // Clear the list
                    binding.textViewNoOpportunities.setText(R.string.no_opportunities_found);
                    binding.textViewNoOpportunities.setVisibility(View.VISIBLE);
                }
                if (binding.swipeRefreshLayout.isRefreshing()) {
                    binding.swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean isLoading) {
                if (!isAdded() || binding == null || isLoading == null) return;
                Log.d(TAG, "isLoading LiveData changed: " + isLoading);

                if (isLoading) {
                    // Show progress bar only if not already doing a swipe refresh
                    if (!binding.swipeRefreshLayout.isRefreshing()) {
                        binding.progressBar.setVisibility(View.VISIBLE);
                    }
                    binding.textViewNoOpportunities.setVisibility(View.GONE); // Hide status text while loading
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String errorMessage) {
                if (!isAdded() || binding == null) return;

                if (errorMessage != null && !errorMessage.isEmpty()) {
                    Log.e(TAG, "ErrorMessage LiveData: " + errorMessage);
                    if (getContext() != null) { // Show Toast for more immediate feedback
                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                    binding.textViewNoOpportunities.setText(errorMessage);
                    binding.textViewNoOpportunities.setVisibility(View.VISIBLE);
                    if (eventAdapter != null) eventAdapter.submitList(new ArrayList<>()); // Clear list on error
                    binding.recyclerViewOpportunities.setVisibility(View.GONE);
                    binding.progressBar.setVisibility(View.GONE); // Hide progress bar on error
                } else {
                    // Error is cleared. Visibility of noOpportunities text is handled by the opportunities observer.
                    // If opportunities list is populated, it will hide the textView.
                    // If opportunities list is empty, it will show the "no opportunities" message.
                    if (eventAdapter != null && eventAdapter.getItemCount() > 0) {
                        binding.textViewNoOpportunities.setVisibility(View.GONE);
                    }
                }
                if (binding.swipeRefreshLayout.isRefreshing()) { // Ensure swipe stops on error too
                    binding.swipeRefreshLayout.setRefreshing(false);
                }
            }
        });
    }

    private void setupUIListeners() {
        if (binding == null) {
            Log.e(TAG, "Binding is null in setupUIListeners. Cannot set listeners.");
            return;
        }
        binding.swipeRefreshLayout.setOnRefreshListener(this); // Fragment implements OnRefreshListener

        binding.searchViewOpportunities.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                hideKeyboard();
                binding.searchViewOpportunities.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Perform search on empty to clear results, if a previous query existed
                if (newText.isEmpty() && viewModel.getSearchQueryValue() != null && !viewModel.getSearchQueryValue().isEmpty()) {
                    performSearch(null); // Clear search
                }
                // For live search as user types (optional, can be resource-intensive):
                // performSearch(newText);
                return true;
            }
        });

        View closeButton = binding.searchViewOpportunities.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                binding.searchViewOpportunities.setQuery("", false);
                performSearch(null); // Reset/fetch all
                hideKeyboard();
                binding.searchViewOpportunities.clearFocus();
            });
        }
    }

    private void performSearch(@Nullable String query) {
        // Query can be null to indicate clearing the search
        Log.d(TAG, "Performing search for: " + (query == null ? "NULL (clear search)" : query));
        viewModel.setSearchQuery(query); // ViewModel should trigger data fetch
    }

    private void hideKeyboard() {
        if (!isAdded() || getActivity() == null) return;
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    // --- SwipeRefreshLayout.OnRefreshListener Implementation ---
    @Override
    public void onRefresh() {
        Log.d(TAG, "Swipe to refresh triggered.");
        // viewModel.fetchOpportunities(true) means it's a forced refresh.
        // The ViewModel's refreshEvents() or fetchEvents() method should use current filters.
        if (viewModel != null) {
            viewModel.fetchOpportunities(true); // Assuming this re-fetches with current filters
            loadCurrentUserApplications(); // Re-check application statuses
        } else {
            if (binding != null) binding.swipeRefreshLayout.setRefreshing(false);
        }
    }

    // --- EventAdapter.OnEventListener Implementation ---

    @Override
    public void onEventClick(EventModel event, int position) {
        if (!isAdded() || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            Log.w(TAG, "onEventClick: prerequisites not met or eventId is null/empty.");
            if (getContext() != null) Toast.makeText(getContext(), getString(R.string.error_opening_event_details_short), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Event clicked: " + event.getTitle() + " (ID: " + event.getEventId() + ")");

        try {
            // Ensure VolunteeringFragmentDirections and the action exist and are correctly defined
            NavDirections action = VolunteeringFragmentDirections.actionNavVolunteeringEventsToEventDetailFragment(event.getEventId());
            Navigation.findNavController(requireView()).navigate(action);
        } catch (Exception e) { // Catch general exceptions during navigation
            Log.e(TAG, "Navigation to event details failed for event: " + event.getEventId(), e);
            if (getContext() != null) Toast.makeText(getContext(), getString(R.string.error_navigating_to_details_general), Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onApplyClick(EventModel event, int position) {
        if (!isAdded() || getContext() == null || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            Log.w(TAG, "onApplyClick: prerequisites not met or eventId is null/empty.");
            if (getContext() != null) Toast.makeText(getContext(), R.string.cannot_process_application_event, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "onApplyClick for event: " + event.getTitle());

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            if (getContext() != null) Toast.makeText(getContext(), R.string.must_be_logged_in_to_apply, Toast.LENGTH_SHORT).show();
            // TODO: Consider navigating to login screen
            return;
        }

        if (event.getOrganizerId() == null || event.getOrganizerId().isEmpty()) {
            if (getContext() != null) Toast.makeText(getContext(), R.string.event_data_incomplete_cannot_apply_short, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Apply failed: Organizer ID is null for event: " + event.getTitle());
            return;
        }

        final String eventId = event.getEventId();

        if (currentUserAppliedEventIds.contains(eventId)) {
            if (getContext() != null) Toast.makeText(getContext(), R.string.already_applied_to_this_event, Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding != null && binding.progressBar != null) binding.progressBar.setVisibility(View.VISIBLE);

        String volunteerUid = currentUser.getUid();
        // Use "applications" as a subcollection under "events"
        String applicationId = db.collection("events").document(eventId)
                .collection("applications").document().getId();

        Map<String, Object> applicationData = new HashMap<>();
        applicationData.put("applicationId", applicationId);
        applicationData.put("jobPostId", eventId); // Field name from original code
        applicationData.put("volunteerUid", volunteerUid);
        applicationData.put("volunteerName", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "N/A");
        applicationData.put("volunteerEmail", currentUser.getEmail() != null ? currentUser.getEmail() : "N/A");
        applicationData.put("organizerUid", event.getOrganizerId());
        applicationData.put("eventTitle", event.getTitle()); // Using getTitle()
        applicationData.put("status", "pending");
        applicationData.put("applicationTimestamp", FieldValue.serverTimestamp());

        // Transaction to ensure atomicity for critical updates
        db.runTransaction(transaction -> {
            // 1. Add application to the event's "applications" subcollection
            transaction.set(db.collection("events").document(eventId).collection("applications").document(applicationId), applicationData);

            // 2. Add a reference to the user's "appliedToEvents" subcollection (for volunteer's 'My Applications' screen)
            // This assumes a "users/{userId}/appliedToEvents/{eventId}" structure
            Map<String, Object> userAppliedEventData = new HashMap<>();
            userAppliedEventData.put("eventId", eventId); // Store eventId
            userAppliedEventData.put("applicationTimestamp", applicationData.get("applicationTimestamp")); // Use same timestamp
            userAppliedEventData.put("eventTitle", event.getTitle()); // Denormalize for easy display
            userAppliedEventData.put("status", "pending"); // Store initial status here too for user's view
            userAppliedEventData.put("applicationIdInEvent", applicationId); // Link to the actual application
            transaction.set(db.collection("users").document(volunteerUid).collection("appliedToEvents").document(eventId), userAppliedEventData);


            // 3. Increment volunteersRegistered count on the event document
            // IMPORTANT: Ensure "volunteersRegistered" field exists on your EventModel and Firestore documents.
            // If it's called "slotsFilled", use that name instead.
            transaction.update(db.collection("events").document(eventId), "slotsFilled", FieldValue.increment(1)); // Assuming slotsFilled is the counter

            return null; // Transaction success
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Application transaction successful for event: " + eventId);
            if (!isAdded()) return;
            if (binding != null && binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
            if (getContext() != null) Toast.makeText(getContext(), R.string.application_submitted_success, Toast.LENGTH_SHORT).show();

            // Update UI immediately
            currentUserAppliedEventIds.add(eventId);
            updateAdapterWithAppliedIds(currentUserAppliedEventIds);

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Application transaction failed for event: " + eventId, e);
            if (!isAdded()) return;
            if (binding != null && binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
            if (getContext() != null) {
                // Use a more specific error string if available, or fallback
                String detailedError = getString(R.string.error_submitting_application_default_with_message, e.getMessage() != null ? e.getMessage() : "Unknown error");
                Toast.makeText(getContext(), detailedError, Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    public void onViewApplicantsClick(EventModel event, int position) {
        if (!isAdded() || event == null) return;
        Log.w(TAG, "onViewApplicantsClick called in volunteer view. Event: " + event.getTitle() + ". This is for organizers.");
        if (getContext() != null) {
            Toast.makeText(getContext(), R.string.action_for_event_organizers_only, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEventOptionsClicked(EventModel event, View anchorView) {
        if (!isAdded() || getContext() == null || event == null) return;
        Log.d(TAG, "Event options clicked for: " + event.getTitle());

        PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        // Ensure you have R.menu.event_item_options_menu_volunteer
        popup.getMenuInflater().inflate(R.menu.event_item_options_menu_volunteer, popup.getMenu());

        // You might want to dynamically change menu items based on whether the user has applied, etc.
        // For example:
        // MenuItem applyItem = popup.getMenu().findItem(R.id.action_apply_to_event_menu); // Example ID
        // if (applyItem != null) applyItem.setVisible(!currentUserAppliedEventIds.contains(event.getEventId()));
        // ... (similar logic for withdraw if you add it)

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_view_event_details_menu) { // Example ID from your menu
                onEventClick(event, -1); // Re-use onEventClick, position -1 as it's not from list item itself
                return true;
            } else if (itemId == R.id.action_share_event_menu) { // Example ID
                Toast.makeText(getContext(), "Sharing: " + event.getTitle(), Toast.LENGTH_SHORT).show();
                // TODO: Implement shareEvent(event.getEventId(), event.getTitle());
                return true;
            } else if (itemId == R.id.action_report_event_menu) { // Example ID
                Toast.makeText(getContext(), "Reporting: " + event.getTitle(), Toast.LENGTH_SHORT).show();
                // TODO: Implement reportEvent(event.getEventId());
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "VolunteeringFragment onDestroyView called.");
        if (binding != null && binding.recyclerViewOpportunities != null) {
            binding.recyclerViewOpportunities.setAdapter(null); // Clear adapter from RecyclerView
        }
        binding = null; // Important to prevent memory leaks with ViewBinding
        eventAdapter = null; // Clear adapter reference
    }
}


