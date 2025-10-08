/** VolunteeringFragment.java is the modern replacement that fits within
 *  your single-activity, navigation-component-based app.**/
package com.example.volunteersApp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.volunteersApp.adapters.EventAdapter;
import com.example.volunteersApp.databinding.FragmentVolunteeringBinding;
import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.viewmodels.VolunteeringViewModel;
import com.example.volunteersApp.viewmodels.HomeViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.HashSet;
//import java.util.List;
import java.util.Set;

public class VolunteeringFragment extends Fragment implements EventAdapter.OnEventListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "VolunteeringFragment";
    public static final String ARG_CATEGORY_FILTER = "category_filter";

    private FragmentVolunteeringBinding binding;
    private VolunteeringViewModel viewModel;
    private EventAdapter eventAdapter;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private Set<String> currentUserAppliedEventIds = new HashSet<>();

    public VolunteeringFragment() {
        // Default constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        viewModel = new ViewModelProvider(this).get(VolunteeringViewModel.class);
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        if (getArguments() != null) {
            String category = getArguments().getString(ARG_CATEGORY_FILTER);
            if (category != null && !category.isEmpty()) {
                Log.d(TAG, "Category filter from arguments: " + category);
                viewModel.setCategoryFilter(category);
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
        setupUIListeners();
        setupObservers();

        // Initial data fetch if ViewModel is empty
        if (viewModel.getOpportunities().getValue() == null || viewModel.getOpportunities().getValue().isEmpty()) {
            viewModel.fetchOpportunities(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Load application status every time the fragment becomes visible
        loadCurrentUserApplications();
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;
        eventAdapter = new EventAdapter(
                requireContext(),
                false, // isOrganizerView
                this,  // OnEventListener
                new HashSet<>(),
                storage
        );
        binding.recyclerViewOpportunities.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewOpportunities.setAdapter(eventAdapter);
    }

    private void loadCurrentUserApplications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            updateAdapterWithAppliedIds(new HashSet<>());
            return;
        }
        String volunteerUid = currentUser.getUid();
        Log.d(TAG, "Fetching applications for user: " + volunteerUid);

        // This assumes a user-centric model for checking applications
        viewModel.fetchUserAppliedEventIds(volunteerUid).observe(getViewLifecycleOwner(), appliedIds -> {
            Log.d(TAG, "User's applied event IDs updated: " + appliedIds.size());
            updateAdapterWithAppliedIds(appliedIds);
        });
    }

    private void updateAdapterWithAppliedIds(Set<String> appliedIds) {
        if (!isAdded() || eventAdapter == null) return;
        currentUserAppliedEventIds.clear();
        if (appliedIds != null) {
            currentUserAppliedEventIds.addAll(appliedIds);
        }
        eventAdapter.setAppliedEventIds(new HashSet<>(currentUserAppliedEventIds));
    }

    private void setupObservers() {
        viewModel.getOpportunities().observe(getViewLifecycleOwner(), opportunities -> {
            if (!isAdded() || binding == null || eventAdapter == null) return;
            Log.d(TAG, "Opportunities LiveData updated with " + opportunities.size() + " items.");

            eventAdapter.submitList(new ArrayList<>(opportunities));
            binding.textViewNoOpportunities.setVisibility(opportunities.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (!isAdded() || binding == null) return;
            if (!binding.swipeRefreshLayout.isRefreshing()) { // Don't show progress bar if swipe-refreshing
                binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            if (isLoading) {
                binding.textViewNoOpportunities.setVisibility(View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (!isAdded() || binding == null) return;
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Log.e(TAG, "ErrorMessage: " + errorMessage);
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                binding.textViewNoOpportunities.setText(errorMessage);
                binding.textViewNoOpportunities.setVisibility(View.VISIBLE);
                if (eventAdapter != null) eventAdapter.submitList(new ArrayList<>());
            }
        });
    }

    private void setupUIListeners() {
        if (binding == null) return;
        binding.swipeRefreshLayout.setOnRefreshListener(this);

        // The ID in your XML is 'search_view_opportunities'
        binding.searchViewOpportunities.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                hideKeyboard();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // For live search as user types
                performSearch(newText);
                return true;
            }
        });
    }

    private void performSearch(@Nullable String query) {
        Log.d(TAG, "Performing search for: " + query);
        viewModel.setSearchQuery(query);
    }

    private void hideKeyboard() {
        if (!isAdded() || getActivity() == null) return;
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "Swipe to refresh triggered.");
        viewModel.fetchOpportunities(true); // Force refresh
        loadCurrentUserApplications(); // Also refresh application status
        binding.swipeRefreshLayout.setRefreshing(false); // ViewModel will handle loading state
    }

    @Override
    public void onEventClick(EventModel event, int position) {
        if (!isAdded() || event == null || event.getEventId() == null || event.getEventId().isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.error_opening_event_details_short), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Event clicked: " + event.getTitle() + " (ID: " + event.getEventId() + ")");
        try {
            // Using Safe Args to navigate
            NavDirections action = VolunteeringFragmentDirections.actionNavBrowseEventsToEventDetail(event.getEventId());
            Navigation.findNavController(requireView()).navigate(action);
        } catch (Exception e) {
            Log.e(TAG, "Navigation to event details failed", e);
            Toast.makeText(getContext(), getString(R.string.error_navigating_to_details_general), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onApplyClick(EventModel event, int position) {
        if (!isAdded() || event == null || event.getEventId() == null) return;
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), R.string.must_be_logged_in_to_apply, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserAppliedEventIds.contains(event.getEventId())) {
            Toast.makeText(getContext(), R.string.already_applied_to_this_event, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Apply clicked for event: " + event.getTitle());
        viewModel.applyForEvent(event, currentUser.getUid()); // Delegate logic to ViewModel
    }

    // These methods are for the adapter interface but are not used in the volunteer view
    @Override
    public void onViewApplicantsClick(EventModel event, int position) { /* No-op for volunteers */ }
    @Override
    public void onEventOptionsClicked(EventModel event, View anchorView) { /* No-op for volunteers */ }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.recyclerViewOpportunities.setAdapter(null); // Clear adapter reference
        binding = null; // Prevent memory leaks
    }
}


