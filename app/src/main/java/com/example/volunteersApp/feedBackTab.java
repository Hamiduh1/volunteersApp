package com.example.volunteersApp;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // For logging
import android.view.MenuItem; // For handling home button
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Correct import

// Import the generated binding class
import com.example.volunteersApp.databinding.ActivityFeedbacktabBinding; // Change if your layout file name is different

public class feedBackTab extends AppCompatActivity {

    private static final String TAG = "feedBackTab"; // For logging
    private ActivityFeedbacktabBinding binding; // Declare binding variable

    // Default constructor (often not strictly needed if no other constructors are defined)
    public feedBackTab() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using View Binding
        binding = ActivityFeedbacktabBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup Toolbar
        Toolbar toolbar = binding.toolbar; // Access toolbar via binding
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Feedback");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.feedbacksubmit.setOnClickListener(v -> {
            validateAndSendEmail();
        });
    }

    private void validateAndSendEmail() {
        // Get text from EditTexts using binding
        String name = binding.feedbackname.getText().toString().trim();
        String email = binding.feedbackemail.getText().toString().trim();
        String subject = binding.feedbacksubject.getText().toString().trim();
        String suggestion = binding.feedbacksuggestions.getText().toString().trim();

        boolean isValid = true; // Flag to track validation status

        if (TextUtils.isEmpty(name)) {
            binding.feedbackname.setError("Enter Name");
            isValid = false;
        } else {
            binding.feedbackname.setError(null); // Clear error if valid
        }

        if (TextUtils.isEmpty(email)) {
            binding.feedbackemail.setError("Enter Email");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // Basic email pattern check
            binding.feedbackemail.setError("Enter a valid Email");
            isValid = false;
        }
        else {
            binding.feedbackemail.setError(null);
        }

        if (TextUtils.isEmpty(subject)) {
            binding.feedbacksubject.setError("Enter Subject");
            isValid = false;
        } else {
            binding.feedbacksubject.setError(null);
        }

        if (TextUtils.isEmpty(suggestion)) {
            binding.feedbacksuggestions.setError("Enter Suggestion");
            isValid = false;
        } else {
            binding.feedbacksuggestions.setError(null);
        }

        if (isValid) {
            // All fields are valid, proceed to send email
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:")); // Only email apps should handle this
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"hemanthsai392@gmail.com"}); // Recipient
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT,
                    "Dear Binary Warriors,\n\n" +
                            suggestion +
                            "\n\nRegards,\n" +
                            name + "\n" + // Include name in the body
                            "Email: " + email); // And email for reference

            try {
                // Using Intent.createChooser is a good practice as it always shows the user a choice
                // if multiple email apps are available, and gracefully handles if none are.
                startActivity(Intent.createChooser(emailIntent, "Send feedback using..."));
                Toast.makeText(feedBackTab.this, "Please select an email app to send feedback.", Toast.LENGTH_LONG).show();
                // Consider what to do after launching:
                // finish(); // Closes this activity after the email app is launched
            } catch (ActivityNotFoundException ex) {
                Log.e(TAG, "No email client installed.", ex);
                Toast.makeText(feedBackTab.this, "No email app found on your device.", Toast.LENGTH_LONG).show();
                // finish(); // Also consider finishing if no email app
            } catch (Exception ex) { // Catch any other unexpected errors
                Log.e(TAG, "Unexpected error when trying to send email.", ex);
                Toast.makeText(feedBackTab.this, "An unexpected error occurred. Please try again.", Toast.LENGTH_LONG).show();
                // finish();
            }
        } else {
            Toast.makeText(this, "Please correct the errors above.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here.
        if (item.getItemId() == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown.
            finish(); // Closes the current activity and goes to the previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}