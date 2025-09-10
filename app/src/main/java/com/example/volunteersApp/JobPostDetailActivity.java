package com.example.volunteersApp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Make sure this import is correct

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.volunteersApp.models.JobPost;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class JobPostDetailActivity extends AppCompatActivity {

    private static final String TAG = "JobPostDetailActivity";

    // UI Elements
    private TextView textViewDetailJobTitle, textViewDetailOrgName, textViewDetailDate,
            textViewDetailLocation, textViewDetailDescription, textViewDetailSkills,
            textViewDetailUrgency;

    private Button buttonViewOrgProfile;
    private Button buttonApplyNow;
    private ProgressBar progressBarDetailLoading;

    // Firebase Firestore
    private FirebaseFirestore db;
    private DocumentReference jobPostRef;
    private CollectionReference applicationsCollectionRef;
    private FirebaseAuth mAuth;

    private String currentJobPostId;
    private JobPost currentJobPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_post_detail); // This should be your layout file name

        // Corrected Toolbar ID
        Toolbar toolbar = findViewById(R.id.toolbar_job_detail); // <<< CORRECTED ID HERE
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Opportunity Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        applicationsCollectionRef = db.collection("applications");

        initializeViews();
        if (progressBarDetailLoading != null) {
            progressBarDetailLoading.setVisibility(View.VISIBLE);
        }

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("JOB_POST_ID")) {
            currentJobPostId = intent.getStringExtra("JOB_POST_ID");
            if (currentJobPostId != null && !currentJobPostId.isEmpty()) {
                jobPostRef = db.collection("jobPosts").document(currentJobPostId);
                fetchJobDetails();
            } else {
                handleLoadError("Error: Job Post ID is missing or invalid.");
            }
        } else {
            handleLoadError("Error: Could not load job details. Intent data missing.");
        }

        setupButtonClickListeners();
    }

    // ... rest of your JobPostDetailActivity class ...

    private void initializeViews() {
        // Ensure these IDs match your activity_job_post_detail.xml
        // The XML you provided uses "Detail" in many IDs, e.g., textViewDetailJobTitle
        textViewDetailJobTitle = findViewById(R.id.textViewDetailJobTitle);
        textViewDetailOrgName = findViewById(R.id.textViewDetailOrgName);
        textViewDetailDate = findViewById(R.id.textViewDetailDate);
        textViewDetailLocation = findViewById(R.id.textViewDetailLocation);
        textViewDetailDescription = findViewById(R.id.textViewDetailDescription);
        textViewDetailSkills = findViewById(R.id.textViewDetailSkills);
        textViewDetailUrgency = findViewById(R.id.textViewDetailUrgency);

        buttonViewOrgProfile = findViewById(R.id.buttonViewOrgProfile);
        buttonApplyNow = findViewById(R.id.buttonApplyNow);
        progressBarDetailLoading = findViewById(R.id.progressBarDetailLoading); // XML has progressBarDetailLoading
    }
    // ... rest of the methods (fetchJobDetails, populateUiWithJobDetails, etc.)
    private void fetchJobDetails() {
        if (jobPostRef == null) {
            handleLoadError("Error: Job reference is not initialized.");
            return;
        }
        if (progressBarDetailLoading != null) progressBarDetailLoading.setVisibility(View.VISIBLE);

        jobPostRef.get().addOnSuccessListener(documentSnapshot -> {
            if (progressBarDetailLoading != null) progressBarDetailLoading.setVisibility(View.GONE);
            if (documentSnapshot.exists()) {
                currentJobPost = documentSnapshot.toObject(JobPost.class);
                if (currentJobPost != null) {
                    currentJobPost.setJobPostId(documentSnapshot.getId());
                    populateUiWithJobDetails(currentJobPost);
                    checkIfAlreadyApplied();
                } else {
                    handleLoadError("Error: Could not parse job details from Firestore.");
                }
            } else {
                handleLoadError("Job post not found in Firestore.");
            }
        }).addOnFailureListener(e -> {
            if (progressBarDetailLoading != null) progressBarDetailLoading.setVisibility(View.GONE);
            Log.e(TAG, "Failed to load job details from Firestore: ", e);
            handleLoadError("Failed to load job details: " + e.getMessage());
        });
    }

    private void populateUiWithJobDetails(JobPost jobPost) {
        String title = jobPost.getJobTitle() != null ? jobPost.getJobTitle() : jobPost.getTitle();
        if (title == null) title = "Details Unavailable";

        textViewDetailJobTitle.setText(title);
        if (jobPost.getOrganizationName() != null) textViewDetailOrgName.setText(jobPost.getOrganizationName());

        // Check if the TextViews themselves are null before setting text or visibility
        if (textViewDetailDate != null) {
            if (jobPost.getDate() != null) {
                textViewDetailDate.setText(jobPost.getDate());
                textViewDetailDate.setVisibility(View.VISIBLE);
            } else {
                textViewDetailDate.setVisibility(View.GONE);
            }
        }

        if (textViewDetailLocation != null) {
            if (jobPost.getLocationName() != null) {
                textViewDetailLocation.setText(jobPost.getLocationName());
                textViewDetailLocation.setVisibility(View.VISIBLE);
            } else {
                textViewDetailLocation.setVisibility(View.GONE);
            }
        }

        if (textViewDetailDescription != null) {
            if (jobPost.getDescription() != null) {
                textViewDetailDescription.setText(jobPost.getDescription());
                textViewDetailDescription.setVisibility(View.VISIBLE);
            } else {
                textViewDetailDescription.setVisibility(View.GONE);
            }
        }
        // For Skills and Urgency, also ensure the corresponding label is handled if you have separate labels
        if (textViewDetailSkills != null) {
            TextView skillsLabel = findViewById(R.id.textViewDetailSkillsLabel); // Assuming you have this label
            if (jobPost.getSkills() != null && !jobPost.getSkills().isEmpty()) {
                textViewDetailSkills.setText(jobPost.getSkills());
                textViewDetailSkills.setVisibility(View.VISIBLE);
                if (skillsLabel != null) skillsLabel.setVisibility(View.VISIBLE);
            } else {
                textViewDetailSkills.setVisibility(View.GONE);
                if (skillsLabel != null) skillsLabel.setVisibility(View.GONE);
            }
        }

        if (textViewDetailUrgency != null) {
            TextView urgencyLabel = findViewById(R.id.textViewDetailUrgencyLabel); // Assuming you have this label
            if (jobPost.getUrgency() != null && !jobPost.getUrgency().isEmpty()) {
                textViewDetailUrgency.setText(jobPost.getUrgency());
                textViewDetailUrgency.setVisibility(View.VISIBLE);
                if (urgencyLabel != null) urgencyLabel.setVisibility(View.VISIBLE);
            } else {
                textViewDetailUrgency.setVisibility(View.GONE);
                if (urgencyLabel != null) urgencyLabel.setVisibility(View.GONE);
            }
        }


        if (buttonViewOrgProfile != null) buttonViewOrgProfile.setEnabled(true);
    }
    private void handleLoadError(String message) {
        Toast.makeText(JobPostDetailActivity.this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
        if (buttonApplyNow != null) buttonApplyNow.setEnabled(false);
        if (buttonViewOrgProfile != null) buttonViewOrgProfile.setEnabled(false);
        if (progressBarDetailLoading != null) progressBarDetailLoading.setVisibility(View.GONE);
    }

    private void setupButtonClickListeners() {
        if (buttonApplyNow != null) {
            buttonApplyNow.setOnClickListener(v -> {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser == null) {
                    Toast.makeText(this, "You need to be logged in to apply.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (currentJobPost == null || currentJobPostId == null || currentJobPost.getEmployerUid() == null) {
                    Toast.makeText(this, "Job details not fully loaded or employer info missing.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showApplicationConfirmationDialog();
            });
        }

        if (buttonViewOrgProfile != null) {
            buttonViewOrgProfile.setOnClickListener(v -> {
                if (currentJobPost == null || currentJobPost.getEmployerUid() == null) {
                    Toast.makeText(this, "Organization details not available.", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Example: Open OrganizationProfileActivity
                // Intent intent = new Intent(JobPostDetailActivity.this, OrganizationProfileActivity.class);
                // intent.putExtra("EMPLOYER_UID", currentJobPost.getEmployerUid());
                // startActivity(intent);
                Toast.makeText(JobPostDetailActivity.this, "View Org Profile for: " + currentJobPost.getEmployerUid(), Toast.LENGTH_SHORT).show();
            });
        }
    }
    private void showApplicationConfirmationDialog() {
        if (currentJobPost == null || (currentJobPost.getJobTitle() == null && currentJobPost.getTitle() == null) ) {
            Toast.makeText(this, "Job details missing for confirmation.", Toast.LENGTH_SHORT).show();
            return;
        }
        String jobDisplayTitle = currentJobPost.getJobTitle() != null ? currentJobPost.getJobTitle() : currentJobPost.getTitle();
        new AlertDialog.Builder(this)
                .setTitle("Confirm Application")
                .setMessage("Are you sure you want to apply for '" + jobDisplayTitle + "'?")
                .setPositiveButton("Apply", (dialog, which) -> submitApplication())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitApplication() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentJobPost == null || currentJobPostId == null || currentJobPost.getEmployerUid() == null) {
            Toast.makeText(this, "Cannot submit application. Missing data or not logged in.", Toast.LENGTH_LONG).show();
            if (buttonApplyNow != null) buttonApplyNow.setEnabled(true); // Re-enable if submission aborted early
            return;
        }

        if (buttonApplyNow != null) buttonApplyNow.setEnabled(false);
        Toast.makeText(this, "Submitting application...", Toast.LENGTH_SHORT).show();

        String volunteerUid = currentUser.getUid();
        String volunteerName = currentUser.getDisplayName();
        if (volunteerName == null || volunteerName.isEmpty()) {
            volunteerName = "Volunteer User"; // Fallback
        }
        String volunteerEmail = currentUser.getEmail();

        String applicationId = applicationsCollectionRef.document().getId();

        Map<String, Object> applicationData = new HashMap<>();
        applicationData.put("applicationId", applicationId);
        applicationData.put("jobPostId", currentJobPostId);
        applicationData.put("jobTitle", currentJobPost.getJobTitle() != null ? currentJobPost.getJobTitle() : currentJobPost.getTitle());
        applicationData.put("organizationName", currentJobPost.getOrganizationName());
        applicationData.put("userId", volunteerUid);
        applicationData.put("volunteerName", volunteerName);
        applicationData.put("volunteerEmail", volunteerEmail);
        applicationData.put("employerUid", currentJobPost.getEmployerUid());
        applicationData.put("appliedAt", FieldValue.serverTimestamp());
        applicationData.put("status", "pending");

       // Object createdAtObj = currentJobPost.getCreatedAt();
      //  if (createdAtObj instanceof Long) {
        //    long createdAtTimestamp = (Long) createdAtObj;
        //    if (createdAtTimestamp > 0) {
         //       applicationData.put("jobCreatedAt", createdAtTimestamp);
      //      }
      //  } else if (createdAtObj != null) {
      //      Log.w(TAG, "jobCreatedAt is not of type Long: " + createdAtObj.getClass().getTitle());
     //   }

        applicationsCollectionRef.document(applicationId).set(applicationData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(JobPostDetailActivity.this, "Application submitted successfully!", Toast.LENGTH_LONG).show();
                    if (buttonApplyNow != null) {
                        buttonApplyNow.setText("Applied");
                        buttonApplyNow.setEnabled(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(JobPostDetailActivity.this, "Failed to submit application. Please try again.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error submitting application to Firestore", e);
                    if (buttonApplyNow != null) buttonApplyNow.setEnabled(true);
                });
    }

    private void checkIfAlreadyApplied() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentJobPostId == null || currentJobPostId.isEmpty()) {
            if (buttonApplyNow != null) {
                buttonApplyNow.setText("Apply Now");
                buttonApplyNow.setEnabled(false); // Can't apply if not logged in or no job ID
            }
            return;
        }

        if (buttonApplyNow != null) {
            buttonApplyNow.setEnabled(false);
            buttonApplyNow.setText("Checking...");
        }

        applicationsCollectionRef
                .whereEqualTo("volunteerUid", currentUser.getUid())
                .whereEqualTo("jobPostId", currentJobPostId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (buttonApplyNow == null) return; // View might have been destroyed

                    if (!queryDocumentSnapshots.isEmpty()) {
                        buttonApplyNow.setText("Already Applied");
                        buttonApplyNow.setEnabled(false);
                    } else {
                        // Before enabling, you might want to check if the job is still available
                        // (e.g., based on a 'slotsAvailable' field in currentJobPost)
                        buttonApplyNow.setText("Apply Now");
                        buttonApplyNow.setEnabled(true); // Enable if not applied and job is (presumably) available
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "checkIfAlreadyApplied:onFailure (Firestore)", e);
                    if (buttonApplyNow != null) {
                        buttonApplyNow.setText("Apply Now");
                        buttonApplyNow.setEnabled(true); // Default to enabled on error
                    }
                    Toast.makeText(this, "Could not check application status.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}