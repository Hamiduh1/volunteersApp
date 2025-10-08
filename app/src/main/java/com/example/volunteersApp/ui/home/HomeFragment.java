// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/ui/home/HomeFragment.java

package com.example.volunteersApp.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.UpcomingEventsAdapter;
import com.example.volunteersApp.databinding.FragmentHomeBinding;
//import com.example.volunteersApp.models.EventModel; // CORRECTED: Use EventModel
import com.example.volunteersApp.viewmodels.HomeViewModel;

import java.util.ArrayList;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;
    private NavController navController;
    private HomeViewModel homeViewModel; // The ViewModel will handle data logic
    private UpcomingEventsAdapter upcomingEventsAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        // Initialize ViewModel
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        setupUpcomingEventsRecyclerView();
        setupVolunteerButtonListeners();
        setupObservers();
    }

    private void setupVolunteerButtonListeners() {
        binding.buttonBrowseAllEvents.setOnClickListener(v ->
                navController.navigate(HomeFragmentDirections.actionNavHomeToNavBrowseEvents()));

        binding.buttonBrowseAllJobs.setOnClickListener(v ->
                navController.navigate(HomeFragmentDirections.actionNavHomeToNavBrowseJobs()));

        binding.buttonMyEvents.setOnClickListener(v ->
                navController.navigate(HomeFragmentDirections.actionNavHomeToMyEventsFragment()));

        binding.buttonMyJobs.setOnClickListener(v ->
                navController.navigate(HomeFragmentDirections.actionNavHomeToNavMyJobs()));
    }

    private void setupUpcomingEventsRecyclerView() {
        // Use an empty list to initialize. The adapter will be updated by LiveData.
        upcomingEventsAdapter = new UpcomingEventsAdapter(new ArrayList<>(), event -> {
            if (event.getEventId() == null || event.getEventId().isEmpty()) {
                Log.e(TAG, "Clicked event has no ID. Cannot navigate.");
                Toast.makeText(getContext(), R.string.error_event_id_missing, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                // Ensure this action exists in your navigation graph from HomeFragment
                NavDirections action = HomeFragmentDirections.actionNavHomeToEventDetail(event.getEventId());
                navController.navigate(action);
            } catch (Exception e) {
                Log.e(TAG, "Failed to navigate to event details for event ID: " + event.getEventId(), e);
                Toast.makeText(getContext(), R.string.error_navigating_event_details, Toast.LENGTH_SHORT).show();
            }
        });
        binding.recyclerViewUpcomingEvents.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerViewUpcomingEvents.setAdapter(upcomingEventsAdapter);
    }

    private void setupObservers() {
        // Observe welcome message
        homeViewModel.getWelcomeMessage().observe(getViewLifecycleOwner(), message -> {
            binding.textViewWelcome.setText(message);
        });

        // Observe loading state
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBarHome.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.recyclerViewUpcomingEvents.setAlpha(isLoading ? 0.5f : 1.0f);
        });

        // Observe upcoming events list
        homeViewModel.getUpcomingEvents().observe(getViewLifecycleOwner(), events -> {
            if (events != null) {
                upcomingEventsAdapter.updateEvents(events); // Update adapter's data
                boolean isEmpty = events.isEmpty();
                binding.recyclerViewUpcomingEvents.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                binding.textViewNoUpcomingEvents.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                if (isEmpty) {
                    binding.textViewNoUpcomingEvents.setText(R.string.no_upcoming_events);
                }
            }
        });

        // Observe error messages
        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                binding.textViewNoUpcomingEvents.setText(error);
                binding.textViewNoUpcomingEvents.setVisibility(View.VISIBLE);
                binding.recyclerViewUpcomingEvents.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks
    }
}
