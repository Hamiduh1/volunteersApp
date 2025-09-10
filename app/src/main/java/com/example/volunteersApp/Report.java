package com.example.volunteersApp;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull; // Added
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // Added
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar; // Added for loading indicator
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener; // Added
import com.google.android.gms.tasks.OnSuccessListener; // Added
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser; // Added
import com.google.firebase.firestore.DocumentReference; // Added
import com.google.firebase.firestore.FirebaseFirestore; // Added
import com.example.volunteersApp.R;
import com.example.volunteersApp.models.UserReport;


import java.util.Objects;

public class Report extends AppCompatActivity {

    private static final String TAG = "ReportActivity"; // Added for logging
    private static final String REPORTS_COLLECTION = "user_reports"; // Firestore collection name

    // Views
    private EditText editTextReportedName, editTextEvent, editTextReason, editTextReportedEmailOptional;
    private CheckBox checkBoxIncludeEmail;
    private Button buttonSubmitReport;
    private ProgressBar progressBarReport; // Added

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    // Data
    // Removed old class member variables for report data as they are locally scoped now

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report); // Ensure this layout has a ProgressBar (e.g., progressBarReport)

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Setup Toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar); // Ensure your layout has a Toolbar with this ID
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Report User/Event"); // More descriptive title
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize Views
        editTextReportedName = findViewById(R.id.reportName);
        editTextEvent = findViewById(R.id.reportevent);
        editTextReason = findViewById(R.id.reportreason);
        buttonSubmitReport = findViewById(R.id.reportsubmit);
        checkBoxIncludeEmail = findViewById(R.id.box);
        editTextReportedEmailOptional = findViewById(R.id.reportemail);
        progressBarReport = findViewById(R.id.progressBarReport); // Make sure you add a ProgressBar to your activity_report.xml

        // Initial state for optional email
        editTextReportedEmailOptional.setVisibility(View.GONE);
        if (progressBarReport != null) progressBarReport.setVisibility(View.GONE); // Initially hide progress bar


        checkBoxIncludeEmail.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                editTextReportedEmailOptional.setVisibility(View.VISIBLE);
            } else {
                editTextReportedEmailOptional.setVisibility(View.GONE);
                editTextReportedEmailOptional.setText(""); // Clear if unchecked
                editTextReportedEmailOptional.setError(null); // Clear error if any
            }
        });

        buttonSubmitReport.setOnClickListener(v -> {
            if (currentUser == null) {
                Toast.makeText(Report.this, "You must be logged in to submit a report.", Toast.LENGTH_LONG).show();
                // Optionally, redirect to login:
                // startActivity(new Intent(Report.this, LoginActivity.class));
                // finish();
                return;
            }
            validateAndSubmitReport();
        });
    }

    private void validateAndSubmitReport() {
        String reportedName = editTextReportedName.getText().toString().trim();
        String event = editTextEvent.getText().toString().trim();
        String reason = editTextReason.getText().toString().trim();
        String reportedEmail = "";

        boolean isValid = true;

        if (TextUtils.isEmpty(reportedName)) {
            editTextReportedName.setError("Enter Name of person/entity being reported");
            isValid = false;
        }
        if (TextUtils.isEmpty(event)) {
            editTextEvent.setError("Enter Event/Context name");
            isValid = false;
        }
        if (TextUtils.isEmpty(reason)) {
            editTextReason.setError("Enter Reason for report");
            isValid = false;
        }

        if (checkBoxIncludeEmail.isChecked()) {
            reportedEmail = editTextReportedEmailOptional.getText().toString().trim();
            if (TextUtils.isEmpty(reportedEmail)) {
                editTextReportedEmailOptional.setError("Enter Email of person being reported");
                isValid = false;
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(reportedEmail).matches()) {
                editTextReportedEmailOptional.setError("Enter a valid Email address");
                isValid = false;
            }
        }

        if (isValid) {
            String reportingUserDisplayName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Anonymous";
            String reportingUserId = currentUser.getUid();

            UserReport userReport = new UserReport(
                    reportedName,
                    checkBoxIncludeEmail.isChecked() ? reportedEmail : null, // Store null if not provided
                    event,
                    reason,
                    reportingUserDisplayName,
                    reportingUserId
            );

            saveReportToFirestore(userReport);
            // Optionally, still send the email. You might want to do this
            // only after Firestore save is successful or regardless.
            // For now, let's keep it sequential.
            sendReportEmail(reportedName);
        }
    }

    private void saveReportToFirestore(UserReport reportData) {
        if (progressBarReport != null) progressBarReport.setVisibility(View.VISIBLE);
        buttonSubmitReport.setEnabled(false); // Disable button during save

        db.collection(REPORTS_COLLECTION)
                .add(reportData) // .add() creates a document with an auto-generated ID
                .addOnSuccessListener(documentReference -> {
                    if (progressBarReport != null) progressBarReport.setVisibility(View.GONE);
                    buttonSubmitReport.setEnabled(true);
                    Log.d(TAG, "Report submitted successfully to Firestore with ID: " + documentReference.getId());
                    Toast.makeText(Report.this, "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                    // Navigate away or clear form after successful submission
                    // Example: finish(); or clearFormFields();
                    finish(); // Close the report activity
                })
                .addOnFailureListener(e -> {
                    if (progressBarReport != null) progressBarReport.setVisibility(View.GONE);
                    buttonSubmitReport.setEnabled(true);
                    Log.e(TAG, "Error submitting report to Firestore", e);
                    Toast.makeText(Report.this, "Failed to submit report. Please try again. " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void sendReportEmail(String reportedUserNameForEmail) {
        // This is your existing email logic
        // Consider making the email content more dynamic based on the report data if needed
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:")); // Only email apps should handle this
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"YOUR_ADMIN_EMAIL@example.com"}); // REPLACE WITH YOUR ACTUAL ADMIN EMAIL
        i.putExtra(Intent.EXTRA_SUBJECT, "Volunteer App - User Report: " + reportedUserNameForEmail);

        String emailBody = "Dear Admin Team,\n\n" +
                "A report has been submitted regarding:\n" +
                "User/Entity: " + editTextReportedName.getText().toString() + "\n" +
                "Event/Context: " + editTextEvent.getText().toString() + "\n" +
                "Reason: " + editTextReason.getText().toString() + "\n";

        if (checkBoxIncludeEmail.isChecked() && !TextUtils.isEmpty(editTextReportedEmailOptional.getText().toString())) {
            emailBody += "Reported User's Email (Optional): " + editTextReportedEmailOptional.getText().toString() + "\n";
        }

        FirebaseUser reporter = FirebaseAuth.getInstance().getCurrentUser();
        if (reporter != null) {
            emailBody += "\nReport submitted by: " +
                    (reporter.getDisplayName() != null ? reporter.getDisplayName() : "N/A") +
                    " (UID: " + reporter.getUid() + ")\n";
        }
        emailBody += "\nPlease review this report in the app's admin panel or Firestore 'user_reports' collection.\n\n" +
                "Regards,\nVolunteer App System";

        i.putExtra(Intent.EXTRA_TEXT, emailBody);

        try {
            startActivity(Intent.createChooser(i, "Send Report Email Via..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(Report.this, "No email app found to send report.", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(Report.this, "Unexpected error preparing email: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
        // No longer navigating to MainActivity here, as Firestore success handles it.
        // Toast.makeText(Report.this,"Reported Successfully",Toast.LENGTH_SHORT).show(); // Handled by Firestore success
    }


    // Handle the Up button in the toolbar
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Default behavior: acts like the back button
        return true;
    }

    // Removed getDisplayname() and setDisplayname() as they are not used with the current approach.
    // The reporting user's display name is fetched directly from currentUser.
}