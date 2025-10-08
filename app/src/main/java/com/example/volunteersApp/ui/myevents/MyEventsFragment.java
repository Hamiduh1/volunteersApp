/** THIS IS OLD VERSION OF MYEVENTSFRAGMENT. KEEP FOR REFERENCE ONLY.
  MyEventsFragment.java NEW VERSION IS LOCATED IN FRAGMENT FOLDER:


package com.example.volunteersApp.ui.myevents;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

// Import your child fragments:
import com.example.volunteersApp.ui.myactivity.myapplied.MyAppliedEventsFragment;
// If the above paths are incorrect, ensure they point to the correct location
// e.g., if they are also in the ui package:
// import com.example.volunteersApp.ui.myevents.subfragments.HostedEventsFragment;
// import com.example.volunteersApp.ui.myevents.subfragments.MyAppliedEventsFragment;


import com.example.volunteersApp.R; // Ensure R is imported correctly
import com.example.volunteersApp.organizer.HostedEventsFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MyEventsFragment extends Fragment {

    private ViewPager2 viewPagerMyEvents;
    private TabLayout tabLayoutMyEvents;
    private final String[] tabTitles = new String[]{"Hosted", "Applied"};
    // Add required string resources like R.string.content_desc_hosted_events_tab in strings.xml

    public MyEventsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We call setHasOptionsMenu(true) in onViewCreated after setting up the toolbar
        // to ensure the fragment knows to participate in options menu handling.
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- Toolbar Setup ---
        Toolbar toolbar = view.findViewById(R.id.toolbar_my_events_fragment);
        NavController navController = NavHostFragment.findNavController(this);

        // Create an AppBarConfiguration.
        // If MyEventsFragment is a destination within a larger graph (e.g., from MainActivity),
        // and it's not a "top-level" destination itself (meaning it should show an Up arrow),
        // then passing navController.getGraph() is usually sufficient.
        // If MyEventsFragment had its own nested nav graph with its own top-level destinations,
        // you'd configure those here. For simple "Up" navigation, this is fine.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration
                .Builder(navController.getGraph()) // Pass the graph of the NavController this fragment belongs to
                .build();

        // Set this fragment's Toolbar as the SupportActionBar for the current Activity.
        // This allows NavigationUI to work with it.
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            // Link the Toolbar with the NavController.
            // This will automatically set up the Up button and handle its clicks.
            NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
        }
        // --- End Toolbar Setup ---

        // Indicate that this fragment would like to participate in populating
        // the options menu by receiving a call to onCreateOptionsMenu and
        // related methods.
        setHasOptionsMenu(true);

        // --- ViewPager2 and TabLayout Setup ---
        viewPagerMyEvents = view.findViewById(R.id.view_pager_my_events);
        tabLayoutMyEvents = view.findViewById(R.id.tab_layout_my_events);

        MyEventsPagerAdapter pagerAdapter = new MyEventsPagerAdapter(this);
        viewPagerMyEvents.setAdapter(pagerAdapter);

        // Link the TabLayout with the ViewPager2
        new TabLayoutMediator(tabLayoutMyEvents, viewPagerMyEvents,
                (tab, position) -> {
                    tab.setText(tabTitles[position]);
                    // Set content description for accessibility
                    // Ensure these string resources exist in your strings.xml
                    // e.g., <string name="content_desc_hosted_events_tab">Hosted Events Tab</string>
                    switch (position) {
                        case 0:
                            if (isAdded()) { // Check if fragment is added to an activity
                                tab.setContentDescription(getString(R.string.content_desc_hosted_events_tab));
                            }
                            break;
                        case 1:
                            if (isAdded()) {
                                tab.setContentDescription(getString(R.string.content_desc_applied_events_tab));
                            }
                            break;
                        // Add more cases if you have more tabs
                    }
                }
        ).attach();
        // --- End ViewPager2 and TabLayout Setup ---
    }

    // Adapter for the ViewPager2
    private static class MyEventsPagerAdapter extends FragmentStateAdapter {
        public MyEventsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new HostedEventsFragment(); // Your fragment for hosted events
                case 1:
                    return new MyAppliedEventsFragment(); // Your fragment for applied events
                default:
                    // Fallback, though with getItemCount() == 2, this shouldn't be reached.
                    return new HostedEventsFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Number of tabs
        }
    }

    // Handle options menu item selection (e.g., the "Up" button)
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Let NavigationUI handle menu item selection.
        // This will automatically handle the Up button (android.R.id.home) if the Toolbar
        // was set up correctly with the NavController using NavigationUI.setupWithNavController.
        NavController navController = NavHostFragment.findNavController(this);
        return NavigationUI.onNavDestinationSelected(item, navController)
                || super.onOptionsItemSelected(item);
    }
}
**/