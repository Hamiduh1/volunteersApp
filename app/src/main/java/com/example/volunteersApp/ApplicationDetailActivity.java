package com.example.volunteersApp;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;


// Firestore Imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.Timestamp; // For Firestore Timestamp

// You might use Glide or Picasso for image loading
// import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class ApplicationDetailActivity extends AppCompatActivity {

    private static final String TAG = "AppDetailActivity";

    // Firestore paths
    private static final String JOB_POSTINGS_COLLECTION = "job_postings";
    private static final String APPLICATIONS_SUBCOLLECTION = "applications";
    private static final String USERS_COLLECTION = "users"; // Assuming a root users collection

    // Intent Extras
    public static final String EXTRA_APPLICATION_ID = "APPLICATION_ID"; // This is the Volunteer's UID
    public static final String EXTRA_JOB_POSTING_ID = "JOB_POSTING_ID";

    private TextView textViewVolunteerName, textViewApplicationDate, textViewApplicationStatus;
    private TextView textViewVolunteerEmail, textViewVolunteerPhone, textViewCoverLetter; // Example detail fields
    private ImageView imageViewVolunteerProfile;
    private Button buttonAcceptApplication, buttonRejectApplication;
    private ProgressBar progressBarDetail;
    private View contentContainer; // A layout holding all the content views

    private FirebaseFirestore db;
    private String jobPostingId;
    private String applicationId; // Volunteer's UID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_application_detail); // Create this layout file

        Toolbar toolbar = findViewById(R.id.toolbar_application_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("EventApplication Details");
        }

        db = FirebaseFirestore.getInstance();

        // Get data from Intent
        jobPostingId = getIntent().getStringExtra(EXTRA_JOB_POSTING_ID);
        applicationId = getIntent().getStringExtra(EXTRA_APPLICATION_ID);

        if (jobPostingId == null || applicationId == null) {
            Toast.makeText(this, "Error: Missing application or posting ID.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Missing jobPostingId or applicationId in Intent extras.");
            finish();
            return;
        }

        initializeViews();
        setupButtonListeners(); // Setup listeners before loading data, or disable buttons initially

        loadApplicationDetails();
    }

    private void initializeViews() {
        contentContainer = findViewById(R.id.contentContainerApplicationDetail);
        progressBarDetail = findViewById(R.id.progressBarApplicationDetail);

        imageViewVolunteerProfile = findViewById(R.id.imageViewVolunteerProfileDetail);
        textViewVolunteerName = findViewById(R.id.textViewVolunteerNameDetail);
        textViewApplicationDate = findViewById(R.id.textViewApplicationDateDetail);
        textViewApplicationStatus = findViewById(R.id.textViewApplicationStatusDetail);
        textViewVolunteerEmail = findViewById(R.id.textViewVolunteerEmailDetail);
        textViewVolunteerPhone = findViewById(R.id.textViewVolunteerPhoneDetail);
        textViewCoverLetter = findViewById(R.id.textViewCoverLetterDetail); // Example

        buttonAcceptApplication = findViewById(R.id.buttonAcceptApplication);
        buttonRejectApplication = findViewById(R.id.buttonRejectApplication);

        // Initially hide content and show progress bar
        contentContainer.setVisibility(View.GONE);
        progressBarDetail.setVisibility(View.VISIBLE);
    }

    private void setupButtonListeners() {
        buttonAcceptApplication.setOnClickListener(v -> updateApplicationStatus("accepted"));
        buttonRejectApplication.setOnClickListener(v -> updateApplicationStatus("rejected"));
    }


    private void loadApplicationDetails() {
        setLoadingState(true);

        // Path to the specific application document
        DocumentReference applicationRef = db.collection(JOB_POSTINGS_COLLECTION)
                .document(jobPostingId)
                .collection(APPLICATIONS_SUBCOLLECTION)
                .document(applicationId); // applicationId is volunteer's UID

        applicationRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot appDocument = task.getResult();
                if (appDocument != null && appDocument.exists()) {
                    // Populate application-specific data
                    populateApplicationData(appDocument);

                    // Now, load general volunteer details from the 'users' collection
                    loadVolunteerUserDetails(applicationId); // applicationId is volunteer's UID
                } else {
                    Log.w(TAG, "EventApplication document not found: " + applicationRef.getPath());
                    Toast.makeText(ApplicationDetailActivity.this, "EventApplication details not found.", Toast.LENGTH_SHORT).show();
                    setLoadingState(false);
                    // Optionally finish activity or show error message permanently
                    finish();
                }
            } else {
                Log.e(TAG, "Error fetching application details: ", task.getException());
                Toast.makeText(ApplicationDetailActivity.this, "Failed to load application details.", Toast.LENGTH_SHORT).show();
                setLoadingState(false);
                finish();
            }
        });
    }

    private void populateApplicationData(DocumentSnapshot appDocument) {
        // --- Extract data from the application document ---
        String volunteerNameInApp = appDocument.getString("volunteerName"); // Assuming 'volunteerName' is stored in application
        Timestamp appTimestamp = appDocument.getTimestamp("applicationTimestamp");
        String status = appDocument.getString("status");
        String coverLetter = appDocument.getString("coverLetter"); // Example field

        // --- Set application-specific views ---
        // If volunteerName is in the application document, use it. Otherwise, it will be set by loadVolunteerUserDetails
        if (volunteerNameInApp != null && !volunteerNameInApp.isEmpty()) {
            textViewVolunteerName.setText(volunteerNameInApp);
        }

        if (appTimestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
            textViewApplicationDate.setText(sdf.format(appTimestamp.toDate()));
        } else {
            textViewApplicationDate.setText("N/A");
        }

        textViewApplicationStatus.setText(status != null ? capitalize(status) : "N/A");
        textViewCoverLetter.setText(coverLetter != null ? coverLetter : "No cover letter provided.");

        // Control visibility of action buttons based on status
        if ("pending".equalsIgnoreCase(status)) {
            buttonAcceptApplication.setVisibility(View.VISIBLE);
            buttonRejectApplication.setVisibility(View.VISIBLE);
        } else {
            buttonAcceptApplication.setVisibility(View.GONE);
            buttonRejectApplication.setVisibility(View.GONE);
        }
    }

    private void loadVolunteerUserDetails(String volunteerUid) {
        DocumentReference userRef = db.collection(USERS_COLLECTION).document(volunteerUid);
        userRef.get().addOnCompleteListener(task -> {
            setLoadingState(false); // All data loading attempts are now complete

            if (task.isSuccessful()) {
                DocumentSnapshot userDocument = task.getResult();
                if (userDocument != null && userDocument.exists()) {
                    // Populate user-specific data
                    String username = userDocument.getString("username"); // Or "name" or "fullName"
                    String email = userDocument.getString("email");       // Or "mail" as in TransactActivity
                    String phone = userDocument.getString("phone");       // Or "phno"
                    String profileImageUrl = userDocument.getString("profileImageUrl");

                    if (textViewVolunteerName.getText().toString().isEmpty() && username != null) {
                        textViewVolunteerName.setText(username); // Set if not already set by application data
                    }
                    textViewVolunteerEmail.setText(email != null ? email : "N/A");
                    textViewVolunteerPhone.setText(phone != null ? phone : "N/A");

                    // Load profile image using Glide or Picasso
                    // if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                    //     Glide.with(this).load(profileImageUrl).placeholder(R.drawable.ic_profile_placeholder).into(imageViewVolunteerProfile);
                    // } else {
                    //     imageViewVolunteerProfile.setImageResource(R.drawable.ic_profile_placeholder);
                    // }

                } else {
                    Log.w(TAG, "Volunteer user document not found: " + userRef.getPath());
                    // EventApplication data is already loaded, so we don't necessarily show a fatal error here
                    // Just means extra user details are unavailable
                    if (textViewVolunteerName.getText().toString().isEmpty()) {
                        textViewVolunteerName.setText("Volunteer (Details N/A)");
                    }
                    textViewVolunteerEmail.setText("N/A");
                    textViewVolunteerPhone.setText("N/A");
                }
            } else {
                Log.e(TAG, "Error fetching volunteer user details: ", task.getException());
                // Handle partial data display scenario
                Toast.makeText(ApplicationDetailActivity.this, "Could not load full volunteer details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateApplicationStatus(String newStatus) {
        setLoadingState(true); // Disable buttons and show progress
        buttonAcceptApplication.setEnabled(false);
        buttonRejectApplication.setEnabled(false);

        DocumentReference applicationRef = db.collection(JOB_POSTINGS_COLLECTION)
                .document(jobPostingId)
                .collection(APPLICATIONS_SUBCOLLECTION)
                .document(applicationId);

        applicationRef.update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    setLoadingState(false);
                    Toast.makeText(ApplicationDetailActivity.this, "EventApplication " + newStatus + "!", Toast.LENGTH_SHORT).show();
                    textViewApplicationStatus.setText(capitalize(newStatus));

                    // Update button visibility after status change
                    if ("pending".equalsIgnoreCase(newStatus)) {
                        buttonAcceptApplication.setVisibility(View.VISIBLE);
                        buttonRejectApplication.setVisibility(View.VISIBLE);
                        buttonAcceptApplication.setEnabled(true);
                        buttonRejectApplication.setEnabled(true);
                    } else {
                        buttonAcceptApplication.setVisibility(View.GONE);
                        buttonRejectApplication.setVisibility(View.GONE);
                    }
                    // Optionally, you might want to send a notification to the volunteer here
                })
                .addOnFailureListener(e -> {
                    setLoadingState(false);
                    buttonAcceptApplication.setEnabled(true); // Re-enable on failure if still pending
                    buttonRejectApplication.setEnabled(true);
                    Log.e(TAG, "Error updating application status", e);
                    Toast.makeText(ApplicationDetailActivity.this, "Failed to update status.", Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            progressBarDetail.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE); // Hide content while loading new data
        } else {
            progressBarDetail.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE); // Show content once loaded
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Go back to the previous activity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}