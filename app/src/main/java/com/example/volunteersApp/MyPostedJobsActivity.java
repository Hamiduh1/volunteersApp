/**

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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.employer.JobPosting;
import com.example.volunteersApp.employer.MyPostedJobsAdapter;
import com.example.volunteersApp.employer.PostJobActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Firestore Imports
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query; // Firestore Query
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;


import java.util.ArrayList;
import java.util.Collections;
// HashMap and Map are still used for updates, so keep them.
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyPostedJobsActivity extends AppCompatActivity implements MyPostedJobsAdapter.OnJobActionListener {

    private static final String TAG = "MyPostedJobsActivity";
    private static final String JOB_POSTINGS_COLLECTION = "job_postings"; // Firestore collection name
    private static final int EDIT_JOB_REQUEST_CODE = 101;

    private RecyclerView recyclerViewMyPostedJobs;
    private MyPostedJobsAdapter adapter;
    private List<JobPosting> jobPostingList;
    private ProgressBar progressBarMyJobs;
    private TextView textViewNoJobs;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private String currentUserId;


    // Firestore components
    private FirebaseFirestore db;
    private CollectionReference jobPostingsCollectionRef;
    private ListenerRegistration jobsListenerRegistration;
    private Query myJobsQuery; // Firestore Query

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_posted_jobs);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_my_posted_opportunities);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.login_to_view_postings, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        jobPostingsCollectionRef = db.collection(JOB_POSTINGS_COLLECTION);

        recyclerViewMyPostedJobs = findViewById(R.id.recyclerViewMyPostedJobs);
        progressBarMyJobs = findViewById(R.id.progressBarMyJobs);
        textViewNoJobs = findViewById(R.id.textViewNoJobs);

        jobPostingList = new ArrayList<>();
        // Ensure MyPostedJobsAdapter's constructor is (Context, List<JobPosting>, OnJobActionListener)
        adapter = new MyPostedJobsAdapter(this, jobPostingList, this);
        recyclerViewMyPostedJobs.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMyPostedJobs.setAdapter(adapter);

        // loadMyPostedJobsFromFirestore will be called in onStart
    }

    private void loadMyPostedJobsFromFirestore() {
        progressBarMyJobs.setVisibility(View.VISIBLE);
        textViewNoJobs.setVisibility(View.GONE);
        recyclerViewMyPostedJobs.setVisibility(View.GONE);

        if (currentUserId == null || currentUserId.isEmpty()) {
            progressBarMyJobs.setVisibility(View.GONE);
            Log.e(TAG, "CurrentUser ID is null or empty in loadMyPostedJobsFromFirestore");
            textViewNoJobs.setText(R.string.error_loading_jobs); // Generic error
            textViewNoJobs.setVisibility(View.VISIBLE);
            return;
        }

        // Query to get job postings for the current user, ordered by timestamp
        // Replace "timestamp" with your actual timestamp field name in Firestore.
        // Assuming your JobPosting.getTimestampLong() can handle nulls if the timestamp isn't set.
        myJobsQuery = jobPostingsCollectionRef
                .whereEqualTo("employerUid", currentUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING); // Example: newest first

        detachFirestoreListener(); // Remove previous listener if any

        jobsListenerRegistration = myJobsQuery.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException e) {
                progressBarMyJobs.setVisibility(View.GONE);

                if (e != null) {
                    Log.e(TAG, "Firestore listen failed:", e);
                    Toast.makeText(MyPostedJobsActivity.this,
                            getString(R.string.failed_to_load_jobs_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                    textViewNoJobs.setText(R.string.failed_to_load_opportunities);
                    textViewNoJobs.setVisibility(View.VISIBLE);
                    recyclerViewMyPostedJobs.setVisibility(View.GONE);
                    return;
                }

                if (snapshots == null) {
                    Log.w(TAG, "Received null QuerySnapshot.");
                    checkIfListIsEmpty();
                    return;
                }

                Log.d(TAG, "New data received. Document changes: " + snapshots.getDocumentChanges().size() + ", Total docs: " + snapshots.size());


                boolean listChanged = false;
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    JobPosting posting = dc.getDocument().toObject(JobPosting.class);
                    // Crucial: Set the document ID from Firestore into your POJO
                    posting.setPostingId(dc.getDocument().getId());

                    int index;
                    switch (dc.getType()) {
                        case ADDED:
                            if (!jobPostingListContainsId(posting.getPostingId())) {
                                jobPostingList.add(posting); // Will be sorted later
                                listChanged = true;
                                Log.d(TAG, "Job ADDED: " + posting.getPostingId() + " Title: " + posting.getJobTitle());
                            }
                            break;
                        case MODIFIED:
                            index = findJobPostingIndexById(posting.getPostingId());
                            if (index != -1) {
                                jobPostingList.set(index, posting);
                                listChanged = true;
                                Log.d(TAG, "Job MODIFIED: " + posting.getPostingId() + " Title: " + posting.getJobTitle());
                            } else { // Should not happen if ADDED was processed first
                                jobPostingList.add(posting);
                                listChanged = true;
                                Log.w(TAG, "Job MODIFIED (but not found, adding): " + posting.getPostingId());
                            }
                            break;
                        case REMOVED:
                            index = findJobPostingIndexById(posting.getPostingId());
                            if (index != -1) {
                                jobPostingList.remove(index);
                                listChanged = true;
                                Log.d(TAG, "Job REMOVED: " + posting.getPostingId() + " Title: " + posting.getJobTitle());
                            }
                            break;
                    }
                }

                if (listChanged) {
                    // Sort by timestamp descending (newest first)
                    // Ensure JobPosting.getTimestampLong() returns Long and handles nulls
                    Collections.sort(jobPostingList, (p1, p2) -> {
                        Long t1 = p1.getTimestampLong(); // This method must exist in JobPosting and return Long
                        Long t2 = p2.getTimestampLong(); // This method must exist in JobPosting and return Long
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1; // nulls last
                        if (t2 == null) return -1; // nulls last
                        return Long.compare(t2, t1);
                    });
                    adapter.setJobPostings(jobPostingList); // Assuming adapter has setJobPostings or similar
                    // Or use adapter.notifyDataSetChanged() if setJobPostings internally does that.
                }

                checkIfListIsEmpty();
                if (!jobPostingList.isEmpty()) {
                    recyclerViewMyPostedJobs.setVisibility(View.VISIBLE);
                }
            }
        });
        Log.d(TAG, "Attached Firestore SnapshotListener for MyPostedJobs.");
    }

    private boolean jobPostingListContainsId(String id) {
        if (id == null) return false;
        for (JobPosting job : jobPostingList) {
            if (id.equals(job.getPostingId())) {
                return true;
            }
        }
        return false;
    }

    private int findJobPostingIndexById(String id) {
        if (id == null) return -1;
        for (int i = 0; i < jobPostingList.size(); i++) {
            if (id.equals(jobPostingList.get(i).getPostingId())) {
                return i;
            }
        }
        return -1;
    }


    private void checkIfListIsEmpty() {
        if (jobPostingList.isEmpty()) {
            textViewNoJobs.setText(R.string.no_posted_opportunities);
            textViewNoJobs.setVisibility(View.VISIBLE);
            recyclerViewMyPostedJobs.setVisibility(View.GONE);
        } else {
            textViewNoJobs.setVisibility(View.GONE);
            recyclerViewMyPostedJobs.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onEditJob(JobPosting jobPosting) {
        Intent intent = new Intent(this, PostJobActivity.class);
        intent.putExtra("EDIT_POSTING_ID", jobPosting.getPostingId()); // Pass Firestore document ID
        // Pass other data as before
        intent.putExtra("organizationName", jobPosting.getOrganizationName());
        intent.putExtra("eventName", jobPosting.getTitle());
        intent.putExtra("jobTitle", jobPosting.getJobTitle());
        intent.putExtra("description", jobPosting.getDescription());
        intent.putExtra("date", jobPosting.getDate());
        intent.putExtra("time", jobPosting.getTime());
        intent.putExtra("location", jobPosting.getLocationName());
        intent.putExtra("category", jobPosting.getCategory());
        intent.putExtra("volunteersNeeded", jobPosting.getVolunteersNeeded());
        intent.putExtra("status", jobPosting.getStatus());
        startActivityForResult(intent, EDIT_JOB_REQUEST_CODE);
    }

    @Override
    public void onDeleteJob(JobPosting jobPosting, int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_opportunity_title)
                .setMessage(getString(R.string.delete_opportunity_confirmation, jobPosting.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) -> performDeleteFirestore(jobPosting))
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void performDeleteFirestore(JobPosting jobPosting) {
        if (jobPosting.getPostingId() == null || jobPosting.getPostingId().isEmpty()) {
            Toast.makeText(this, R.string.error_cannot_delete_item, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot delete job, postingId is null or empty.");
            return;
        }
        progressBarMyJobs.setVisibility(View.VISIBLE);

        String postingId = jobPosting.getPostingId();

        // For robust deletion of associated data (e.g., applications for this job),
        // a Cloud Function triggered by the deletion of a job posting is the recommended approach.
        // functions.firestore.document('/job_postings/{postId}').onDelete((snap, context) => { ... });

        jobPostingsCollectionRef.document(postingId).delete()
                .addOnSuccessListener(aVoid -> {
                    progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(MyPostedJobsActivity.this, R.string.opportunity_deleted_successfully, Toast.LENGTH_SHORT).show();
                    // The Firestore listener (onEvent) will automatically update the UI by processing the REMOVED DocumentChange.
                    Log.d(TAG, "Job deleted successfully: " + postingId);
                })
                .addOnFailureListener(e -> {
                    progressBarMyJobs.setVisibility(View.GONE);
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    Toast.makeText(MyPostedJobsActivity.this,
                            getString(R.string.failed_to_delete_opportunity, errorMessage), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to delete job: " + postingId, e);
                });
    }

    @Override
    public void onToggleStatusJob(JobPosting jobPosting, int position) {
        if (jobPosting.getPostingId() == null || jobPosting.getPostingId().isEmpty()) {
            Toast.makeText(this, R.string.error_updating_status, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot toggle status, postingId is null or empty.");
            return;
        }
        progressBarMyJobs.setVisibility(View.VISIBLE);

        String currentStatus = jobPosting.getStatus();
        String newStatus = "open".equalsIgnoreCase(currentStatus) ? "closed" : "open";

        jobPostingsCollectionRef.document(jobPosting.getPostingId())
                .update("status", newStatus) // Update only the 'status' field
                .addOnSuccessListener(aVoid -> {
                    progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(MyPostedJobsActivity.this,
                            getString(R.string.status_updated_to, newStatus), Toast.LENGTH_SHORT).show();
                    // The Firestore listener (onEvent) will update the UI by processing the MODIFIED DocumentChange.
                    Log.d(TAG, "Job status updated successfully for: " + jobPosting.getPostingId() + " to " + newStatus);
                })
                .addOnFailureListener(e -> {
                    progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(MyPostedJobsActivity.this,
                            getString(R.string.failed_to_update_status, e.getMessage()), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update status for: " + jobPosting.getPostingId(), e);
                });
    }

    @Override
    public void onViewApplicants(JobPosting jobPosting) {
        // Intent intent = new Intent(this, ViewApplicationsActivity.class); // Ensure ViewApplicationsActivity exists
        // intent.putExtra("POSTING_ID", jobPosting.getPostingId());
        // intent.putExtra("POSTING_NAME", jobPosting.getTitle());
        // startActivity(intent);
        Toast.makeText(this, getString(R.string.view_applicants_for, jobPosting.getTitle()), Toast.LENGTH_SHORT).show();
        // TODO: Implement ViewApplicationsActivity and ensure it uses Firestore if it fetches application data.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_JOB_REQUEST_CODE && resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.job_details_updated, Toast.LENGTH_SHORT).show();
            // The Firestore listener should automatically refresh the list if data changed in PostJobActivity
            // and that activity wrote back to the same Firestore document.
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
        // Attach listener when activity becomes visible
        if (currentUser != null) { // Ensure user is still valid
            loadMyPostedJobsFromFirestore();
        } else {
            // Handle case where user might have signed out in background or auth state changed
            Toast.makeText(this, R.string.login_to_view_postings, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detach listener when activity is no longer visible
        detachFirestoreListener();
    }

    private void detachFirestoreListener() {
        if (jobsListenerRegistration != null) {
            jobsListenerRegistration.remove();
            jobsListenerRegistration = null;
            Log.d(TAG, "Detached Firestore SnapshotListener for MyPostedJobs.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Final cleanup, though onStop should handle most listener detachment.
        detachFirestoreListener();
    }
}
 **/