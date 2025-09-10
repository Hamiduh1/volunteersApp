package com.example.volunteersApp;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.volunteersApp.fragments.BrowseEventsFragment; // We will create this fragment

import java.util.Objects;

public class AvailableEventsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We'll create this layout file next: res/layout/activity_available_events.xml
        setContentView(R.layout.activity_available_events);

        Toolbar toolbar = findViewById(R.id.toolbarAvailableEvents); // ID from activity_available_events.xml
        setSupportActionBar(toolbar);
        setTitle("Available Volunteer Events"); // Or "Browse Events", "Upcoming Events"
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (savedInstanceState == null) {
            // Only add the fragment if the activity is newly created (not on configuration change)
            loadBrowseEventsFragment();
        }
    }

    private void loadBrowseEventsFragment() {
        // Create an instance of your fragment that will display the list of all available events
        Fragment browseEventsFragment = new BrowseEventsFragment(); // We need to create this

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // Replace the FrameLayout in activity_available_events.xml with this fragment
        // We'll use R.id.fragment_container_available_events as the ID for the FrameLayout
        fragmentTransaction.replace(R.id.fragment_container_available_events, browseEventsFragment);
        fragmentTransaction.commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle presses on the action bar items
        if (item.getItemId() == android.R.id.home) {
            // This will take the user back to the previous activity or close this one
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
