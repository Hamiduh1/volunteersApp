package com.example.volunteersApp.fragments; // Corrected package

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.R;
// Assuming JobDetailsActivity is in the main app package
import com.example.volunteersApp.JobDetailsActivity;
import com.example.volunteersApp.adapters.JobPostAdapter;
import com.example.volunteersApp.models.JobPost;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException; // Added for error handling

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects; // For null checks with requireContext() if needed later

public class AvailableJobsFragment extends Fragment implements JobPostAdapter.OnJobPostActionListener {

    private static final String TAG = "AvailableJobsFragment";
    private static final String JOB_POSTINGS_COLLECTION = "jobPosts"; // Ensure this matches Firestore (was job_postings)
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_OPEN = "open";

    private RecyclerView recyclerViewJobs;
    private JobPostAdapter jobPostAdapter;
    private List<JobPost> masterJobPostList; // Holds all fetched items before filtering

    private FirebaseFirestore db;
    private CollectionReference jobPostingsCollectionRef;
    private DocumentSnapshot lastVisibleDocument = null; // For pagination

    private ProgressBar progressBar;
    private EditText searchEditText;
    private TextView textViewStatusMessage; // For messages like "No jobs found", "Loading..."

    private static final int PAGE_SIZE = 10;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private boolean initialLoadDone = false; // To track if the first load attempt has completed

    public AvailableJobsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Fragment created.");
        // Initialize non-view related members here
        masterJobPostList = new ArrayList<>();
        db = FirebaseFirestore.getInstance();
        jobPostingsCollectionRef = db.collection(JOB_POSTINGS_COLLECTION);

        // Assuming JobPostAdapter takes the listener and isEmployerView flag
        // For "Available Jobs", isEmployerView is likely false.
        jobPostAdapter = new JobPostAdapter(this, false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_available_jobs, container, false);
        Log.d(TAG, "onCreateView: Inflating layout.");

        // Initialize Views
        recyclerViewJobs = view.findViewById(R.id.recyclerViewAvailableJobs);
        progressBar = view.findViewById(R.id.progressBarAvailableJobs);
        searchEditText = view.findViewById(R.id.editTextSearchJobs);
        textViewStatusMessage = view.findViewById(R.id.textViewStatusMessageAvailableJobs);

        // Basic null checks for critical views
        if (recyclerViewJobs == null) Log.e(TAG, "onCreateView: RecyclerView NOT FOUND.");
        if (progressBar == null) Log.w(TAG, "onCreateView: ProgressBar not found.");
        if (searchEditText == null) Log.w(TAG, "onCreateView: Search EditText not found.");
        if (textViewStatusMessage == null) Log.w(TAG, "onCreateView: TextViewStatusMessage not found.");

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: View created, setting up UI.");

        setupRecyclerView();
        setupSearchListener();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "onViewCreated: User is NOT authenticated.");
            if (getContext() != null) { // Check getContext() before using it
                Toast.makeText(getContext(), R.string.please_login_to_view_jobs, Toast.LENGTH_LONG).show();
            }
            showStatusMessage(getString(R.string.login_required_to_see_jobs));
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (recyclerViewJobs != null) recyclerViewJobs.setVisibility(View.GONE);
            initialLoadDone = true; // Mark load as done (unsuccessfully due to auth)
            return;
        }

        // Only start loading if not already loading and initial load hasn't been attempted
        if (!isLoading && !initialLoadDone) {
            startInitialLoad();
        } else if (initialLoadDone) {
            // If returning to the fragment and data was already loaded/filtered, re-apply
            applyFilterAndSubmitToAdapter(searchEditText != null ? searchEditText.getText().toString() : "");
        }
    }

    private void setupRecyclerView() {
        if (recyclerViewJobs == null) return;

        recyclerViewJobs.setHasFixedSize(true);
        recyclerViewJobs.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewJobs.setAdapter(jobPostAdapter); // Adapter is initialized in onCreate
        recyclerViewJobs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && dy > 0) { // Scrolling down
                    int totalItemCountInAdapter = layoutManager.getItemCount();
                    int lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition();
                    boolean searchInactive = searchEditText == null || searchEditText.getText().toString().trim().isEmpty();

                    // Trigger load more only if search is inactive, not loading, not last page,
                    // and near the end of the currently displayed list.
                    // Also, ensure totalItemCountInAdapter reflects the full masterJobPostList size if search is inactive.
                    if (searchInactive && !isLoading && !isLastPage &&
                            totalItemCountInAdapter > 0 &&
                            lastVisibleItemPosition + 5 >= totalItemCountInAdapter && /* Threshold for loading more */
                            totalItemCountInAdapter >= masterJobPostList.size() /* Ensures adapter has all master items before trying for more */
                    ) {
                        Log.d(TAG, "Scroll listener: End of list approaching. Requesting more items.");
                        loadJobPostsPage();
                    }
                }
            }
        });
        Log.d(TAG, "setupRecyclerView: RecyclerView setup complete.");
    }

    private void setupSearchListener() {
        if (searchEditText == null) return;

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilterAndSubmitToAdapter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilterAndSubmitToAdapter(String queryText) {
        if (!isAdded() || masterJobPostList == null || jobPostAdapter == null) {
            Log.w(TAG, "applyFilter: Preconditions not met (fragment not added, list or adapter null).");
            if (isAdded()) { // Only try to update UI if fragment is still added
                showStatusMessage(getString(R.string.error_displaying_jobs));
            }
            return;
        }

        List<JobPost> filteredList = new ArrayList<>();
        String lowerCaseQuery = queryText.toLowerCase(Locale.getDefault()).trim();

        if (lowerCaseQuery.isEmpty()) {
            filteredList.addAll(masterJobPostList);
        } else {
            for (JobPost post : masterJobPostList) {
                // Check multiple fields for the search query
                boolean titleMatches = post.getJobTitle() != null && post.getJobTitle().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
                boolean orgMatches = post.getOrganizationName() != null && post.getOrganizationName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
                boolean catMatches = post.getCategory() != null && post.getCategory().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
                boolean locMatches = post.getLocationName() != null && post.getLocationName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
                // boolean descMatches = post.getDescription() != null && post.getDescription().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);


                if (titleMatches || orgMatches || catMatches || locMatches /*|| descMatches*/) {
                    filteredList.add(post);
                }
            }
        }
        Log.d(TAG, "applyFilter: Query '" + queryText + "', Master list size: " + masterJobPostList.size() + ", Filtered list size: " + filteredList.size());

        jobPostAdapter.submitList(new ArrayList<>(filteredList)); // Submit a new copy for DiffUtil

        updateUiBasedOnDataState(filteredList.isEmpty(), lowerCaseQuery.isEmpty());
    }

    private void startInitialLoad() {
        Log.i(TAG, "startInitialLoad: Initiating data load from Firestore.");
        if (isLoading) {
            Log.d(TAG, "startInitialLoad: Already loading.");
            return;
        }

        isLoading = true;
        isLastPage = false;
        lastVisibleDocument = null;
        initialLoadDone = false; // Mark that we are starting the initial load

        masterJobPostList.clear();
        jobPostAdapter.submitList(new ArrayList<>()); // Clear adapter

        showLoadingUI(true); // Show progress bar, hide list and status message
        loadJobPostsPage();
    }

    private void loadJobPostsPage() {
        if (!isAdded()) { // Check if fragment is still added
            isLoading = false;
            initialLoadDone = true; // Consider load attempt done if fragment is detached
            Log.w(TAG, "loadJobPostsPage: Fragment not added. Aborting load.");
            return;
        }

        if (isLastPage && lastVisibleDocument != null) { // Check lastVisibleDocument to ensure it's not the very first empty load
            Log.d(TAG, "loadJobPostsPage: Last page already reached and loaded.");
            isLoading = false;
            showLoadingUI(false); // Hide progress bar
            updateUiBasedOnDataState(jobPostAdapter.getCurrentList().isEmpty(),
                    searchEditText != null && searchEditText.getText().toString().trim().isEmpty());
            return;
        }

        // isLoading is set by the caller (startInitialLoad or scroll listener)
        // If this is not the initial load, the main progress bar might be hidden,
        // so we don't explicitly show it again here unless it's a pagination indicator (not implemented here).
        // The showLoadingUI(true) in startInitialLoad handles the main progress bar.

        Query firestoreQuery = jobPostingsCollectionRef
                .whereEqualTo(FIELD_STATUS, STATUS_OPEN)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastVisibleDocument != null) {
            firestoreQuery = firestoreQuery.startAfter(lastVisibleDocument);
            Log.d(TAG, "loadJobPostsPage: Paginated query starting after document ID: " + lastVisibleDocument.getId());
        } else {
            Log.d(TAG, "loadJobPostsPage: Initial query (first page).");
        }

        firestoreQuery.get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) { // Re-check if fragment is still added
                        isLoading = false;
                        initialLoadDone = true;
                        return;
                    }
                    Log.d(TAG, "onSuccess: Data received. Document count: " + querySnapshot.size());

                    List<JobPost> newPostsBatch = new ArrayList<>();
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        try {
                            JobPost jobPost = document.toObject(JobPost.class);
                            // If jobPostId is not set by @DocumentId, set it manually
                            if (jobPost.getJobPostId() == null || jobPost.getJobPostId().isEmpty()) {
                                jobPost.setJobPostId(document.getId());
                            }
                            newPostsBatch.add(jobPost);
                        } catch (Exception e) {
                            Log.e(TAG, "Error converting document to JobPost: " + document.getId(), e);
                        }
                    }

                    masterJobPostList.addAll(newPostsBatch); // Add new items to the master list

                    if (!newPostsBatch.isEmpty()) {
                        lastVisibleDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    }

                    if (newPostsBatch.size() < PAGE_SIZE) {
                        isLastPage = true;
                        Log.d(TAG, "Last page reached (loaded " + newPostsBatch.size() + "/" + PAGE_SIZE + ").");
                    }

                    // Always re-apply current search filter and submit to adapter
                    applyFilterAndSubmitToAdapter(searchEditText != null ? searchEditText.getText().toString() : "");
                    // updateUiBasedOnDataState is called within applyFilterAndSubmitToAdapter

                    isLoading = false;
                    initialLoadDone = true; // Mark initial load attempt as complete
                    if (lastVisibleDocument == null && masterJobPostList.isEmpty()) { // First load, no items
                        // updateUiBasedOnDataState will handle showing "No open opportunities"
                    }
                    showLoadingUI(false); // Hide progress bar
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        isLoading = false;
                        initialLoadDone = true;
                        return;
                    }
                    Log.e(TAG, "Firebase query error: ", e);
                    isLoading = false;
                    initialLoadDone = true; // Mark initial load attempt as complete (failed)
                    showLoadingUI(false); // Hide progress bar

                    String errorMessage = getString(R.string.failed_to_load_opportunities_fragment);
                    if (e instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException firestoreEx = (FirebaseFirestoreException) e;
                        if (firestoreEx.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            errorMessage = getString(R.string.permission_denied_loading_jobs);
                            // Optionally, guide user or log specific action for permission issues
                        } else {
                            errorMessage += ": " + e.getLocalizedMessage();
                        }
                    } else {
                        errorMessage += ": " + e.getLocalizedMessage();
                    }

                    if (getContext() != null) {
                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                    // Update UI to show error or empty state based on current master list
                    // If master list is empty after failure, it means no data could be fetched at all.
                    applyFilterAndSubmitToAdapter(searchEditText != null ? searchEditText.getText().toString() : "");
                });
    }

    private void showLoadingUI(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) { // When loading, hide list and status message
            if (recyclerViewJobs != null) recyclerViewJobs.setVisibility(View.GONE);
            if (textViewStatusMessage != null) textViewStatusMessage.setVisibility(View.GONE);
        }
        // If not showing loading, the visibility of recyclerView and textViewStatusMessage
        // will be handled by updateUiBasedOnDataState
    }

    private void showStatusMessage(String message) {
        if (textViewStatusMessage != null) {
            textViewStatusMessage.setText(message);
            textViewStatusMessage.setVisibility(View.VISIBLE);
        }
        if (recyclerViewJobs != null) {
            recyclerViewJobs.setVisibility(View.GONE); // Hide list when status message is shown
        }
    }


    private void updateUiBasedOnDataState(boolean isDisplayListEmpty, boolean isSearchQueryEmpty) {
        if (!isAdded() || textViewStatusMessage == null || recyclerViewJobs == null) {
            return;
        }

        // If currently loading (especially initial load), progressBar is visible via showLoadingUI,
        // and list/status message are hidden. So, don't interfere if loading.
        if (isLoading && !initialLoadDone) { // Specifically for initial load phase
            Log.d(TAG, "updateUiBasedOnDataState: Initial load in progress, deferring UI update.");
            return;
        }

        if (isDisplayListEmpty) {
            recyclerViewJobs.setVisibility(View.GONE);
            textViewStatusMessage.setVisibility(View.VISIBLE);
            if (!isSearchQueryEmpty) {
                textViewStatusMessage.setText(R.string.no_results_for_search_fragment);
            } else if (masterJobPostList.isEmpty() && initialLoadDone) { // No items from source and initial load attempt is done
                // This covers cases:
                // 1. Successful initial load returned no items.
                // 2. Failed initial load (e.g., permission denied, network error) left master list empty.
                textViewStatusMessage.setText(R.string.no_open_opportunities_found_fragment);
            } else if (masterJobPostList.isEmpty() && !initialLoadDone && !isLoading) {
                // Should not happen if initialLoadDone is managed correctly.
                // This would be before the first load even starts or finishes.
                textViewStatusMessage.setText(R.string.loading_jobs); // Or a generic empty state
            }
            else {
                // If display is empty, search is empty, but master list is NOT empty (should not happen with current filter)
                // OR if it's not the last page but current display is empty (also unlikely with pagination logic)
                // Fallback to a generic message if other conditions don't quite fit.
                textViewStatusMessage.setText(R.string.no_items_match_criteria);
            }
        } else { // Display list is NOT empty
            recyclerViewJobs.setVisibility(View.VISIBLE);
            textViewStatusMessage.setVisibility(View.GONE);
        }
        Log.d(TAG, "updateUiBasedOnDataState: DisplayListEmpty=" + isDisplayListEmpty +
                ", SearchQueryEmpty=" + isSearchQueryEmpty +
                ", MasterListEmpty=" + masterJobPostList.isEmpty() +
                ", IsLoading=" + isLoading + ", IsLastPage=" + isLastPage +
                ", InitialLoadDone=" + initialLoadDone +
                ", TextViewMsg: " + (textViewStatusMessage.getVisibility() == View.VISIBLE ? textViewStatusMessage.getText() : "HIDDEN"));
    }

    @Override
    public void onViewDetailsClicked(JobPost jobPost, int position) {
        if (getContext() == null || !isAdded()) { // Simpler check
            Log.w(TAG, "onViewDetailsClicked: Context null or fragment not added.");
            return;
        }
        if (jobPost == null || jobPost.getJobPostId() == null || jobPost.getJobPostId().isEmpty()) {
            Toast.makeText(getContext(), R.string.error_job_details_unavailable, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "onViewDetailsClicked: JobPost or JobPost ID is null or empty. Title: " + (jobPost != null ? jobPost.getJobTitle() : "N/A"));
            return;
        }

        Log.d(TAG, "onViewDetailsClicked for: '" + jobPost.getJobTitle() + "' ID: " + jobPost.getJobPostId());
        Intent intent = new Intent(getActivity(), JobDetailsActivity.class);
        intent.putExtra(JobDetailsActivity.EXTRA_JOB_POST_ID, jobPost.getJobPostId());
        startActivity(intent);
    }

    @Override
    public void onJobPostDelete(JobPost jobPostToDelete, int positionInAdapter) {
        // This fragment is for viewing "available" jobs, typically user doesn't delete from this view.
        // If a job post were deleted (e.g., by an employer elsewhere), a full refresh or
        // more complex real-time update mechanism would be needed.
        // For simplicity, we can log it. If isEmployerView was true, we'd handle it.
        Log.d(TAG, "onJobPostDelete callback received for job: " +
                (jobPostToDelete != null ? jobPostToDelete.getJobTitle() : "Unknown job") +
                ". No direct action taken in AvailableJobsFragment as it's a read-only view for users.");

        // If this fragment *did* need to reflect deletions initiated from the adapter itself
        // (e.g., if it was an employer's view with delete buttons in the adapter):
        // 1. Remove from masterJobPostList
        //    masterJobPostList.removeIf(post -> post.getJobPostId().equals(jobPostToDelete.getJobPostId()));
        // 2. Re-apply filter and submit to adapter
        //    applyFilterAndSubmitToAdapter(searchEditText != null ? searchEditText.getText().toString() : "");
        //    Toast.makeText(getContext(), "Job post deleted.", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Cleaning up views.");
        // Nullify views to help GC and prevent potential leaks
        recyclerViewJobs = null;
        searchEditText = null;
        progressBar = null;
        textViewStatusMessage = null;
        // jobPostAdapter and masterJobPostList are handled in onDestroy
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Fragment destroyed. Clearing resources.");
        // Clear lists and other non-view resources
        if (masterJobPostList != null) {
            masterJobPostList.clear();
            masterJobPostList = null;
        }
        jobPostAdapter = null; // Adapter can be nulled here
        db = null;
        jobPostingsCollectionRef = null;
        lastVisibleDocument = null;
    }

    // --- Ensure you have these string resources in res/values/strings.xml ---
    // <string name="failed_to_load_opportunities_fragment">Failed to load opportunities</string>
    // <string name="no_open_opportunities_found_fragment">No open volunteer opportunities found.</string>
    // <string name="no_results_for_search_fragment">No opportunities match your search.</string>
    // <string name="no_items_match_criteria">No items match the current criteria.</string>
    // <string name="permission_denied_loading_jobs">You don\'t have permission to view jobs. Please check your account or contact support.</string>
    // <string name="please_login_to_view_jobs">Please log in to view opportunities.</string>
    // <string name="login_required_to_see_jobs">Login is required to see available jobs.</string>
    // <string name="error_displaying_jobs">Error displaying jobs.</string>
    // <string name="loading_jobs">Loading jobs...</string>
    // <string name="error_job_details_unavailable">Error: Job details are unavailable.</string>
}
