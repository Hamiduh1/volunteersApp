/**  This new version removes all references to MyEventsPagerAdapter
 * and ViewPager2, replacing them with a single RecyclerView and
 * direct communication with the MyEventsViewModel. This new version
 * is much more efficient. It fetches all of the user's relevant events once,
 * stores them in a master list, and then provides a filteredEvents
 * LiveData that changes based on the tab selected in the UI. **/
// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/viewmodels/MyEventsViewModel.java

// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/fragments/MyEventsFragment.java

// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/fragments/MyEventsFragment.java

package com.example.volunteersApp.fragments;

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
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.MyEventsAdapter; // Make sure you created this file
import com.example.volunteersApp.databinding.FragmentMyEventsBinding;
import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.viewmodels.MyEventsViewModel;
import com.google.android.material.tabs.TabLayout;

public class MyEventsFragment extends Fragment implements MyEventsAdapter.OnMyEventListener {

    private static final String TAG = "MyEventsFragment";

    private FragmentMyEventsBinding binding;
    private MyEventsViewModel myEventsViewModel;
    private MyEventsAdapter myEventsAdapter; // A single adapter for the RecyclerView

    public MyEventsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myEventsViewModel = new ViewModelProvider(this).get(MyEventsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMyEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupTabLayout();
        observeViewModel();
    }

    private void setupRecyclerView() {
        myEventsAdapter = new MyEventsAdapter(this); // Pass listener
        binding.recyclerViewMyEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewMyEvents.setAdapter(myEventsAdapter);
    }

    private void setupTabLayout() {
        // Remove all existing tabs before adding new ones
        binding.tabLayoutMyEvents.removeAllTabs();

        // Add tabs programmatically
        binding.tabLayoutMyEvents.addTab(binding.tabLayoutMyEvents.newTab().setText("Applied"));
        binding.tabLayoutMyEvents.addTab(binding.tabLayoutMyEvents.newTab().setText("Upcoming"));
        binding.tabLayoutMyEvents.addTab(binding.tabLayoutMyEvents.newTab().setText("Attended"));

        binding.tabLayoutMyEvents.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        myEventsViewModel.setFilter(MyEventsViewModel.EventFilter.APPLIED);
                        break;
                    case 1:
                        myEventsViewModel.setFilter(MyEventsViewModel.EventFilter.UPCOMING);
                        break;
                    case 2:
                        myEventsViewModel.setFilter(MyEventsViewModel.EventFilter.PAST);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void observeViewModel() {
        myEventsViewModel.getFilteredEvents().observe(getViewLifecycleOwner(), events -> {
            if (events != null) {
                myEventsAdapter.submitList(events);
                binding.textViewNoEvents.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        myEventsViewModel.isLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBarMyEvents.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        myEventsViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onEventClick(EventModel event) {
        if (event == null || event.getEventId() == null) {
            Toast.makeText(getContext(), "Cannot open event details.", Toast.LENGTH_SHORT).show();
            return;
        }
        NavDirections action = MyEventsFragmentDirections.actionMyEventsFragmentToEventDetailFragment(event.getEventId());
        Navigation.findNavController(requireView()).navigate(action);
    }

    @Override
    public void onWithdrawClick(EventModel event) {
        if (event == null || event.getEventId() == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Withdraw Application")
                .setMessage("Are you sure you want to withdraw your application for '" + event.getTitle() + "'?")
                .setPositiveButton("Withdraw", (dialog, which) -> myEventsViewModel.withdrawFromEvent(event.getEventId()))
                .setNegativeButton("Cancel", null)
                .show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
