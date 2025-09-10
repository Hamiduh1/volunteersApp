package com.example.volunteersApp; // Your existing package name

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem; // For handling toolbar back arrow

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

// Import your fragment
import com.example.volunteersApp.fragments.AvailableJobsFragment;

// Remove imports that are now handled by the fragment:
// import android.content.Intent;
// import android.view.View;
// import android.widget.ProgressBar;
// import android.widget.TextView;
// import android.widget.Toast;
// import androidx.recyclerview.widget.LinearLayoutManager;
// import androidx.recyclerview.widget.RecyclerView;
// import com.example.volunteersApp.adapters.JobPostAdapter;
// import com.example.volunteersApp.models.JobPost;
// import com.google.firebase.auth.FirebaseAuth;
// import com.google.firebase.auth.FirebaseUser;
// import com.google.firebase.firestore.CollectionReference;
// import com.google.firebase.firestore.DocumentSnapshot;
// import com.google.firebase.firestore.FirebaseFirestore;
// import com.google.firebase.firestore.Query;
// import com.google.firebase.firestore.QueryDocumentSnapshot;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.stream.Collectors;


// The Activity no longer needs to implement JobPostAdapter.OnJobPostActionListener
public class AvailableJobsActivity extends AppCompatActivity {

    private static final String TAG = "AvailableJobsActivity";

    // All fields related to RecyclerView, data fetching, adapter, etc.,
    // are removed from the Activity as they are now managed by AvailableJobsFragment.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // You'll need a new layout for this activity, or adapt the existing one
        // to include a FrameLayout as a fragment container.
        setContentView(R.layout.activity_available_jobs_host); // e.g., activity_available_jobs_host.xml
        Log.d(TAG, "onCreate: Activity created to host fragment.");

        Toolbar toolbar = findViewById(R.id.toolbar_available_jobs_host); // Ensure this ID is in your new layout
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.available_opportunities_title); // Use a string resource
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // If savedInstanceState is null, it's the first time the activity is created.
        // Add the fragment.
        if (savedInstanceState == null) {
            Log.d(TAG, "onCreate: No saved instance state, creating and adding AvailableJobsFragment.");
            AvailableJobsFragment availableJobsFragment = new AvailableJobsFragment();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            // Replace R.id.fragment_container with the ID of the FrameLayout in your activity_available_jobs_host.xml
            transaction.replace(R.id.fragment_container_available_jobs, availableJobsFragment);
            transaction.commit();
        } else {
            // If savedInstanceState is not null, the fragment will be automatically restored by the system.
            Log.d(TAG, "onCreate: Restoring from saved instance state. Fragment should be automatically restored by FragmentManager.");
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle presses on the action bar items
        if (item.getItemId() == android.R.id.home) {
            // Respond to the action bar's Up/Home button
            // This will take the user back to the previous activity or screen.
            onSupportNavigateUp(); // Or finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // This method is called when the up button is pressed.
        // It's good practice to navigate up the app's hierarchy.
        // If you're simply going back, onBackPressed() is fine.
        onBackPressed();
        return true;
    }

    // All data loading methods (loadInitialJobPosts, loadMoreJobPosts),
    // UI update methods (updateUIBasedOnData, setLoading),
    // scroll listener setup (setupScrollListener),
    // and JobPostAdapter.OnJobPostActionListener interface methods (onViewDetailsClicked, onJobPostDelete)
    // are REMOVED from this Activity. They are now the responsibility of AvailableJobsFragment.
}
