package com.example.volunteersApp.fragments; // Or your fragments package

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.volunteersApp.adapters.MyEventsPagerAdapter;
import com.example.volunteersApp.databinding.FragmentMyEventsBinding; // ViewBinding
import com.example.volunteersApp.viewmodels.MyEventsViewModel;
import com.google.android.material.tabs.TabLayoutMediator;

public class MyEventsFragment extends Fragment {

    private FragmentMyEventsBinding binding;
    private MyEventsViewModel myEventsViewModel;
    private MyEventsPagerAdapter myEventsPagerAdapter;

    public static MyEventsFragment newInstance() {
        return new MyEventsFragment();
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

        myEventsPagerAdapter = new MyEventsPagerAdapter(requireActivity()); // Or use getChildFragmentManager() if tabs are complex
        binding.viewPagerMyEvents.setAdapter(myEventsPagerAdapter);

        new TabLayoutMediator(binding.tabLayoutMyEvents, binding.viewPagerMyEvents,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("Applied"); // Or "Pending Approval"
                            break;
                        case 1:
                            tab.setText("Upcoming"); // Approved & Upcoming
                            break;
                        case 2:
                            tab.setText("Attended"); // Past Attended
                            break;
                    }
                }).attach();

        observeViewModel();
        myEventsViewModel.fetchMyEventsData(); // Initial data fetch
    }

    private void observeViewModel() {
        myEventsViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                binding.progressBarMyEvents.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        myEventsViewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.viewPagerMyEvents.setAdapter(null); // Clear adapter for ViewPager2
        binding = null; // Clear binding
    }
}
