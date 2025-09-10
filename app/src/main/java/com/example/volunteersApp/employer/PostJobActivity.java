package com.example.volunteersApp.employer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.volunteersApp.LoginActivity;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.ActivityPostJobBinding;
// Model (ensure it's the Firestore-compatible version)
// import com.example.volunteersApp.employer.JobPosting;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore SDK imports
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions; // For merging updates
// import com.google.firebase.firestore.FieldValue; // If manually setting server timestamp in a Map

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date; // For timestamp field in JobPosting model
import java.util.Locale;
// import java.util.Map; // Only if constructing map manually for updates
import java.util.Objects;

public class PostJobActivity extends AppCompatActivity {

    private static final String TAG = "PostJobActivity";
    private static final String JOB_POSTINGS_COLLECTION = "job_postings";

    private ActivityPostJobBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Firestore specific
    private FirebaseFirestore db;
    private CollectionReference jobPostingsCollectionRef;

    private Calendar calendar;

    private String editingPostingId = null; // This will be the Document ID from Firestore
    private boolean isEditMode = false;
    private String currentStatusForEdit = "open"; // Default status

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostJobBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "You need to be logged in to post or edit opportunities.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        jobPostingsCollectionRef = db.collection(JOB_POSTINGS_COLLECTION);

        calendar = Calendar.getInstance();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("EDIT_POSTING_ID")) {
            isEditMode = true;
            editingPostingId = intent.getStringExtra("EDIT_POSTING_ID");
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Edit Opportunity");
            }
            binding.buttonSubmitPost.setText(R.string.update_opportunity);
            if (editingPostingId != null && !editingPostingId.isEmpty()) {
                fetchAndPrefillJobData(editingPostingId);
            } else {
                Toast.makeText(this, "Error: Posting ID missing for edit.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "EDIT_POSTING_ID was passed but its value was null or empty.");
                finish();
            }
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.post_new_opportunity_title));
            }
            binding.buttonSubmitPost.setText(R.string.post_opportunity);
        }
        setupUI();
    }

    private void fetchAndPrefillJobData(String documentId) {
        binding.progressBarPostJob.setVisibility(View.VISIBLE);
        jobPostingsCollectionRef.document(documentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.progressBarPostJob.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        JobPosting existingJob = documentSnapshot.toObject(JobPosting.class);
                        if (existingJob != null) {
                            // The JobPosting model's @DocumentId field should be populated here
                            // For example, if your JobPosting model has 'postingId' annotated with @DocumentId:
                            // editingPostingId = existingJob.getPostingId(); // Confirm it matches documentId
                            currentStatusForEdit = existingJob.getStatus() != null ? existingJob.getStatus() : "open";
                            prefillForm(existingJob);
                        } else {
                            Log.e(TAG, "Failed to deserialize JobPosting object for ID: " + documentId);
                            Toast.makeText(PostJobActivity.this, "Error: Could not load job details.", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        Log.w(TAG, "Job posting not found in Firestore for ID: " + documentId);
                        Toast.makeText(PostJobActivity.this, "Error: Job posting not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBarPostJob.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to fetch job for edit from Firestore (ID: " + documentId + "): ", e);
                    Toast.makeText(PostJobActivity.this, "Failed to load details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void prefillForm(JobPosting job) {
        // Use getters from your Firestore-compatible JobPosting model
        if (binding.editTextOrganizationName != null) {
            binding.editTextOrganizationName.setText(job.getOrganizationName());
        }
        binding.editTextEventName.setText(job.getTitle());
        if (binding.editTextJobTitle != null) {
            binding.editTextJobTitle.setText(job.getJobTitle());
        }
        binding.editTextDescription.setText(job.getDescription());
        binding.editTextDate.setText(job.getDate());
        binding.editTextTime.setText(job.getTime());
        binding.editTextLocation.setText(job.getLocationName());
        binding.editTextCategory.setText(job.getCategory());
        binding.editTextVolunteersNeeded.setText(String.valueOf(job.getVolunteersNeeded()));

        // Pre-fill calendar (this logic remains the same)
        if (job.getDate() != null && !job.getDate().isEmpty()) {
            try {
                SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                calendar.setTime(Objects.requireNonNull(sdfDate.parse(job.getDate())));
            } catch (ParseException | NullPointerException e) {
                Log.e(TAG, "Error parsing date for prefill: " + job.getDate(), e);
            }
        }
        if (job.getTime() != null && !job.getTime().isEmpty()) {
            try {
                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.US);
                Calendar tempCal = Calendar.getInstance();
                tempCal.setTime(Objects.requireNonNull(sdfTime.parse(job.getTime())));
                calendar.set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY));
                calendar.set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE));
            } catch (ParseException | NullPointerException e) {
                Log.e(TAG, "Error parsing time for prefill: " + job.getTime(), e);
            }
        }
    }

    private void setupUI() {
        binding.editTextDate.setOnClickListener(v -> showDatePickerDialog());
        binding.editTextTime.setOnClickListener(v -> showTimePickerDialog());
        binding.buttonSubmitPost.setOnClickListener(v -> validateAndPostJob());
    }

    // Date and Time picker dialogs (showDatePickerDialog, updateDateInView, showTimePickerDialog, updateTimeInView)
    // remain unchanged as they are UI logic.

    private void showDatePickerDialog() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, monthOfYear, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
        };
        new DatePickerDialog(PostJobActivity.this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void updateDateInView() {
        String myFormat = "yyyy-MM-dd"; // e.g., 2024-03-15
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.US);
        binding.editTextDate.setText(sdf.format(calendar.getTime()));
        binding.editTextDate.setError(null); // Clear error if any
    }

    private void showTimePickerDialog() {
        TimePickerDialog.OnTimeSetListener timeSetListener = (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeInView();
        };
        new TimePickerDialog(PostJobActivity.this, timeSetListener,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false) // false for 12-hour format with AM/PM
                .show();
    }

    private void updateTimeInView() {
        String myFormat = "hh:mm a"; // e.g., 03:30 PM
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.US);
        binding.editTextTime.setText(sdf.format(calendar.getTime()));
        binding.editTextTime.setError(null); // Clear error if any
    }


    private void validateAndPostJob() {
        String organizationName = (binding.editTextOrganizationName != null) ? binding.editTextOrganizationName.getText().toString().trim() : "";
        String eventName = binding.editTextEventName.getText().toString().trim();
        String jobTitle = (binding.editTextJobTitle != null) ? binding.editTextJobTitle.getText().toString().trim() : "";
        String description = binding.editTextDescription.getText().toString().trim();
        String date = binding.editTextDate.getText().toString().trim();
        String time = binding.editTextTime.getText().toString().trim();
        String location = binding.editTextLocation.getText().toString().trim();
        String category = binding.editTextCategory.getText().toString().trim();
        String volunteersNeededStr = binding.editTextVolunteersNeeded.getText().toString().trim();

        // --- VALIDATION (remains mostly the same) ---
        if (binding.editTextOrganizationName != null && TextUtils.isEmpty(organizationName)) {
            binding.editTextOrganizationName.setError("Organization name is required.");
            binding.editTextOrganizationName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(eventName)) {
            binding.editTextEventName.setError("Event name is required.");
            binding.editTextEventName.requestFocus();
            return;
        }
        // ... (add other validations as before for jobTitle, description, date, time, location, category, volunteersNeededStr)
        if (TextUtils.isEmpty(description)) {
            binding.editTextDescription.setError("Description is required.");
            binding.editTextDescription.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(date)) {
            binding.editTextDate.setError("Date is required.");
            Toast.makeText(this, "Please select a date.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(time)) {
            binding.editTextTime.setError("Time is required.");
            Toast.makeText(this, "Please select a time.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(location)) {
            binding.editTextLocation.setError("Location is required.");
            binding.editTextLocation.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(category)) {
            binding.editTextCategory.setError("Category is required.");
            binding.editTextCategory.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(volunteersNeededStr)) {
            binding.editTextVolunteersNeeded.setError("Number of volunteers is required.");
            binding.editTextVolunteersNeeded.requestFocus();
            return;
        }

        int volunteersNeeded;
        try {
            volunteersNeeded = Integer.parseInt(volunteersNeededStr);
            if (volunteersNeeded <= 0) {
                binding.editTextVolunteersNeeded.setError("Must be greater than 0.");
                binding.editTextVolunteersNeeded.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            binding.editTextVolunteersNeeded.setError("Invalid number for volunteers.");
            binding.editTextVolunteersNeeded.requestFocus();
            return;
        }

        if (currentUser == null) { // Re-check, though checked in onCreate
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        String employerUid = currentUser.getUid();

        binding.progressBarPostJob.setVisibility(View.VISIBLE);
        binding.buttonSubmitPost.setEnabled(false);

        String status = isEditMode ? currentStatusForEdit : "open";

        // Prepare JobPosting POJO
        // The timestamp field in JobPosting (annotated with @ServerTimestamp)
        // will be automatically populated by Firestore upon saving.
        JobPosting jobPosting = new JobPosting(
                employerUid,
                organizationName,
                eventName,
                jobTitle,
                description,
                date,
                time,
                location,
                category,
                volunteersNeeded,
                status
        );
        // Note: If 'postingId' is a field in JobPosting and managed by @DocumentId,
        // it will be null here for new posts. Firestore assigns it.
        // For edits, 'editingPostingId' holds the document ID.

        if (isEditMode && editingPostingId != null) {
            // --- UPDATE EXISTING JOB POST ---
            DocumentReference docRef = jobPostingsCollectionRef.document(editingPostingId);
            // .set with SetOptions.merge() will update fields in the POJO and leave others untouched.
            // If JobPosting has a @ServerTimestamp field, it will be updated.
            docRef.set(jobPosting, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Toast.makeText(PostJobActivity.this, "Opportunity updated successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Log.e(TAG, "Failed to update opportunity in Firestore.", e);
                        Toast.makeText(PostJobActivity.this, "Failed to update. " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            // --- CREATE NEW JOB POST ---
            // Firestore will auto-generate an ID for the new document.
            // The @ServerTimestamp field in jobPosting will be set by the server.
            jobPostingsCollectionRef.add(jobPosting)
                    .addOnSuccessListener(documentReference -> {
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Log.d(TAG, "New opportunity posted with ID: " + documentReference.getId());
                        Toast.makeText(PostJobActivity.this, "Opportunity posted successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Log.e(TAG, "Failed to post opportunity to Firestore.", e);
                        String errorMessage = "Failed to post.";
                        if (e.getMessage() != null) {
                            if (e.getMessage().toLowerCase().contains("permission_denied") || e.getMessage().toLowerCase().contains("missing or insufficient permissions")) {
                                errorMessage += " Please check Firestore security rules for '" + JOB_POSTINGS_COLLECTION + "'.";
                            } else {
                                errorMessage += " " + e.getMessage();
                            }
                        }
                        Toast.makeText(PostJobActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}