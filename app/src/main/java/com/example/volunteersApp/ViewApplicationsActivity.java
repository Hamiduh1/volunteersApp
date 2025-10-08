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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Using EventApplication and its related status enum
import com.example.volunteersApp.models.EventApplication;
import com.example.volunteersApp.models.EventApplicationStatus; // For approve/reject status
import com.example.volunteersApp.organizer.ApplicationAdapter; // Adapter from organizer package

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query; // For orderBy
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// For lambda usage with Kotlin adapter from Java
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2; // For onRejectClicked with reason

public class ViewApplicationsActivity extends AppCompatActivity {

    private static final String TAG = "ViewAppsActivity";

    // Constants for Firestore structure
    private static final String EVENTS_COLLECTION = "events"; // Collection where events are stored
    private static final String APPLICATIONS_SUBCOLLECTION = "applications"; // Subcollection under each event for applications
    private static final String ORGANIZER_UID_FIELD_IN_EVENTS = "organizerUid"; // Field in "events" for organizer's UID (ensure this matches EventModel)
    // Field in the 'applications' subcollection for sorting by application time
    private static final String APPLICATION_TIMESTAMP_FIELD_IN_APPS = "applicationTimestamp"; // Matches EventApplication.java

    private RecyclerView recyclerViewApplications;
    private ApplicationAdapter applicationAdapter; // from com.example.volunteersApp.organizer
    private List<EventApplication> applicationList; // <<< CHANGED to EventApplication

    private ProgressBar progressBarApplications;
    private TextView textViewApplicationsStatus;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_applications);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("View Event Applications"); // Updated title
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

        // Lambdas for the Kotlin adapter, now expecting EventApplication
        Function1<EventApplication, Unit> onAppClick = application -> { // <<< CHANGED
            onApplicationItemClick(application);
            return Unit.INSTANCE;
        };

        // This lambda matches the adapter's onApproveClicked: ((EventApplication) -> Unit)?
        Function1<EventApplication, Unit> onApprove = application -> { // <<< CHANGED
            updateApplicationStatusInFirestore(
                    application.getApplicationId(),
                    application.getEventId(), // EventApplication has eventId
                    EventApplicationStatus.APPROVED, // Use EventApplicationStatus
                    null
            );
            return Unit.INSTANCE;
        };

        // This lambda matches the adapter's onRejectClicked: ((application: EventApplication, reason: String?) -> Unit)?
        Function2<EventApplication, String, Unit> onReject = (application, reason) -> { // <<< CHANGED
            updateApplicationStatusInFirestore(
                    application.getApplicationId(),
                    application.getEventId(),
                    EventApplicationStatus.REJECTED, // Use EventApplicationStatus
                    reason // Reason comes from adapter's dialog
            );
            return Unit.INSTANCE;
        };

        // Instantiate the Kotlin ApplicationAdapter
        // Constructor: ApplicationAdapter(context, onApplicationClicked, onApproveClicked, onRejectClicked)
        applicationAdapter = new ApplicationAdapter(
                this,
                onAppClick,
                onApprove,
                onReject
        );

        recyclerViewApplications.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewApplications.setAdapter(applicationAdapter);

        loadApplicationsForOrganizer(); // Renamed for clarity
    }

    // Method to handle application item click
    public void onApplicationItemClick(@NonNull EventApplication application) { // <<< CHANGED
        if (application.getApplicationId() == null || application.getEventId() == null) {
            Toast.makeText(this, "Application data is incomplete.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Clicked application with null Application ID or EventId.");
            return;
        }

        Toast.makeText(this, "Clicked: " + (application.getVolunteerName() != null ? application.getVolunteerName() : "N/A"), Toast.LENGTH_SHORT).show();

        // Navigate to a detail screen (e.g., ApplicationDetailFragment)
        Intent intent = new Intent(this, ApplicationDetailActivity.class); // Ensure this activity/fragment can handle EventApplication details
        intent.putExtra("APPLICATION_ID", application.getApplicationId());
        intent.putExtra("EVENT_ID", application.getEventId());
        // Optionally pass volunteerUid if ApplicationDetailActivity needs it
        // intent.putExtra("VOLUNTEER_ID", application.getVolunteerUid());
        startActivity(intent);
    }

    private void loadApplicationsForOrganizer() {
        setLoadingState(true);
        applicationList.clear();

        // 1. Get all events hosted by the current organizer
        db.collection(EVENTS_COLLECTION)
                .whereEqualTo(ORGANIZER_UID_FIELD_IN_EVENTS, currentUser.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        setLoadingState(false);
                        Log.e(TAG, "Error fetching organizer events.", task.getException());
                        if (textViewApplicationsStatus != null) {
                            textViewApplicationsStatus.setText("Failed to load your events.");
                            textViewApplicationsStatus.setVisibility(View.VISIBLE);
                        }
                        updateUiBasedOnApplicationList();
                        return;
                    }

                    QuerySnapshot eventsSnapshot = task.getResult();
                    if (eventsSnapshot.isEmpty()) {
                        Log.d(TAG, "No events found for organizer: " + currentUser.getUid());
                        setLoadingState(false);
                        updateUiBasedOnApplicationList();
                        return;
                    }

                    List<Task<QuerySnapshot>> applicationFetchTasks = new ArrayList<>();

                    for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                        String eventId = eventDoc.getId();
                        // 2. For each event, get its 'applications' subcollection
                        Task<QuerySnapshot> fetchAppsTask = db.collection(EVENTS_COLLECTION)
                                .document(eventId)
                                .collection(APPLICATIONS_SUBCOLLECTION)
                                .orderBy(APPLICATION_TIMESTAMP_FIELD_IN_APPS, Query.Direction.DESCENDING) // Sort by timestamp
                                .get();
                        applicationFetchTasks.add(fetchAppsTask);
                    }

                    if (applicationFetchTasks.isEmpty()) {
                        setLoadingState(false);
                        updateUiBasedOnApplicationList();
                        return;
                    }

                    // 3. When all application fetches are successful
                    Tasks.whenAllSuccess(applicationFetchTasks).addOnSuccessListener(results -> {
                        applicationList.clear();
                        for (Object result : results) {
                            if (result instanceof QuerySnapshot) {
                                QuerySnapshot applicationsSnapshot = (QuerySnapshot) result;
                                if (!applicationsSnapshot.isEmpty()) {
                                    for (QueryDocumentSnapshot appDoc : applicationsSnapshot) {
                                        try {
                                            // Convert to EventApplication
                                            EventApplication application = appDoc.toObject(EventApplication.class); // <<< CHANGED
                                            // @DocumentId in EventApplication should handle applicationId.
                                            // eventId is implicitly known from parent or should be a field in EventApplication.
                                            // If EventApplication model doesn't store eventId but you need it:
                                            // String parentEventId = appDoc.getReference().getParent().getParent().getId();
                                            // application.setEventId(parentEventId); // (Requires setter in EventApplication)

                                            applicationList.add(application);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error converting application document to EventApplication, app ID: "
                                                    + appDoc.getId(), e);
                                        }
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
            // Visibility of other views is handled by updateUiBasedOnApplicationList
        }
    }

    private void updateUiBasedOnApplicationList() {
        if (applicationList == null) { // Should not happen if initialized in onCreate
            applicationList = new ArrayList<>();
        }

        // Sorting is already done by Firestore query, but if client-side sort is preferred:
        // Collections.sort(applicationList, (app1, app2) -> {
        //     Timestamp ts1 = app1.getApplicationTimestamp(); // <<< CHANGED
        //     Timestamp ts2 = app2.getApplicationTimestamp(); // <<< CHANGED
        //     if (ts1 == null && ts2 == null) return 0;
        //     if (ts1 == null) return 1;
        //     if (ts2 == null) return -1;
        //     return ts2.compareTo(ts1); // Newest first
        // });

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

    private void updateApplicationStatusInFirestore(String applicationId, String eventId,
                                                    EventApplicationStatus newStatus, @Nullable String reason) { // <<< CHANGED to EventApplicationStatus
        if (applicationId == null || eventId == null) {
            Toast.makeText(this, "Error: Missing application or event ID.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "updateApplicationStatusInFirestore: applicationId or eventId is null.");
            return;
        }

        Log.d(TAG, "Updating status for app: " + applicationId + " in event: " + eventId + " to " + newStatus.name());

        db.collection(EVENTS_COLLECTION).document(eventId)
                .collection(APPLICATIONS_SUBCOLLECTION).document(applicationId)
                .update("status", newStatus.name(), // Ensure your Firestore field for status is "status"
                        "reasonForRejection", (newStatus == EventApplicationStatus.REJECTED ? reason : null),
                        "lastUpdatedAt", Timestamp.now()) // Optional: track updates
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ViewApplicationsActivity.this, "Application status updated to " + newStatus.name(), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Successfully updated application status for " + applicationId);
                    // No need to manually reload if Firestore query has orderBy.
                    // If you were not using orderBy in the query and relied on client sort,
                    // or if the update doesn't trigger a snapshot listener refresh of the exact item,
                    // you might need to call loadApplicationsForOrganizer() here.
                    // However, with orderBy and snapshot listener (if used), this should be fine.
                    // For direct .get(), a manual refresh would be needed: loadApplicationsForOrganizer();
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

