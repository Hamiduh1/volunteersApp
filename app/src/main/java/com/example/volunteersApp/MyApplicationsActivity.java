package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
// import androidx.annotation.Nullable; // No longer needed for addSnapshotListener with lambda
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Import the Kotlin adapter and the ApplicationModel
import com.example.volunteersApp.models.ApplicationModel;
import com.example.volunteersApp.organizer.ApplicationAdapter; // Using the Kotlin adapter from the 'organizer' package

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// import com.google.firebase.firestore.CollectionReference; // Not directly used
import com.google.firebase.firestore.FirebaseFirestore;
// import com.google.firebase.firestore.FirebaseFirestoreException; // Handled in lambda
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
// import com.google.firebase.firestore.QuerySnapshot; // Handled in lambda

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
// For lambda usage with Kotlin adapter from Java
import kotlin.Unit;
import kotlin.jvm.functions.Function1;


// No longer implementing ApplicationAdapter.OnApplicationClickListener
public class MyApplicationsActivity extends AppCompatActivity {

    private static final String TAG = "MyApplicationsActivity";
    private static final String APPLICATIONS_COLLECTION = "applications"; // Or your Firestore collection name

    private RecyclerView recyclerViewMyApplications;
    private ApplicationAdapter applicationAdapter; // Will be com.example.volunteersApp.organizer.ApplicationAdapter
    private List<ApplicationModel> currentApplicationsList; // Changed to ApplicationModel


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
            getSupportActionBar().setTitle("My Applications");
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

        currentApplicationsList = new ArrayList<>();

        // Instantiate the Kotlin adapter
        // The Kotlin adapter expects lambdas for its callbacks.
        // onApplicationClicked: (application: ApplicationModel) -> Unit
        // onApproveClicked: ((ApplicationModel) -> Unit)? = null
        // onRejectClicked: ((ApplicationModel) -> Unit)? = null

        // Define the click listener lambda
        Function1<ApplicationModel, Unit> onAppClick = application -> {
            onApplicationItemClick(application); // Call a method to handle the click
            return Unit.INSTANCE; // Kotlin lambdas returning Unit need this from Java
        };

        // The Kotlin adapter from 'organizer' package has a constructor like:
        // ApplicationAdapter(context, onApplicationClicked, onApproveClicked, onRejectClicked)
        // For "My Applications" screen, approve/reject are likely not needed.
        applicationAdapter = new ApplicationAdapter(
                this,       // Context
                onAppClick, // onApplicationClicked lambda
                null,       // onApproveClicked (not applicable here, so pass null)
                null        // onRejectClicked (not applicable here, so pass null)
        );


        recyclerViewMyApplications.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMyApplications.setAdapter(applicationAdapter);
        recyclerViewMyApplications.setItemAnimator(null);
    }

    // Method to handle the application item click (previously the interface method)
    public void onApplicationItemClick(@NonNull ApplicationModel application) {
        Toast.makeText(this, "Clicked on application for: " + (application.getEventTitle() != null ? application.getEventTitle() : "N/A"), Toast.LENGTH_SHORT).show();
        // TODO: Implement navigation to an Application Detail screen or other action
        // Example:
        // Intent intent = new Intent(MyApplicationsActivity.this, ApplicationDetailActivity.class);
        // intent.putExtra("APPLICATION_ID", application.getApplicationId());
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

        // Query to get applications where the current user is the volunteer.
        // IMPORTANT: Ensure your 'applications' documents have a "volunteerId" or "userId" field
        // that stores the UID of the user who applied.
        Query userApplicationsQuery = db.collection(APPLICATIONS_COLLECTION)
                .whereEqualTo("volunteerId", currentUserId) // Or "userId" if that's your field for the applicant
                .orderBy("appliedAt", Query.Direction.DESCENDING); // Assuming 'appliedAt' is your timestamp field in ApplicationModel

        detachFirestoreListener();

        applicationsListenerRegistration = userApplicationsQuery.addSnapshotListener(this, (snapshots, e) -> {
            if (isFinishing()  || isDestroyed()) { // Check if activity is finishing
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
                if (applicationAdapter != null) applicationAdapter.submitList(new ArrayList<>(currentApplicationsList));
                return;
            }

            if (snapshots == null) {
                Log.w(TAG, "Received null QuerySnapshot. Assuming no applications.");
                currentApplicationsList.clear();
                if (applicationAdapter != null) applicationAdapter.submitList(new ArrayList<>(currentApplicationsList));
                checkIfListIsEmpty();
                return;
            }

            Log.d(TAG, "New data received. Documents count: " + snapshots.size());
            currentApplicationsList.clear();

            for (QueryDocumentSnapshot doc : snapshots) {
                try {
                    // Convert to ApplicationModel
                    ApplicationModel application = doc.toObject(ApplicationModel.class);
                    // ApplicationModel might not have a setApplicationId if it's a data class with val
                    // Ensure 'applicationId' is correctly mapped by Firestore or set it if needed (e.g. if it's a var)
                    // If 'applicationId' is a 'val' in ApplicationModel and part of constructor,
                    // Firestore should map it if field name matches or use @DocumentId.
                    // If it's a 'var', you might need a setter or copy.
                    // For simplicity, assuming Firestore maps it or 'applicationId' is a val.

                    currentApplicationsList.add(application);
                } catch (Exception conversionError) {
                    Log.e(TAG, "Error converting document to ApplicationModel object: " + doc.getId(), conversionError);
                }
            }

            Collections.sort(currentApplicationsList, (a1, a2) -> {
                Timestamp ts1 = a1.getAppliedAt(); // Assuming ApplicationModel has getAppliedAt()
                Timestamp ts2 = a2.getAppliedAt();

                if (ts1 == null && ts2 == null) return 0;
                if (ts1 == null) return 1;
                if (ts2 == null) return -1;
                return ts2.compareTo(ts1);
            });

            if (applicationAdapter != null) applicationAdapter.submitList(new ArrayList<>(currentApplicationsList)); // Submit a new copy

            checkIfListIsEmpty();
            if (!currentApplicationsList.isEmpty()) {
                recyclerViewMyApplications.setVisibility(View.VISIBLE);
            }
        });
        Log.d(TAG, "Attached Firestore SnapshotListener for user applications.");
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
                finish();
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
       // _binding = null; // Clear binding if you were using ViewBinding for the activity layout
    }
}
