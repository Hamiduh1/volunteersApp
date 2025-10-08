package com.example.volunteersApp;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

// UPDATED: Import VolunteeringFragment instead of BrowseVolunteerEventsFragment
import com.example.volunteersApp.VolunteeringFragment; // Ensure this is the correct package and class name

import java.util.Objects;

public class AvailableEventsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure you have a layout file: res/layout/activity_available_events.xml
        // This layout should contain a Toolbar with id "toolbarAvailableEvents"
        // and a FrameLayout with id "fragment_container_available_events"
        setContentView(R.layout.activity_available_events);

        Toolbar toolbar = findViewById(R.id.toolbarAvailableEvents);
        setSupportActionBar(toolbar);
        // Consider using a string resource for the title
        setTitle(getString(R.string.title_available_volunteer_events)); // Example: R.string.title_available_volunteer_events
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (savedInstanceState == null) {
            // Only add the fragment if the activity is newly created (not on configuration change)
            loadVolunteeringFragment(); // UPDATED method name for clarity
        }
    }

    private void loadVolunteeringFragment() { // UPDATED method name
        // Create an instance of VolunteeringFragment
        // Check if VolunteeringFragment requires any arguments via newInstance pattern
        // For now, assuming a default constructor or a newInstance() without arguments.
        Fragment volunteeringFragment; // Declare as Fragment type
        try {
            // If VolunteeringFragment has a static newInstance() method (recommended):
            // volunteeringFragment = VolunteeringFragment.newInstance();
            // Or if it simply uses a public no-arg constructor:
            volunteeringFragment = new VolunteeringFragment();
        } catch (Exception e) {
            // Handle case where fragment instantiation might fail (e.g. class not found if package is wrong)
            // Log.e("AvailableEventsActivity", "Error instantiating VolunteeringFragment", e);
            // Toast.makeText(this, "Error loading events screen.", Toast.LENGTH_SHORT).show();
            // finish(); // Optionally close the activity if the fragment can't be loaded
            return; // Exit if fragment cannot be created
        }


        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // Replace the FrameLayout in activity_available_events.xml with this fragment
        // Ensure R.id.fragment_container_available_events exists in your activity_available_events.xml
        fragmentTransaction.replace(R.id.fragment_container_available_events, volunteeringFragment);
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
