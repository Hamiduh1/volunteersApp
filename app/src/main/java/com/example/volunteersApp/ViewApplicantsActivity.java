package com.example.volunteersApp;

import android.content.Intent;
import android.net.Uri;
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

import com.example.volunteersApp.adapters.ApplicantAdapter;
import com.example.volunteersApp.models.ApplicationWithUserDetails;
import com.example.volunteersApp.models.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
// import com.google.firebase.firestore.Query; // Uncomment if you use .orderBy

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger; // Import AtomicInteger

public class ViewApplicantsActivity extends AppCompatActivity implements ApplicantAdapter.OnApplicantActionListener {

    private static final String TAG = "ViewApplicantsActivity";
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_NAME = "event_name";

    private String eventId;
    private String eventName;

    private RecyclerView recyclerViewApplicants;
    private ApplicantAdapter applicantAdapter;
    private List<ApplicationWithUserDetails> applicantDetailsList;

    private FirebaseFirestore db;
    private ListenerRegistration eventApplicationsListener;

    private ProgressBar progressBar;
    private TextView textViewStatus;
    private TextView textViewNoApplicants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_applicants);

        Log.d(TAG, "onCreate: Activity started.");

        Toolbar toolbar = findViewById(R.id.toolbar_view_applicants);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            eventName = getIntent().getStringExtra(EXTRA_EVENT_NAME);
            if (eventName != null && !eventName.isEmpty()) {
                getSupportActionBar().setTitle(getString(R.string.applicants_for_event_title, eventName));
            } else {
                getSupportActionBar().setTitle(getString(R.string.view_applicants_default_title));
            }
        }


        db = FirebaseFirestore.getInstance();
        applicantDetailsList = new ArrayList<>();

        recyclerViewApplicants = findViewById(R.id.recyclerViewApplicants);
        progressBar = findViewById(R.id.progressBarViewApplicants);
        textViewStatus = findViewById(R.id.textViewStatusViewApplicants);
        textViewNoApplicants = findViewById(R.id.textViewNoApplicants);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Event ID is null or empty. Cannot load applicants.");
            Toast.makeText(this, "Error: Event ID missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupRecyclerView();
        Log.d(TAG, "onCreate: Setup complete for event ID: " + eventId);
    }

    private void setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Initializing RecyclerView and Adapter.");
        // NEW (Correct)
        applicantAdapter = new ApplicantAdapter(this, this);
        recyclerViewApplicants.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewApplicants.setAdapter(applicantAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Fetching applicants for event ID: " + eventId);
        fetchApplications();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Removing listeners.");
        if (eventApplicationsListener != null) {
            eventApplicationsListener.remove();
        }
    }

    private void fetchApplications() {
        if (eventId == null) return;
        Log.d(TAG, "fetchApplications: Querying 'eventapplications' for event: " + eventId);

        showLoading(true);
        textViewNoApplicants.setVisibility(View.GONE);
        recyclerViewApplicants.setVisibility(View.GONE);
        textViewStatus.setVisibility(View.GONE);

        eventApplicationsListener = db.collection("events").document(eventId)
                .collection("eventapplications")
                // .orderBy("timestamp", Query.Direction.ASCENDING) // Optional: ensure "timestamp" field exists
                .addSnapshotListener((applicationSnapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching applications:", e);
                        showLoading(false);
                        showError(getString(R.string.error_fetching_applicants_details, e.getMessage()));
                        return;
                    }

                    if (applicationSnapshots == null) {
                        Log.w(TAG, "Application snapshots are null for event: " + eventId);
                        showLoading(false);
                        showError(getString(R.string.error_fetching_applicants_details, "Received null data."));
                        return;
                    }

                    Log.d(TAG, "fetchApplications: Processing " + applicationSnapshots.size() + " application entries.");

                    // Use a final list for this specific snapshot processing cycle to avoid concurrent modification issues
                    // with the main applicantDetailsList if the listener fires again quickly.
                    // The main list will be updated at the end of all async operations for this snapshot.
                    final List<ApplicationWithUserDetails> currentSnapshotProcessingList = new ArrayList<>();


                    if (applicationSnapshots.isEmpty()) {
                        Log.d(TAG, "No applications found for event: " + eventId);
                        showLoading(false);
                        textViewNoApplicants.setVisibility(View.VISIBLE);
                        recyclerViewApplicants.setVisibility(View.GONE);
                        applicantDetailsList.clear(); // Clear the main list
                        applicantAdapter.notifyDataSetChanged();
                        return;
                    }

                    // This will be effectively final as it's not reassigned.
                    final AtomicInteger pendingUserDetailsFetches = new AtomicInteger(applicationSnapshots.size());

                    for (QueryDocumentSnapshot appDoc : applicationSnapshots) {
                        String applicantUid = appDoc.getString("userId");
                        String applicationStatus = appDoc.getString("status");
                        String applicationId = appDoc.getId();
                        // Long applicationTimestamp = appDoc.getLong("timestamp");

                        if (applicantUid == null || applicantUid.isEmpty()) {
                            Log.w(TAG, "Application document " + applicationId + " missing or empty userId.");
                            // Decrement counter if we skip this application
                            // No need for explicit synchronized block here for AtomicInteger's decrementAndGet
                            if (pendingUserDetailsFetches.decrementAndGet() == 0) {
                                // All fetches are complete (or skipped) for this snapshot
                                updateAdapterWithProcessedList(currentSnapshotProcessingList);
                            }
                            continue;
                        }

                        db.collection("users").document(applicantUid).get()
                                .addOnSuccessListener(userSnapshot -> {
                                    if (userSnapshot.exists()) {
                                        User user = userSnapshot.toObject(User.class);
                                        if (user != null) {
                                            ApplicationWithUserDetails details = new ApplicationWithUserDetails();
                                            details.setUid(userSnapshot.getId());
                                            details.setName(user.getTitle());
                                            details.setEmail(user.getEmail());
                                            details.setProfileImageUrl(user.getProfileImageUrl());
                                            details.setApplicationId(applicationId);
                                            details.setEventId(eventId);
                                            details.setStatus(applicationStatus != null ? applicationStatus : "pending");
                                            // if (applicationTimestamp != null) details.setTimestamp(applicationTimestamp);
                                            currentSnapshotProcessingList.add(details); // Add to the temporary list
                                        } else {
                                            Log.w(TAG, "User object is null after conversion for UID: " + applicantUid);
                                        }
                                    } else {
                                        Log.w(TAG, "User document not found for UID: " + applicantUid + " (application ID: " + applicationId + ")");
                                    }

                                    if (pendingUserDetailsFetches.decrementAndGet() == 0) {
                                        updateAdapterWithProcessedList(currentSnapshotProcessingList);
                                    }
                                })
                                .addOnFailureListener(userError -> {
                                    Log.e(TAG, "Failed to fetch user details for UID: " + applicantUid, userError);
                                    if (pendingUserDetailsFetches.decrementAndGet() == 0) {
                                        updateAdapterWithProcessedList(currentSnapshotProcessingList);
                                    }
                                });
                    }
                    // If applicationSnapshots was not empty but all UIDs were invalid or all fetches failed,
                    // the counter would have already triggered updateAdapterWithProcessedList.
                });
    }

    // Helper method to update the main list and adapter
    private void updateAdapterWithProcessedList(List<ApplicationWithUserDetails> processedList) {
        synchronized (this) { // Synchronize updates to the main list and UI
            applicantDetailsList.clear();
            applicantDetailsList.addAll(processedList);
            applicantAdapter.notifyDataSetChanged();
            showLoading(false);

            if (applicantDetailsList.isEmpty()) {
                textViewNoApplicants.setVisibility(View.VISIBLE);
                recyclerViewApplicants.setVisibility(View.GONE);
                Log.d(TAG, "updateAdapterWithProcessedList: All fetches complete. No valid applicants found.");
            } else {
                textViewNoApplicants.setVisibility(View.GONE);
                recyclerViewApplicants.setVisibility(View.VISIBLE);
                Log.d(TAG, "updateAdapterWithProcessedList: All fetches complete. Displaying " + applicantDetailsList.size() + " applicants.");
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // Or finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading) {
            textViewStatus.setVisibility(View.GONE);
            recyclerViewApplicants.setVisibility(View.GONE);
            textViewNoApplicants.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        textViewStatus.setText(message);
        textViewStatus.setVisibility(View.VISIBLE);
        recyclerViewApplicants.setVisibility(View.GONE);
        textViewNoApplicants.setVisibility(View.GONE);
    }

    // --- ApplicantAdapter.OnApplicantActionListener Implementation ---

    @Override
    public void onApplicantClick(ApplicationWithUserDetails applicant, int position) {
        Log.d(TAG, "onApplicantClick: Clicked on applicant: " + (applicant.getTitle() != null ? applicant.getTitle() : "N/A") + " (AppID: " + applicant.getApplicationId() + ") at position: " + position);
        Toast.makeText(this, "Clicked on: " + (applicant.getTitle() != null ? applicant.getTitle() : "Applicant"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAcceptApplicant(ApplicationWithUserDetails applicant) {
        Log.d(TAG, "onAcceptApplicant: AppID: " + applicant.getApplicationId() + " (UID: " + applicant.getUid() + ") for event: " + eventId);
        updateApplicantStatus(applicant.getApplicationId(), "accepted");
    }

    @Override
    public void onRejectApplicant(ApplicationWithUserDetails applicant) {
        Log.d(TAG, "onRejectApplicant: AppID: " + applicant.getApplicationId() + " (UID: " + applicant.getUid() + ") for event: " + eventId);
        updateApplicantStatus(applicant.getApplicationId(), "rejected");
    }

    @Override
    public void onContactApplicant(ApplicationWithUserDetails applicant) {
        Log.d(TAG, "onContactApplicant: AppID: " + applicant.getApplicationId() + " (Email: " + applicant.getEmail() + ")");
        if (applicant.getEmail() == null || applicant.getEmail().isEmpty()) {
            Toast.makeText(this, "Applicant email not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + applicant.getEmail()));
        String currentEventName = (eventName != null && !eventName.isEmpty()) ? eventName : "the event";
        intent.putExtra(Intent.EXTRA_SUBJECT, "Regarding your application for " + currentEventName);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateApplicantStatus(String applicationId, String newStatus) {
        if (eventId == null || applicationId == null) {
            Log.e(TAG, "Cannot update status: eventId or applicationId is null.");
            Toast.makeText(this, "Error updating status (missing IDs).", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Updating status for AppID: " + applicationId + " to '" + newStatus + "' in eventapplications for event: " + eventId);

        db.collection("events").document(eventId)
                .collection("eventapplications").document(applicationId)
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Applicant (AppID: " + applicationId + ") status updated to " + newStatus + " successfully in Firestore.");
                    Toast.makeText(ViewApplicantsActivity.this, "Applicant " + newStatus, Toast.LENGTH_SHORT).show();
                    // The Firestore listener will automatically pick up this change and refresh the list.
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update status for AppID: " + applicationId + " to " + newStatus, e);
                    Toast.makeText(ViewApplicantsActivity.this, "Failed to update status: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

