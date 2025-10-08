package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
// import androidx.annotation.Nullable; // No longer needed for FirebaseFirestoreSettings here
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.models.JobPost;
// import com.google.firebase.FirebaseApp; // No longer needed for specific DB init
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
// import com.google.firebase.firestore.FirebaseFirestoreSettings; // No longer needed for emulator setup here
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
// import java.util.Objects; // Not directly used, can be removed if no other usage

public class JobDetailsActivity extends AppCompatActivity {
    // Define the constants for the extras this Activity expects
    public static final String EXTRA_JOB_POST_ID = "com.example.volunteersApp.JOB_POST_ID";
    public static final String EXTRA_EMPLOYER_UID = "com.example.volunteersApp.EMPLOYER_UID";

    private static final String TAG = "JobDetailsActivity";

    // --- Views ---
    private ImageView imageViewJobDetailImage;
    private TextView textViewJobDetailTitle, textViewJobDetailOrgName, textViewJobDetailLocation,
            textViewJobDetailDate, textViewJobDetailTime,
            textViewJobDetailDescription, textViewJobDetailSkills, textViewJobDetailUrgency,
            textViewJobDetailRequirements, textViewJobDetailContact, textViewJobDetailHours,
            textViewVolunteersNeeded;
    private Button buttonApplyNow;
    private ProgressBar progressBarApply;
    private ProgressBar progressBarLoadingDetails;
    private Toolbar toolbarJobDetails;
    private View contentLayout;

    // --- Firebase ---
    private FirebaseFirestore fsDb; // Will point to the (default) Firestore database
    private CollectionReference jobPostingsCollection;
    private CollectionReference applicationsCollection;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private JobPost currentJobPost;

    private boolean isSubmittingApplication = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_details);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // --- Initialize Firestore for the (default) database ---
        try {
            fsDb = FirebaseFirestore.getInstance(); // This now gets the (default) database instance
            Log.i(TAG, "Successfully initialized Firestore for (default) database.");

            // Optional: Configure Firestore Emulator for (default) database (Uncomment and modify if using)
            // if (com.example.volunteersApp.BuildConfig.DEBUG) {
            //     Log.d(TAG, "DEBUG mode: Attempting to configure Firestore emulator for (default) database");
            //     try {
            //         fsDb.useEmulator("10.0.2.2", 8080); // Default port for Firestore emulator
            //         Log.i(TAG, "Firestore emulator configured for (default) database to use 10.0.2.2:8080");
            //         // For emulators, you might not need to disable persistence unless you have specific reasons.
            //         // FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
            //         //         .setPersistenceEnabled(false)
            //         //         .build();
            //         // fsDb.setFirestoreSettings(settings);
            //     } catch (IllegalStateException e) {
            //         Log.w(TAG, "Firestore emulator settings for (default) database could not be applied (possibly already set or instance in use): " + e.getMessage());
            //     }
            // }

        } catch (IllegalStateException e) {
            Log.e(TAG, "Error initializing Firebase or getting (default) Firestore instance. " +
                    "Ensure google-services.json is correct.", e);
            Toast.makeText(this, "Error initializing database services. Please check logs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize Firestore collection references (from the (default) fsDb instance)
        // Ensure these collection names ("job_postings", "applications") exist in your (default) database
        jobPostingsCollection = fsDb.collection("job_postings");
        applicationsCollection = fsDb.collection("applications");

        initializeViews();
        setSupportActionBar(toolbarJobDetails);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String jobPostId = getIntent().getStringExtra(EXTRA_JOB_POST_ID);

        if (jobPostId != null && !jobPostId.isEmpty()) {
            loadJobDetailsFromFirestore(jobPostId);
        } else {
            Log.e(TAG, "Error: Job Post ID missing in Intent.");
            Toast.makeText(this, getString(R.string.error_job_id_missing), Toast.LENGTH_LONG).show();
            finish();
        }

        buttonApplyNow.setOnClickListener(v -> {
            if (currentUser == null) {
                Toast.makeText(this, getString(R.string.please_log_in_to_apply), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentJobPost == null || currentJobPost.getJobPostId() == null) {
                Toast.makeText(this, getString(R.string.job_data_unavailable_cannot_apply), Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSubmittingApplication) {
                Toast.makeText(this, getString(R.string.application_in_progress), Toast.LENGTH_SHORT).show();
                return;
            }
            showApplicationConfirmationDialog();
        });

        textViewJobDetailOrgName.setOnClickListener(v -> {
            if (currentJobPost != null && currentJobPost.getEmployerUid() != null) {
                // TODO: Implement navigation to OrganizationProfileActivity
                // Intent intent = new Intent(JobDetailsActivity.this, OrganizationProfileActivity.class);
                // intent.putExtra(OrganizationProfileActivity.EXTRA_ORG_UID, currentJobPost.getEmployerUid());
                // startActivity(intent);
                Toast.makeText(this, "Organization Profile Clicked (Implement Navigation)", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeViews() {
        toolbarJobDetails = findViewById(R.id.toolbarJobDetails);
        imageViewJobDetailImage = findViewById(R.id.imageViewJobDetailImage);
        textViewJobDetailTitle = findViewById(R.id.textViewJobDetailTitle);
        textViewJobDetailOrgName = findViewById(R.id.textViewJobDetailOrgName);
        textViewJobDetailLocation = findViewById(R.id.textViewJobDetailLocation);
        textViewJobDetailDate = findViewById(R.id.textViewJobDetailDate);
        textViewJobDetailTime = findViewById(R.id.textViewJobDetailTime);
        textViewJobDetailDescription = findViewById(R.id.textViewJobDetailDescription);
        textViewJobDetailSkills = findViewById(R.id.textViewJobDetailSkills);
        textViewJobDetailUrgency = findViewById(R.id.textViewJobDetailUrgency);
        textViewJobDetailRequirements = findViewById(R.id.textViewJobDetailRequirements);
        textViewJobDetailContact = findViewById(R.id.textViewJobDetailContact);
        textViewJobDetailHours = findViewById(R.id.textViewJobDetailHours);
        textViewVolunteersNeeded = findViewById(R.id.textViewVolunteersNeeded);
        buttonApplyNow = findViewById(R.id.buttonApplyNow);
        progressBarApply = findViewById(R.id.progressBarApply);
        progressBarLoadingDetails = findViewById(R.id.progressBarLoadingDetails);
        contentLayout = findViewById(R.id.contentLayoutJobDetails);
    }

    private void showApplicationConfirmationDialog() {
        if (!isFinishing() && !isDestroyed()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.confirm_application_title))
                    .setMessage(String.format(getString(R.string.confirm_application_message), currentJobPost != null ? currentJobPost.getJobTitle() : "this job"))
                    .setPositiveButton(getString(R.string.apply_button_text), (dialog, which) -> {
                        submitApplicationFirestore();
                    })
                    .setNegativeButton(getString(R.string.cancel_button_text), null)
                    .setIcon(R.drawable.ic_dialog_info) // Make sure this drawable exists
                    .show();
        }
    }

    private void loadJobDetailsFromFirestore(String jobPostId) {
        if (progressBarLoadingDetails != null) progressBarLoadingDetails.setVisibility(View.VISIBLE);
        if (contentLayout != null) contentLayout.setVisibility(View.GONE);
        buttonApplyNow.setEnabled(false);

        Log.d(TAG, "Loading job details from (default) Firestore for ID: " + jobPostId);

        jobPostingsCollection.document(jobPostId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (progressBarLoadingDetails != null) progressBarLoadingDetails.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        Log.i(TAG, "Job post document found in (default) Firestore.");
                        currentJobPost = documentSnapshot.toObject(JobPost.class);
                        if (currentJobPost != null) {
                            currentJobPost.setJobPostId(documentSnapshot.getId()); // Essential
                            populateJobDetails();
                            checkIfAlreadyAppliedFirestore();
                            if (getSupportActionBar() != null) {
                                getSupportActionBar().setTitle(currentJobPost.getJobTitle() != null ? currentJobPost.getJobTitle() : getString(R.string.job_details_title));
                            }
                            if (contentLayout != null) contentLayout.setVisibility(View.VISIBLE);
                        } else {
                            Log.e(TAG, "Failed to parse JobPost from (default) Firestore document. Data: " + documentSnapshot.getData());
                            Toast.makeText(this, getString(R.string.failed_to_load_job_details), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        Log.e(TAG, "No such job post found in (default) Firestore with ID: " + jobPostId);
                        Toast.makeText(this, getString(R.string.no_job_data_found), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    if (progressBarLoadingDetails != null) progressBarLoadingDetails.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading job details from (default) Firestore", e);
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("permission_denied")) {
                        Toast.makeText(this, getString(R.string.permission_denied_firestore_details) + " Check Firestore rules for (default) database.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, getString(R.string.failed_to_load_job_details) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    finish();
                });
    }

    private void populateJobDetails() {
        if (currentJobPost == null) {
            Log.e(TAG, "currentJobPost is null in populateJobDetails.");
            return;
        }
        textViewJobDetailTitle.setText(currentJobPost.getJobTitle());
        textViewJobDetailOrgName.setText(currentJobPost.getOrganizationName());
        textViewJobDetailLocation.setText(currentJobPost.getLocationName());
        textViewJobDetailDescription.setText(currentJobPost.getDescription());

        if (currentJobPost.getEventTimestamp() > 0) {
            Date eventDate = new Date(currentJobPost.getEventTimestamp());
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            textViewJobDetailDate.setText(dateFormat.format(eventDate));
            textViewJobDetailTime.setText(timeFormat.format(eventDate));
            textViewJobDetailTime.setVisibility(View.VISIBLE);
        } else {
            textViewJobDetailDate.setText(currentJobPost.getDate() != null ? currentJobPost.getDate() : getString(R.string.date_not_available));
            if (currentJobPost.getTime() != null && !currentJobPost.getTime().isEmpty()) {
                textViewJobDetailTime.setText(currentJobPost.getTime());
                textViewJobDetailTime.setVisibility(View.VISIBLE);
            } else {
                textViewJobDetailTime.setVisibility(View.GONE);
            }
        }

        setTextOrHide(textViewJobDetailSkills, currentJobPost.getSkills(), getString(R.string.prefix_skills_required_fallback));
        setTextOrHide(textViewJobDetailUrgency, currentJobPost.getUrgency(), getString(R.string.prefix_urgency_fallback));
        setTextOrHide(textViewJobDetailRequirements, currentJobPost.getRequirements(), getString(R.string.prefix_requirements_fallback));
        setTextOrHide(textViewJobDetailContact, currentJobPost.getContactInfoProvidedByEmployer(), getString(R.string.prefix_contact_fallback));
        setTextOrHide(textViewJobDetailHours, currentJobPost.getHours(), getString(R.string.prefix_expected_hours_fallback));

        if (currentJobPost.getVolunteersNeeded() > 0) {
            textViewVolunteersNeeded.setText(String.format(Locale.getDefault(), getString(R.string.prefix_volunteers_needed_fallback_plural), currentJobPost.getVolunteersNeeded()));
            textViewVolunteersNeeded.setVisibility(View.VISIBLE);
        } else {
            textViewVolunteersNeeded.setText(getString(R.string.volunteers_needed_none_or_filled_fallback));
            textViewVolunteersNeeded.setVisibility(View.VISIBLE);
        }

        int placeholderDrawable = R.drawable.ic_default_image_placeholder; // Make sure this drawable exists
        Glide.with(this)
                .load(currentJobPost.getImageUrl())
                .placeholder(placeholderDrawable)
                .error(placeholderDrawable)
                .into(imageViewJobDetailImage);
    }

    private void setTextOrHide(TextView textView, String text, String prefix) {
        if (textView == null) return;
        if (text != null && !text.trim().isEmpty()) {
            textView.setText(prefix.isEmpty() ? text : prefix + text);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    private void checkIfAlreadyAppliedFirestore() {
        if (currentUser == null || currentJobPost == null || currentJobPost.getJobPostId() == null) {
            Log.w(TAG, "Cannot check application status (Firestore): User or JobPost data missing.");
            buttonApplyNow.setText(getString(R.string.apply_now_button));
            buttonApplyNow.setEnabled(true); // Or false if login is strictly required before enabling
            return;
        }
        progressBarApply.setVisibility(View.VISIBLE);
        buttonApplyNow.setEnabled(false);

        applicationsCollection
                .whereEqualTo("jobPostId", currentJobPost.getJobPostId())
                .whereEqualTo("volunteerUid", currentUser.getUid())
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBarApply.setVisibility(View.GONE);
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.i(TAG, "User " + currentUser.getUid() + " has already applied (Firestore) for job " + currentJobPost.getJobPostId());
                        buttonApplyNow.setText(getString(R.string.applied_status));
                        buttonApplyNow.setEnabled(false);
                    } else {
                        Log.i(TAG, "User " + currentUser.getUid() + " has not yet applied (Firestore) for job " + currentJobPost.getJobPostId());
                        if (currentJobPost.getVolunteersNeeded() > 0) {
                            buttonApplyNow.setText(getString(R.string.apply_now_button));
                            buttonApplyNow.setEnabled(true);
                        } else {
                            buttonApplyNow.setText(getString(R.string.job_fully_booked));
                            buttonApplyNow.setEnabled(false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progressBarApply.setVisibility(View.GONE);
                    Log.w(TAG, "checkIfAlreadyAppliedFirestore:onFailure", e);
                    Toast.makeText(JobDetailsActivity.this,
                            String.format(getString(R.string.failed_to_check_application_status_error_fallback), e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    buttonApplyNow.setText(getString(R.string.apply_now_button));
                    buttonApplyNow.setEnabled(true);
                });
    }

    private void submitApplicationFirestore() {
        if (currentUser == null || currentJobPost == null || currentJobPost.getJobPostId() == null) {
            Toast.makeText(this, getString(R.string.error_missing_data_for_application), Toast.LENGTH_SHORT).show();
            return;
        }

        isSubmittingApplication = true;
        progressBarApply.setVisibility(View.VISIBLE);
        buttonApplyNow.setEnabled(false);
        buttonApplyNow.setText(getString(R.string.submitting_application_button_text));

        final DocumentReference jobPostRef = jobPostingsCollection.document(currentJobPost.getJobPostId());
        final DocumentReference newApplicationRef = applicationsCollection.document(); // Auto-generate ID

        fsDb.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot jobSnapshot = transaction.get(jobPostRef);
            Long currentVolunteersNeededLong = jobSnapshot.getLong("volunteersNeeded");

            if (currentVolunteersNeededLong == null) {
                throw new FirebaseFirestoreException("volunteersNeeded field is missing or not a number in job post: " + jobPostRef.getId(),
                        FirebaseFirestoreException.Code.ABORTED);
            }

            int currentVolunteersNeeded = currentVolunteersNeededLong.intValue();
            Log.d(TAG, "Transaction (Firestore): currentVolunteersNeeded: " + currentVolunteersNeeded + " for job: " + currentJobPost.getJobPostId());

            if (currentVolunteersNeeded <= 0) {
                throw new FirebaseFirestoreException("Job is already fully booked. Volunteers needed: " + currentVolunteersNeeded,
                        FirebaseFirestoreException.Code.ABORTED);
            }

            transaction.update(jobPostRef, "volunteersNeeded", currentVolunteersNeeded - 1);

            Map<String, Object> applicationData = new HashMap<>();
            applicationData.put("jobPostId", currentJobPost.getJobPostId());
            applicationData.put("jobTitle", currentJobPost.getJobTitle());
            applicationData.put("employerUid", currentJobPost.getEmployerUid());
            applicationData.put("organizationName", currentJobPost.getOrganizationName());
            applicationData.put("volunteerUid", currentUser.getUid());
            applicationData.put("volunteerName", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Anonymous Volunteer");
            applicationData.put("volunteerEmail", currentUser.getEmail());
            applicationData.put("applicationDate", FieldValue.serverTimestamp());
            applicationData.put("status", "pending"); // Default status
            if (currentJobPost.getImageUrl() != null) {
                applicationData.put("jobMainImage", currentJobPost.getImageUrl());
            }
            if (currentJobPost.getEventTimestamp() > 0) {
                applicationData.put("eventTimestamp", currentJobPost.getEventTimestamp());
            }

            transaction.set(newApplicationRef, applicationData);
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.i(TAG, "Transaction successful (Firestore): EventApplication submitted and volunteersNeeded decremented.");
            isSubmittingApplication = false;
            progressBarApply.setVisibility(View.GONE);
            Toast.makeText(JobDetailsActivity.this, getString(R.string.application_submitted_successfully), Toast.LENGTH_SHORT).show();
            buttonApplyNow.setText(getString(R.string.applied_status));
            buttonApplyNow.setEnabled(false);

            jobPostRef.get().addOnSuccessListener(updatedJobSnapshot -> {
                if (updatedJobSnapshot.exists()) {
                    Long newCount = updatedJobSnapshot.getLong("volunteersNeeded");
                    if (newCount != null && currentJobPost != null) {
                        Log.d(TAG, "Updating local currentJobPost.volunteersNeeded to: " + newCount.intValue() + " (from Firestore after transaction)");
                        currentJobPost.setVolunteersNeeded(newCount.intValue());
                        populateJobDetails();
                    }
                }
            });

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Transaction failed (Firestore): ", e);
            isSubmittingApplication = false;
            progressBarApply.setVisibility(View.GONE);
            buttonApplyNow.setEnabled(true);
            buttonApplyNow.setText(getString(R.string.apply_now_button));

            if (e instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException firestoreEx = (FirebaseFirestoreException) e;
                if (firestoreEx.getCode() == FirebaseFirestoreException.Code.ABORTED) {
                    if (firestoreEx.getMessage() != null && firestoreEx.getMessage().contains("fully booked")) {
                        Toast.makeText(JobDetailsActivity.this, getString(R.string.job_fully_booked_error_fallback), Toast.LENGTH_LONG).show();
                        buttonApplyNow.setText(getString(R.string.job_fully_booked));
                        buttonApplyNow.setEnabled(false);
                        jobPostRef.get().addOnSuccessListener(latestJobSnapshot -> {
                            if (latestJobSnapshot.exists() && currentJobPost != null) {
                                Long latestCount = latestJobSnapshot.getLong("volunteersNeeded");
                                if (latestCount != null) {
                                    currentJobPost.setVolunteersNeeded(latestCount.intValue());
                                    populateJobDetails();
                                }
                            }
                        });
                    } else if (firestoreEx.getMessage() != null && firestoreEx.getMessage().contains("volunteersNeeded field is missing")) {
                        Toast.makeText(JobDetailsActivity.this, getString(R.string.job_data_error_contact_admin_fallback) , Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(JobDetailsActivity.this, getString(R.string.failed_to_submit_application_transaction_aborted_fallback) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(JobDetailsActivity.this, getString(R.string.failed_to_submit_application_error_fallback) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(JobDetailsActivity.this, getString(R.string.failed_to_submit_application_error_fallback) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // finish(); // or onBackPressed();
            onBackPressed(); // More standard behavior for up button
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Ensure all R.string values used below are defined in your strings.xml
    // e.g., R.string.error_job_id_missing, R.string.please_log_in_to_apply, etc.
}
