// MyEventsContainerActivity.java
package com.example.volunteersApp; // Or your activities package

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.volunteersApp.fragments.MyEventsFragment; // Import your fragment

public class MyEventsContainerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Option 1: A simple layout with just a FragmentContainerView
        setContentView(R.layout.activity_my_events_container); // Create this layout file

        if (savedInstanceState == null) { // Load fragment only once
            MyEventsFragment myEventsFragment = new MyEventsFragment();

            // If you passed data from HostFinal, retrieve it here and pass to fragment
            if (getIntent().hasExtra(HostFinal.EXTRA_NEWLY_CREATED_EVENT_ID)) {
                String newlyCreatedEventId = getIntent().getStringExtra(HostFinal.EXTRA_NEWLY_CREATED_EVENT_ID);
                Bundle args = new Bundle();
                args.putString("HIGHLIGHT_EVENT_ID", newlyCreatedEventId); // Example key
                myEventsFragment.setArguments(args);
            }

            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            // Replace R.id.my_events_fragment_container with the ID of your FragmentContainerView
            fragmentTransaction.replace(R.id.my_events_fragment_container, myEventsFragment);
            fragmentTransaction.commit();
        }
    }
}
