package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull; // Keep for parameter annotations if any
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Import the Kotlin adapter and ApplicationModel
import com.example.volunteersApp.organizer.ApplicationAdapter;
import com.example.volunteersApp.models.ApplicationModel;
import com.example.volunteersApp.models.ApplicationStatus; // For approve/reject

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
// For lambda usage with Kotlin adapter from Java
import kotlin.Unit;
import kotlin.jvm.functions.Function1;


// No longer implementing ApplicationAdapter.OnApplicationClickListener from the Java adapter
public class ViewApplicationsActivity extends AppCompatActivity {

    private static final String TAG = "ViewAppsActivity";

    // Assuming these constants might need adjustment based on your Firestore structure for ApplicationModel
    private static final String JOB_POSTINGS_COLLECTION = "events"; // Typically "events" if job postings are events
    private static final String APPLICATIONS_SUBCOLLECTION = "applications"; // Subcollection under each event
    private static final String ORGANIZER_UID_FIELD = "organizerId"; // Field in "events" collection for the organizer's UID
    // Using 'appliedAt' from ApplicationModel, ensure this field exists in your Firestore 'applications' docs
    private static final String APPLICATION_TIMESTAMP_FIELD = "appliedAt";

    private RecyclerView recyclerViewApplications;
    private ApplicationAdapter applicationAdapter; // Will be com.example.volunteersApp.organizer.ApplicationAdapter
    private List<ApplicationModel> applicationList; // Changed to ApplicationModel

    private ProgressBar progressBarApplications;
    private TextView textViewApplicationsStatus;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    // You might need a ViewModel here to handle status updates more cleanly
    // For now, direct update for simplicity, but consider a ViewModel for production.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_applications);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Event Applications"); // More generic title
        }

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "You need to be logged in to view applications.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        recyclerViewApplications = findViewById(R.id.recyclerViewApplications);
        progressBarApplications = findViewById(R.id.progressBarApplications);
        textViewApplicationsStatus = findViewById(R.id.textViewApplicationsStatus);

        applicationList = new ArrayList<>();

        // Lambdas for the Kotlin adapter
        Function1<ApplicationModel, Unit> onAppClick = application -> {
            onApplicationItemClick(application);
            return Unit.INSTANCE;
        };

        Function1<ApplicationModel, Unit> onApprove = application -> {
            updateApplicationStatusInFirestore(application.getApplicationId(), application.getEventId(), ApplicationStatus.APPROVED, null);
            return Unit.INSTANCE;
        };

        Function1<ApplicationModel, Unit> onReject = application -> {
            // TODO: Consider showing a dialog to input a reason for rejection
            String reason = "Application did not meet requirements."; // Placeholder
            updateApplicationStatusInFirestore(application.getApplicationId(), application.getEventId(), ApplicationStatus.REJECTED, reason);
            return Unit.INSTANCE;
        };

        // Instantiate the Kotlin ApplicationAdapter from the 'organizer' package
        applicationAdapter = new ApplicationAdapter(
                this,
                onAppClick,
                onApprove,
                onReject
        );

        recyclerViewApplications.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewApplications.setAdapter(applicationAdapter);

        loadApplicationsForEmployer();
    }

    // Method to handle application item click
    public void onApplicationItemClick(@NonNull ApplicationModel application) {
        if (application == null || application.getApplicationId() == null || application.getEventId() == null) {
            Toast.makeText(this, "Application data is incomplete.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Clicked application with null ID or EventId.");
            return;
        }

        Toast.makeText(this, "Clicked: " + application.getVolunteerName(), Toast.LENGTH_SHORT).show();

        // Navigate to a detail screen (ensure ApplicationDetailActivity can handle ApplicationModel or its IDs)
        Intent intent = new Intent(this, ApplicationDetailActivity.class);
        intent.putExtra("APPLICATION_ID", application.getApplicationId()); // This is the application document ID
        intent.putExtra("EVENT_ID", application.getEventId()); // The event this application is for
        // Pass volunteerId if ApplicationDetailActivity needs it and it's part of ApplicationModel
        intent.putExtra("VOLUNTEER_ID", application.getVolunteerId());
        startActivity(intent);
    }


    private void loadApplicationsForEmployer() {
        setLoadingState(true);
        applicationList.clear();

        // Get all events hosted by the current user
        db.collection(JOB_POSTINGS_COLLECTION) // Using "events" as the collection for job postings/events
                .whereEqualTo(ORGANIZER_UID_FIELD, currentUser.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoadingState(false);
                        Log.e(TAG, "Error fetching employer events.", task.getException());
                        if (textViewApplicationsStatus != null) {
                            textViewApplicationsStatus.setText("Failed to load your events.");
                            textViewApplicationsStatus.setVisibility(View.VISIBLE);
                        }
                        updateUiBasedOnApplicationList(); // Update UI, will show "no applications"
                        return;
                    }

                    QuerySnapshot jobPostingsSnapshot = task.getResult();
                    if (jobPostingsSnapshot == null || jobPostingsSnapshot.isEmpty()) {
                        Log.d(TAG, "No events found for employer: " + currentUser.getUid());
                        setLoadingState(false);
                        updateUiBasedOnApplicationList(); // Show "no applications"
                        return;
                    }

                    List<Task<QuerySnapshot>> applicationFetchTasks = new ArrayList<>();
                    // final List<String> jobPostingIds = new ArrayList<>(); // To map apps to event IDs if needed later, now part of ApplicationModel

                    for (QueryDocumentSnapshot eventDoc : jobPostingsSnapshot) {
                        String eventId = eventDoc.getId();
                        // jobPostingIds.add(eventId);

                        // For each event, get its 'applications' subcollection
                        Task<QuerySnapshot> fetchAppsTask = db.collection(JOB_POSTINGS_COLLECTION)
                                .document(eventId)
                                .collection(APPLICATIONS_SUBCOLLECTION)
                                // .orderBy(APPLICATION_TIMESTAMP_FIELD, Query.Direction.DESCENDING) // Done with client-side sort
                                .get();
                        applicationFetchTasks.add(fetchAppsTask);
                    }

                    if (applicationFetchTasks.isEmpty()) {
                        setLoadingState(false);
                        updateUiBasedOnApplicationList();
                        return;
                    }

                    Tasks.whenAllSuccess(applicationFetchTasks).addOnSuccessListener(results -> {
                        applicationList.clear();
                        for (int i = 0; i < results.size(); i++) {
                            QuerySnapshot applicationsSnapshot = (QuerySnapshot) results.get(i);
                            // String currentEventId = jobPostingIds.get(i); // Event ID is now in ApplicationModel

                            if (applicationsSnapshot != null && !applicationsSnapshot.isEmpty()) {
                                for (QueryDocumentSnapshot appDoc : applicationsSnapshot) {
                                    try {
                                        ApplicationModel application = appDoc.toObject(ApplicationModel.class);
                                        // Ensure applicationId is set (if not a @DocumentId in model)
                                        // and eventId is set if not mapped directly by Firestore from parent.
                                        // ApplicationModel should have eventId populated if it's a field
                                        // in the Firestore document for the application.
                                        // application.setEventId(currentEventId); // Only if eventId isn't in the appDoc

                                        applicationList.add(application);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error converting application document to ApplicationModel for event, app ID: "
                                                + appDoc.getId(), e);
                                    }
                                }
                            }
                        }
                        setLoadingState(false);
                        updateUiBasedOnApplicationList();
                    }).addOnFailureListener(e -> {
                        setLoadingState(false);
                        Log.e(TAG, "Failed to fetch one or more application sets.", e);
                        if (textViewApplicationsStatus != null) {
                            textViewApplicationsStatus.setText("Error loading some applications.");
                            textViewApplicationsStatus.setVisibility(View.VISIBLE);
                        }
                        updateUiBasedOnApplicationList();
                    });
                });
    }


    private void setLoadingState(boolean isLoading) {
        if (progressBarApplications == null || textViewApplicationsStatus == null || recyclerViewApplications == null) return;
        if (isLoading) {
            progressBarApplications.setVisibility(View.VISIBLE);
            textViewApplicationsStatus.setVisibility(View.GONE);
            recyclerViewApplications.setVisibility(View.GONE);
        } else {
            progressBarApplications.setVisibility(View.GONE);
        }
    }

    private void updateUiBasedOnApplicationList() {
        if (applicationList == null) {
            applicationList = new ArrayList<>();
        }

        try {
            Collections.sort(applicationList, (app1, app2) -> {
                Timestamp ts1 = app1.getAppliedAt(); // Use getAppliedAt from ApplicationModel
                Timestamp ts2 = app2.getAppliedAt();
                if (ts1 == null && ts2 == null) return 0;
                if (ts1 == null) return 1;
                if (ts2 == null) return -1;
                return ts2.compareTo(ts1); // Newest first
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sorting applications by timestamp.", e);
        }

        if (applicationAdapter != null) {
            applicationAdapter.submitList(new ArrayList<>(applicationList)); // Submit new list copy
        }

        if (textViewApplicationsStatus == null || recyclerViewApplications == null) return;

        if (applicationList.isEmpty()) {
            textViewApplicationsStatus.setText("No applications found for your events.");
            textViewApplicationsStatus.setVisibility(View.VISIBLE);
            recyclerViewApplications.setVisibility(View.GONE);
        } else {
            textViewApplicationsStatus.setVisibility(View.GONE);
            recyclerViewApplications.setVisibility(View.VISIBLE);
        }
    }

    // This method updates the status in Firestore.
    // Consider moving this logic to a ViewModel for better architecture.
    private void updateApplicationStatusInFirestore(String applicationId, String eventId, ApplicationStatus newStatus, @Nullable String reason) {
        if (applicationId == null || eventId == null) {
            Toast.makeText(this, "Error: Missing application or event ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Path to the application document: /events/{eventId}/applications/{applicationId}
        // OR /applications/{applicationId} if applications are a top-level collection and have eventId field.
        // Assuming applications are a subcollection of events for this example.
        Log.d(TAG, "Updating status for app: " + applicationId + " in event: " + eventId + " to " + newStatus.name());

        db.collection(JOB_POSTINGS_COLLECTION).document(eventId)
                .collection(APPLICATIONS_SUBCOLLECTION).document(applicationId)
                .update("applicationStatus", newStatus.name(),
                        "reasonForRejection", (newStatus == ApplicationStatus.REJECTED ? reason : null),
                        "lastUpdatedAt", Timestamp.now()) // Update lastUpdatedAt
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ViewApplicationsActivity.this, "Application status updated to " + newStatus.name(), Toast.LENGTH_SHORT).show();
                    // The list will auto-refresh if loadApplicationsForEmployer uses a snapshot listener.
                    // If not using a snapshot listener, you might need to manually call loadApplicationsForEmployer() here.
                    // For now, assuming snapshot listener handles the refresh.
                    Log.d(TAG, "Successfully updated application status for " + applicationId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ViewApplicationsActivity.this, "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error updating application status for " + applicationId, e);
                });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
