// File: com/example/volunteersApp/MainPagerAdapter.java
package com.example.volunteersApp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.volunteersApp.fragments.AvailableJobsFragment; // Your existing fragment

// Import your VolunteeringFragment if it's not in the fragments sub-package
// import com.example.volunteersApp.VolunteeringFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 2; // Or 3 if you have more primary tabs
    public static final int JOBS_FRAGMENT_POSITION = 0;
    public static final int VOLUNTEER_EVENTS_FRAGMENT_POSITION = 1; // New position for VolunteeringFragment
    // public static final int BROWSE_EVENTS_FRAGMENT_POSITION = 2; // If you had another

    public static final int EVENTS_FRAGMENT_POSITION = 1;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case JOBS_FRAGMENT_POSITION:
                return new AvailableJobsFragment(); // Your existing fragment for "Jobs"
            case VOLUNTEER_EVENTS_FRAGMENT_POSITION:
                return new VolunteeringFragment(); // Our new fragment for "Volunteer Events"
            // case BROWSE_EVENTS_FRAGMENT_POSITION:
            //    return new BrowseEventsFragment(); // If you use this one
            default:
                return new AvailableJobsFragment(); // Fallback
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS; // Adjust if you have more tabs
    }
}
