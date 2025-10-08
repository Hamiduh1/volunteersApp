/** Looking at the file MyEventsPagerAdapter.java, its entire purpose
is to create instances of EventListFragment.  we identified that
 EventListFragment was redundant and its functionality was being replaced
 by more modern, ViewModel-driven fragments. We made the decision to
 delete EventListFragment.

package com.example.volunteersApp.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity; // or FragmentManager + Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.volunteersApp.ui.eventdetails.EventListFragment; // A generic fragment to display a list of events

public class MyEventsPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 3; // Applied, Approved Upcoming, Past Attended

    public MyEventsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                // "APPLIED_PENDING", "APPROVED_UPCOMING", "PAST_ATTENDED"
                return EventListFragment.newInstance("APPLIED_PENDING");
            case 1:
                return EventListFragment.newInstance("APPROVED_UPCOMING");
            case 2:
                return EventListFragment.newInstance("PAST_ATTENDED");
            default:
                throw new IllegalStateException("Invalid position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}
 **/