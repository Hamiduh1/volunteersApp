package com.example.volunteersApp;

import android.content.Intent; // Added for potential navigation
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Now primarily using EventApplication
import com.example.volunteersApp.models.EventApplication;
// ApplicationAdapter from the 'organizer' package expects EventApplication
import com.example.volunteersApp.organizer.ApplicationAdapter;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2; // For adapter's onRejectClicked if it takes reason

public class MyApplicationsActivity extends AppCompatActivity {

    private static final String TAG = "MyApplicationsActivity";
    // IMPORTANT: Verify this is the correct collection for EventApplications made by the user.
    // It might be "eventApplications", or applications could be a subcollection under each event.
    // This example assumes a top-level collection where applications are queried by volunteerUid.
    private static final String APPLICATIONS_COLLECTION = "eventApplications"; // Or your actual collection name

    private RecyclerView recyclerViewMyApplications;
    private ApplicationAdapter applicationAdapter; // from com.example.volunteersApp.organizer
    private List<EventApplication> currentApplicationsList; // <<< CHANGED to EventApplication

    private ProgressBar progressBarMyApplications;
    private TextView textViewNoApplications;

    private FirebaseAuth mAuth;
    private String currentUserId;

    private FirebaseFirestore db;
    private ListenerRegistration applicationsListenerRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_applications);

        Toolbar toolbar = findViewById(R.id.toolbarMyApplications);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Event Applications"); // Title updated
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser firebaseUser = mAuth.getCurrentUser();

        if (firebaseUser == null) {
            Toast.makeText(this, "Please log in to view your applications.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();
        db = FirebaseFirestore.getInstance();

        recyclerViewMyApplications = findViewById(R.id.recyclerViewMyApplications);
        progressBarMyApplications = findViewById(R.id.progressBarMyApplications);
        textViewNoApplications = findViewById(R.id.textViewNoApplications);

        currentApplicationsList = new ArrayList<>(); // Now an ArrayList of EventApplication

        // Define the click listener lambda for EventApplication
        Function1<EventApplication, Unit> onAppClick = application -> { // <<< CHANGED to EventApplication
            onApplicationItemClick(application);
            return Unit.INSTANCE;
        };

        // The organizer.ApplicationAdapter constructor (as per previous discussions):
        // ApplicationAdapter(
        //      context: Context,
        //      onApplicationClicked: (EventApplication) -> Unit,
        //      onApproveClicked: ((EventApplication) -> Unit)? = null,
        //      onRejectClicked: ((application: EventApplication, reason: String?) -> Unit)? = null
        // )
        // For "My Applications" screen, approve/reject are not performed by the volunteer.
        applicationAdapter = new ApplicationAdapter(
                this,       // Context
                onAppClick, // onApplicationClicked lambda
                null,       // onApproveClicked (volunteer doesn't approve/reject their own)
                null        // onRejectClicked (volunteer doesn't approve/reject their own)
        );

        recyclerViewMyApplications.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMyApplications.setAdapter(applicationAdapter);
        recyclerViewMyApplications.setItemAnimator(null); // Optional: remove default animations
    }

    // Method to handle the application item click
    public void onApplicationItemClick(@NonNull EventApplication application) { // <<< CHANGED to EventApplication
        Toast.makeText(this, "Clicked on application for event: " +
                (application.getEventTitle() != null ? application.getEventTitle() : "N/A"), Toast.LENGTH_SHORT).show();

        // TODO: Implement navigation to an Event Detail screen or Application Detail screen
        // Example: Navigate to a generic detail screen for the event the application is for.
        // Intent intent = new Intent(MyApplicationsActivity.this, EventDetailActivity.class);
        // intent.putExtra("EVENT_ID", application.getEventId());
        // intent.putExtra("APPLICATION_ID", application.getApplicationId()); // Optionally pass app ID too
        // startActivity(intent);
    }

    private void loadApplicationsFromFirestore() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "currentUserId is null or empty. Cannot load applications.");
            textViewNoApplications.setText("User ID not found. Cannot load applications.");
            textViewNoApplications.setVisibility(View.VISIBLE);
            progressBarMyApplications.setVisibility(View.GONE);
            recyclerViewMyApplications.setVisibility(View.GONE);
            return;
        }

        progressBarMyApplications.setVisibility(View.VISIBLE);
        textViewNoApplications.setVisibility(View.GONE);
        recyclerViewMyApplications.setVisibility(View.GONE);

        // Query to get EventApplications where the current user is the volunteer.
        // Ensure your 'eventApplications' documents have a "volunteerUid" field.
        Query userApplicationsQuery = db.collection(APPLICATIONS_COLLECTION)
                .whereEqualTo("volunteerUid", currentUserId) // <<< CHANGED to volunteerUid (as in EventApplication.java)
                .orderBy("applicationTimestamp", Query.Direction.DESCENDING); // <<< CHANGED to applicationTimestamp

        detachFirestoreListener();

        applicationsListenerRegistration = userApplicationsQuery.addSnapshotListener(this, (snapshots, e) -> {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Activity is finishing, snapshot listener callback skipped.");
                return;
            }
            progressBarMyApplications.setVisibility(View.GONE);

            if (e != null) {
                Log.e(TAG, "Firestore listen failed:", e);
                Toast.makeText(MyApplicationsActivity.this, "Failed to load applications: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                textViewNoApplications.setText("Failed to load applications.");
                textViewNoApplications.setVisibility(View.VISIBLE);
                recyclerViewMyApplications.setVisibility(View.GONE);
                currentApplicationsList.clear();
                // Submit an empty list of the correct type
                if (applicationAdapter != null) applicationAdapter.submitList(new ArrayList<EventApplication>());
                return;
            }

            if (snapshots == null) {
                Log.w(TAG, "Received null QuerySnapshot. Assuming no applications.");
                currentApplicationsList.clear();
                if (applicationAdapter != null) applicationAdapter.submitList(new ArrayList<EventApplication>());
                checkIfListIsEmpty();
                return;
            }

            Log.d(TAG, "New data received. Documents count: " + snapshots.size());
            currentApplicationsList.clear();

            for (QueryDocumentSnapshot doc : snapshots) {
                try {
                    // Convert to EventApplication
                    EventApplication application = doc.toObject(EventApplication.class); // <<< CHANGED to EventApplication.class
                    // EventApplication has setApplicationId via @DocumentId or if you defined a setter.
                    // If applicationId is not set automatically by Firestore (e.g. not a public field or no setter with @DocumentId)
                    // and you need it:
                    // application.setApplicationId(doc.getId()); // Ensure EventApplication has this setter
                    currentApplicationsList.add(application);
                } catch (Exception conversionError) {
                    Log.e(TAG, "Error converting document to EventApplication object: " + doc.getId(), conversionError);
                }
            }

            // Sort by applicationTimestamp (descending, same as query but good for client-side consistency if needed)
            Collections.sort(currentApplicationsList, (a1, a2) -> {
                Timestamp ts1 = a1.getApplicationTimestamp(); // <<< CHANGED to getApplicationTimestamp
                Timestamp ts2 = a2.getApplicationTimestamp(); // <<< CHANGED to getApplicationTimestamp

                if (ts1 == null && ts2 == null) return 0;
                if (ts1 == null) return 1; // nulls last
                if (ts2 == null) return -1; // nulls last
                return ts2.compareTo(ts1); // Descending
            });

            // Submit a new copy of the list of EventApplication
            if (applicationAdapter != null) applicationAdapter.submitList(new ArrayList<>(currentApplicationsList));

            checkIfListIsEmpty();
            if (!currentApplicationsList.isEmpty()) {
                recyclerViewMyApplications.setVisibility(View.VISIBLE);
            }
        });
        Log.d(TAG, "Attached Firestore SnapshotListener for user's event applications.");
    }

    private void checkIfListIsEmpty() {
        if (currentApplicationsList.isEmpty()) {
            textViewNoApplications.setText(R.string.you_havent_applied_to_any_opportunities_yet);
            textViewNoApplications.setVisibility(View.VISIBLE);
            recyclerViewMyApplications.setVisibility(View.GONE);
        } else {
            textViewNoApplications.setVisibility(View.GONE);
            recyclerViewMyApplications.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadApplicationsFromFirestore();
        } else {
            Log.w(TAG, "onStart: currentUserId is null or empty. Not loading applications.");
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(this, "Please log in to view your applications.", Toast.LENGTH_LONG).show();
                // finish(); // Consider if you want to finish immediately or let them log in.
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachFirestoreListener();
    }

    private void detachFirestoreListener() {
        if (applicationsListenerRegistration != null) {
            applicationsListenerRegistration.remove();
            applicationsListenerRegistration = null;
            Log.d(TAG, "Detached Firestore SnapshotListener.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachFirestoreListener();
    }
}
