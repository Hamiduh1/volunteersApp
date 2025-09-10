package com.example.volunteersApp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.adapters.JobPostSimpleAdapter; // Correct import
import com.example.volunteersApp.models.JobPost;
import com.example.volunteersApp.models.Organization;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

// Implement the click listener interface from the adapter
public class OrganizationProfileActivity extends AppCompatActivity implements JobPostSimpleAdapter.OnJobPostClickListener {

    private static final String TAG = "OrgProfileActivity";
    public static final String EXTRA_EMPLOYER_UID = "EMPLOYER_UID";

    private ImageView imageViewOrganizationHeader;
    private TextView textViewOrgName, textViewOrgType, textViewOrgDescription,
            textViewOrgContactEmail, textViewOrgContactPhone, textViewOrgWebsite,
            textViewOrgJobPostsLabel;
    private RecyclerView recyclerViewOrgJobPosts;
    private ProgressBar progressBarOrganizationProfile;
    private CollapsingToolbarLayout collapsingToolbarOrganization;
    private Toolbar toolbarOrganizationProfile;

    private FirebaseFirestore db;
    private String employerUid;
    private JobPostSimpleAdapter jobPostSimpleAdapter;
    // jobPostList is not directly used by the ListAdapter after submission,
    // but can be useful for preparing the list to be submitted.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_profile);

        db = FirebaseFirestore.getInstance();

        toolbarOrganizationProfile = findViewById(R.id.toolbarOrganizationProfile);
        setSupportActionBar(toolbarOrganizationProfile);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        collapsingToolbarOrganization = findViewById(R.id.collapsingToolbarOrganization);
        imageViewOrganizationHeader = findViewById(R.id.imageViewOrganizationHeader);
        textViewOrgName = findViewById(R.id.textViewOrgName);
        textViewOrgType = findViewById(R.id.textViewOrgType);
        textViewOrgDescription = findViewById(R.id.textViewOrgDescription);
        textViewOrgContactEmail = findViewById(R.id.textViewOrgContactEmail);
        textViewOrgContactPhone = findViewById(R.id.textViewOrgContactPhone);
        textViewOrgWebsite = findViewById(R.id.textViewOrgWebsite);
        textViewOrgJobPostsLabel = findViewById(R.id.textViewOrgJobPostsLabel);
        recyclerViewOrgJobPosts = findViewById(R.id.recyclerViewOrgJobPosts);
        progressBarOrganizationProfile = findViewById(R.id.progressBarOrganizationProfile);

        recyclerViewOrgJobPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrgJobPosts.setNestedScrollingEnabled(false);

        // Initialize and set the new adapter.
        // Pass 'this' as the listener because this Activity implements OnJobPostClickListener.
        jobPostSimpleAdapter = new JobPostSimpleAdapter(this);
        recyclerViewOrgJobPosts.setAdapter(jobPostSimpleAdapter);

        if (getIntent().hasExtra(EXTRA_EMPLOYER_UID)) {
            employerUid = getIntent().getStringExtra(EXTRA_EMPLOYER_UID);
            if (employerUid != null && !employerUid.isEmpty()) {
                loadOrganizationData();
                loadOrganizationJobPosts();
            } else {
                showErrorAndFinish(getString(R.string.error_invalid_organization_id));
            }
        } else {
            showErrorAndFinish(getString(R.string.error_missing_organization_id));
        }
    }

    private void loadOrganizationData() {
        progressBarOrganizationProfile.setVisibility(View.VISIBLE);
        db.collection("users").document(employerUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBarOrganizationProfile.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        Organization organization = documentSnapshot.toObject(Organization.class);
                        if (organization != null) {
                            populateOrganizationDetails(organization);
                        } else {
                            showErrorAndFinish(getString(R.string.error_parsing_organization_data));
                        }
                    } else {
                        showErrorAndFinish(getString(R.string.error_organization_not_found));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching organization data", e);
                    progressBarOrganizationProfile.setVisibility(View.GONE);
                    showErrorAndFinish(getString(R.string.error_loading_organization_data) + ": " + e.getMessage());
                });
    }

    private void populateOrganizationDetails(Organization organization) {
        if (organization.getOrganizationName() != null && !organization.getOrganizationName().isEmpty()) {
            collapsingToolbarOrganization.setTitle(organization.getOrganizationName());
            textViewOrgName.setText(organization.getOrganizationName());
        } else {
            collapsingToolbarOrganization.setTitle(getString(R.string.organization_profile_title)); // Default title
            textViewOrgName.setText(getString(R.string.name_not_available));
        }

        setTextOrHide(textViewOrgType, organization.getOrganizationType(), null);
        setTextOrHide(textViewOrgDescription, organization.getDescription(), null);

        setTextOrHide(textViewOrgContactEmail, organization.getEmail(), null);
        makeTextViewClickable(textViewOrgContactEmail, organization.getEmail(), "mailto:");

        setTextOrHide(textViewOrgContactPhone, organization.getPhoneNumber(), null);
        makeTextViewClickable(textViewOrgContactPhone, organization.getPhoneNumber(), "tel:");

        if (organization.getWebsite() != null && !organization.getWebsite().trim().isEmpty()) {
            textViewOrgWebsite.setText(organization.getWebsite());
            textViewOrgWebsite.setVisibility(View.VISIBLE);
            textViewOrgWebsite.setOnClickListener(v -> openLink(organization.getWebsite()));
        } else {
            textViewOrgWebsite.setVisibility(View.GONE);
        }

        // Use a default placeholder for the header image
        int placeholderHeader = R.drawable.ic_default_org_header; // Ensure this drawable exists
        if (organization.getProfileImageUrl() != null && !organization.getProfileImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(organization.getProfileImageUrl())
                    .placeholder(placeholderHeader)
                    .error(placeholderHeader) // Show placeholder on error
                    .into(imageViewOrganizationHeader);
        } else {
            imageViewOrganizationHeader.setImageResource(placeholderHeader);
        }
    }

    private void loadOrganizationJobPosts() {
        if (employerUid == null || employerUid.isEmpty()) {
            Log.e(TAG, "Cannot load job posts, employerUid is null or empty.");
            // Optionally update UI to reflect that posts cannot be loaded
            textViewOrgJobPostsLabel.setText(getString(R.string.error_loading_job_posts_short));
            recyclerViewOrgJobPosts.setVisibility(View.GONE);
            return;
        }

        db.collection("jobPosts")
                .whereEqualTo("employerUid", employerUid)
                .orderBy("postedDate", Query.Direction.DESCENDING)
                .limit(10) // Consider pagination for larger datasets
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<JobPost> newJobPosts = new ArrayList<>();
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            JobPost jobPost = document.toObject(JobPost.class);
                            // Ensure the JobPost ID is set from the document ID
                            jobPost.setJobPostId(document.getId());
                            newJobPosts.add(jobPost);
                        }
                        textViewOrgJobPostsLabel.setVisibility(View.VISIBLE);
                        recyclerViewOrgJobPosts.setVisibility(View.VISIBLE);
                        textViewOrgJobPostsLabel.setText(getString(R.string.active_job_postings_label));
                    } else {
                        textViewOrgJobPostsLabel.setText(getString(R.string.no_active_job_postings));
                        recyclerViewOrgJobPosts.setVisibility(View.GONE);
                        textViewOrgJobPostsLabel.setVisibility(View.VISIBLE); // Keep label visible for "no posts"
                    }
                    // Update the adapter using submitList()
                    jobPostSimpleAdapter.submitList(newJobPosts);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching organization job posts", e);
                    Toast.makeText(this, getString(R.string.error_loading_job_posts) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    textViewOrgJobPostsLabel.setText(getString(R.string.error_loading_job_posts_short));
                    recyclerViewOrgJobPosts.setVisibility(View.GONE);
                    jobPostSimpleAdapter.submitList(new ArrayList<>()); // Submit empty list on error
                });
    }

    private void setTextOrHide(TextView textView, String text, String prefix) {
        if (textView == null) return;
        if (text != null && !text.trim().isEmpty()) {
            textView.setText(prefix != null ? prefix + text : text);
            textView.setVisibility(View.VISIBLE);
        } else {
            // If you want to explicitly set text to "N/A" or similar for GONE views,
            // do it before setting visibility to GONE, though it won't be visible.
            // textView.setText(getString(R.string.not_available)); // Optional
            textView.setVisibility(View.GONE);
        }
    }

    private void makeTextViewClickable(TextView textView, String data, String uriScheme) {
        if (textView != null && data != null && !data.trim().isEmpty()) {
            textView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(uriScheme + data.trim())); // Trim data
                try {
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException e) {
                    Log.w(TAG, "No app found to handle action: " + uriScheme + data.trim());
                    Toast.makeText(this, getString(R.string.no_app_to_handle_action), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void openLink(String url) {
        if (url == null || url.trim().isEmpty()) return;
        String finalUrl = url.trim();
        if (!finalUrl.toLowerCase().startsWith("http://") && !finalUrl.toLowerCase().startsWith("https://")) {
            finalUrl = "https://" + finalUrl; // Default to https for safety
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl));
        try {
            startActivity(browserIntent);
        } catch (android.content.ActivityNotFoundException e) {
            Log.w(TAG, "No app found to handle URL: " + finalUrl);
            Toast.makeText(this, getString(R.string.no_app_to_handle_action), Toast.LENGTH_SHORT).show();
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Finishes current activity and returns to the previous one on the stack
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Implement the method from JobPostSimpleAdapter.OnJobPostClickListener
    @Override
    public void onJobPostClick(@NonNull JobPost jobPost) {
        Log.d(TAG, "Job Post Clicked: " + jobPost.getTitle() + " (ID: " + jobPost.getJobPostId() + ")");
        Intent intent = new Intent(this, JobDetailsActivity.class);
        intent.putExtra(JobDetailsActivity.EXTRA_JOB_POST_ID, jobPost.getJobPostId());
        // Pass employerUid if JobDetailsActivity needs it and JobPost model has it
        // This is good if JobDetailsActivity might also be opened from contexts where employerUid isn't readily available
        // from the JobPost object itself (though in this case, it should be).
        if (jobPost.getEmployerUid() != null && !jobPost.getEmployerUid().isEmpty()) {
            intent.putExtra(JobDetailsActivity.EXTRA_EMPLOYER_UID, jobPost.getEmployerUid());
        } else if (this.employerUid != null && !this.employerUid.isEmpty()){
            // Fallback to the activity's employerUid if not in jobPost model
            intent.putExtra(JobDetailsActivity.EXTRA_EMPLOYER_UID, this.employerUid);
        }
        startActivity(intent);
    }
}