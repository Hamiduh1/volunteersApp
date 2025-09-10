package com.example.volunteersApp.employer.ui.postedjobs; // Adjust package as needed

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity; // For setting toolbar title
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentEmployerPostedJobsBinding; // Use Fragment binding
import com.example.volunteersApp.employer.JobPosting; // Your model
import com.example.volunteersApp.employer.MyPostedJobsAdapter; // Your adapter
// For EmployerPostJobFragment argument key
import com.example.volunteersApp.employer.ui.postjob.EmployerPostJobFragment;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmployerPostedJobsFragment extends Fragment implements MyPostedJobsAdapter.OnJobActionListener {

    private static final String TAG = "EmployerPostedJobsFrag";
    private static final String JOB_POSTINGS_COLLECTION = "job_postings";
    // Using Activity.RESULT_OK for now, but fragment results are handled differently with Navigation Component
    // For simplicity, we'll rely on the snapshot listener to refresh data after edit.

    private FragmentEmployerPostedJobsBinding binding; // Changed from Activity binding
    private MyPostedJobsAdapter adapter;
    private List<JobPosting> jobPostingList;
    // Views will be accessed via binding

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private String currentUserId;

    private FirebaseFirestore db;
    private CollectionReference jobPostingsCollectionRef;
    private ListenerRegistration jobsListenerRegistration;
    private Query myJobsQuery;
    private NavController navController;

    public EmployerPostedJobsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // Auth check should ideally be handled by EmployerMainActivity before navigating here.
            // If somehow reached, pop back or navigate to login.
            Log.w(TAG, "User is null in onCreate. This shouldn't happen if EmployerMainActivity guards navigation.");
            // Consider navigating to a login graph / start destination if applicable
            return;
        }
        currentUserId = currentUser.getUid();

        db = FirebaseFirestore.getInstance();
        jobPostingsCollectionRef = db.collection(JOB_POSTINGS_COLLECTION);
        jobPostingList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEmployerPostedJobsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        if (getActivity() instanceof AppCompatActivity && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.title_my_posted_opportunities);
        }

        adapter = new MyPostedJobsAdapter(requireContext(), this);
        binding.recyclerViewMyPostedJobs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewMyPostedJobs.setAdapter(adapter);

        // Snapshot listener will be attached in onStart
    }

    private void loadMyPostedJobsFromFirestore() {
        if (binding == null || !isAdded()) return; // Fragment not attached or view destroyed

        binding.progressBarMyJobs.setVisibility(View.VISIBLE);
        binding.textViewNoJobs.setVisibility(View.GONE);
        binding.recyclerViewMyPostedJobs.setVisibility(View.GONE);

        if (currentUserId == null || currentUserId.isEmpty()) {
            binding.progressBarMyJobs.setVisibility(View.GONE);
            Log.e(TAG, "CurrentUser ID is null or empty.");
            binding.textViewNoJobs.setText(R.string.error_loading_jobs);
            binding.textViewNoJobs.setVisibility(View.VISIBLE);
            return;
        }

        myJobsQuery = jobPostingsCollectionRef
                .whereEqualTo("employerUid", currentUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING); // Ensure "timestamp" field exists and is a Timestamp

        detachFirestoreListener();

        jobsListenerRegistration = myJobsQuery.addSnapshotListener((snapshots, e) -> {
            if (!isAdded() || binding == null) { // Check if fragment is still valid
                Log.w(TAG, "Fragment not added or binding is null in snapshot listener. Detaching.");
                detachFirestoreListener(); // Detach if fragment is gone
                return;
            }
            binding.progressBarMyJobs.setVisibility(View.GONE);

            if (e != null) {
                Log.e(TAG, "Firestore listen failed:", e);
                Toast.makeText(getContext(),
                        getString(R.string.failed_to_load_jobs_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                binding.textViewNoJobs.setText(R.string.failed_to_load_opportunities);
                binding.textViewNoJobs.setVisibility(View.VISIBLE);
                binding.recyclerViewMyPostedJobs.setVisibility(View.GONE);
                return;
            }

            if (snapshots == null) {
                Log.w(TAG, "Received null QuerySnapshot.");
                checkIfListIsEmpty();
                return;
            }

            Log.d(TAG, "New data. Changes: " + snapshots.getDocumentChanges().size() + ", Total: " + snapshots.size());

            boolean listNeedsUpdate = false;
            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                JobPosting posting = dc.getDocument().toObject(JobPosting.class);
                posting.setPostingId(dc.getDocument().getId()); // Set document ID

                int index;
                switch (dc.getType()) {
                    case ADDED:
                        if (!jobPostingListContainsId(posting.getPostingId())) {
                            jobPostingList.add(posting);
                            listNeedsUpdate = true;
                        }
                        break;
                    case MODIFIED:
                        index = findJobPostingIndexById(posting.getPostingId());
                        if (index != -1) {
                            jobPostingList.set(index, posting);
                        } else { // Should not happen if ADDED was processed
                            jobPostingList.add(posting);
                        }
                        listNeedsUpdate = true;
                        break;
                    case REMOVED:
                        index = findJobPostingIndexById(posting.getPostingId());
                        if (index != -1) {
                            jobPostingList.remove(index);
                            listNeedsUpdate = true;
                        }
                        break;
                }
            }

            if (listNeedsUpdate) {
                // Sort by timestamp descending (newest first)
                Collections.sort(jobPostingList, (p1, p2) -> {
                    Long t1 = p1.getTimestampLong();
                    Long t2 = p2.getTimestampLong();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return Long.compare(t2, t1);
                });
                // MyPostedJobsAdapter should be a ListAdapter for efficient updates
                // If it's not, you'd call adapter.notifyDataSetChanged() or use DiffUtil manually.
                // Assuming MyPostedJobsAdapter is a ListAdapter or handles its own updates
              //  if (adapter instanceof androidx.recyclerview.widget.ListAdapter) {
                 //   ((androidx.recyclerview.widget.ListAdapter) adapter).submitList(new ArrayList<>(jobPostingList));
               // } else {
               //     adapter.setJobPostings(jobPostingList); // Or equivalent method in your custom adapter
               // }


                // MyPostedJobsAdapter IS a ListAdapter, so directly call submitList
                // Always pass a new list instance to submitList for DiffUtil to work correctly.
                adapter.submitList(new ArrayList<>(jobPostingList));

                // Hide/show empty state view based on the new list
                if (jobPostingList.isEmpty()) {
                    binding.textViewNoJobs.setVisibility(View.VISIBLE);
                } else {
                    binding.textViewNoJobs.setVisibility(View.GONE);
                }



            }

            checkIfListIsEmpty();
            if (!jobPostingList.isEmpty()) {
                binding.recyclerViewMyPostedJobs.setVisibility(View.VISIBLE);
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
        if (!isAdded() || binding == null) return;
        if (jobPostingList.isEmpty()) {
            binding.textViewNoJobs.setText(R.string.no_posted_opportunities);
            binding.textViewNoJobs.setVisibility(View.VISIBLE);
            binding.recyclerViewMyPostedJobs.setVisibility(View.GONE);
        } else {
            binding.textViewNoJobs.setVisibility(View.GONE);
            binding.recyclerViewMyPostedJobs.setVisibility(View.VISIBLE);
        }
    }

    // --- MyPostedJobsAdapter.OnJobActionListener Implementation ---
    @Override
    public void onEditJob(JobPosting jobPosting) {
        if (jobPosting.getPostingId() == null) {
            Toast.makeText(getContext(), "Error: Cannot edit job without ID.", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString(EmployerPostJobFragment.ARG_EDIT_POSTING_ID, jobPosting.getPostingId());
        // Navigate to EmployerPostJobFragment for editing
        navController.navigate(R.id.nav_employer_post_job, args); // Ensure this ID exists in employer_navigation.xml
    }

    @Override
    public void onDeleteJob(JobPosting jobPosting, int position) {
        new AlertDialog.Builder(requireContext()) // Use requireContext()
                .setTitle(R.string.delete_opportunity_title)
                .setMessage(getString(R.string.delete_opportunity_confirmation, jobPosting.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) -> performDeleteFirestore(jobPosting))
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void performDeleteFirestore(JobPosting jobPosting) {
        if (jobPosting.getPostingId() == null || jobPosting.getPostingId().isEmpty()) {
            Toast.makeText(getContext(), R.string.error_cannot_delete_item, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot delete job, postingId is null or empty.");
            return;
        }
        if (!isAdded() || binding == null) return;
        binding.progressBarMyJobs.setVisibility(View.VISIBLE);

        String postingId = jobPosting.getPostingId();
        jobPostingsCollectionRef.document(postingId).delete()
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(getContext(), R.string.opportunity_deleted_successfully, Toast.LENGTH_SHORT).show();
                    // Listener will update UI
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(getContext(),
                            getString(R.string.failed_to_delete_opportunity, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onToggleStatusJob(JobPosting jobPosting, int position) {
        if (jobPosting.getPostingId() == null || jobPosting.getPostingId().isEmpty()) {
            Toast.makeText(getContext(), R.string.error_updating_status, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAdded() || binding == null) return;
        binding.progressBarMyJobs.setVisibility(View.VISIBLE);

        String currentStatus = jobPosting.getStatus();
        String newStatus = "open".equalsIgnoreCase(currentStatus) ? "closed" : "open";

        jobPostingsCollectionRef.document(jobPosting.getPostingId())
                .update("status", newStatus, "timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp()) // Also update timestamp
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(getContext(),
                            getString(R.string.status_updated_to, newStatus), Toast.LENGTH_SHORT).show();
                    // Listener will update UI
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBarMyJobs.setVisibility(View.GONE);
                    Toast.makeText(getContext(),
                            getString(R.string.failed_to_update_status, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onViewApplicants(JobPosting jobPosting) {
        // Navigate to a fragment that shows applications for this jobPosting.getPostingId()
        Bundle args = new Bundle();
        args.putString("event_id", jobPosting.getPostingId()); // Assuming applications fragment uses "event_id"
        // args.putString("event_title", jobPosting.getTitle()); // Optionally pass title
        // Ensure you have a destination in employer_navigation.xml for viewing applications for a specific event
        // For example: R.id.action_employerPostedJobsFragment_to_eventApplicationsFragment
        // navController.navigate(R.id.your_destination_for_event_applications, args);
        Toast.makeText(getContext(), getString(R.string.view_applicants_for, jobPosting.getTitle()), Toast.LENGTH_SHORT).show();
        // TODO: Create and navigate to a fragment to view applicants for this specific job.
        // This destination should probably be R.id.nav_employer_applications if that fragment can take an eventId.
        if (jobPosting.getPostingId() != null) {
            navController.navigate(R.id.nav_employer_applications, args);
        } else {
            Toast.makeText(getContext(), "Error: Job ID is missing.", Toast.LENGTH_SHORT).show();
        }
    }
    // --- End MyPostedJobsAdapter.OnJobActionListener Implementation ---

    @Override
    public void onStart() {
        super.onStart();
        if (currentUser != null) {
            Log.d(TAG, "onStart: Attaching Firestore listener.");
            loadMyPostedJobsFromFirestore();
        } else {
            Log.w(TAG, "onStart: User is null, cannot attach listener.");
            // EmployerMainActivity should handle redirecting to login if user becomes null
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Detaching Firestore listener.");
        detachFirestoreListener();
    }

    private void detachFirestoreListener() {
        if (jobsListenerRegistration != null) {
            jobsListenerRegistration.remove();
            jobsListenerRegistration = null;
            Log.d(TAG, "Firestore SnapshotListener detached.");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachFirestoreListener(); // Ensure listener is detached
        binding = null; // Important for Fragments
        Log.d(TAG, "onDestroyView called.");
    }
}

