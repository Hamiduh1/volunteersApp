package com.example.volunteersApp;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod; // For scrollable TextView
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar; // For loading indicator
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // If you have a toolbar

// Firestore Imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.Objects;

public class TermsConditionsActivity extends AppCompatActivity {

    private static final String TAG = "TermsConditionsActivity";
    private static final String CONFIG_COLLECTION = "app_config"; // Your Firestore collection
    private static final String TERMS_DOC_ID = "terms_and_conditions"; // Your Firestore document ID

    private TextView textViewTerms;
    private ProgressBar progressBarTerms;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_conditions); // Ensure this layout has a TextView and ProgressBar

        // Initialize Firebase Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize Views (make sure these IDs are in your activity_terms_conditions.xml)
        textViewTerms = findViewById(R.id.textViewTermsContent);
        progressBarTerms = findViewById(R.id.progressBarTerms);
        Toolbar toolbar = findViewById(R.id.toolbar_terms); // Assuming you have a toolbar

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Terms & Conditions");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Make TextView scrollable if content is long
        if (textViewTerms != null) {
            textViewTerms.setMovementMethod(new ScrollingMovementMethod());
        }

        loadTermsAndConditions();
    }

    private void loadTermsAndConditions() {
        if (progressBarTerms != null) {
            progressBarTerms.setVisibility(View.VISIBLE);
        }
        if (textViewTerms != null) {
            textViewTerms.setVisibility(View.GONE); // Hide text view while loading
        }


        DocumentReference termsDocRef = db.collection(CONFIG_COLLECTION).document(TERMS_DOC_ID);

        termsDocRef.get().addOnCompleteListener(task -> {
            if (progressBarTerms != null) {
                progressBarTerms.setVisibility(View.GONE);
            }
            if (textViewTerms != null) {
                textViewTerms.setVisibility(View.VISIBLE);
            }

            if (task.isSuccessful()) {
                if (task.getResult() != null && task.getResult().exists()) {
                    // Assuming your terms are stored in a field called "text"
                    String termsText = task.getResult().getString("text");
                    if (termsText != null && !termsText.isEmpty()) {
                        if (textViewTerms != null) {
                            textViewTerms.setText(termsText);
                        }
                    } else {
                        Log.w(TAG, "Terms text field is missing or empty in the document.");
                        if (textViewTerms != null) {
                            textViewTerms.setText("Terms and Conditions are currently unavailable.");
                        }
                        Toast.makeText(TermsConditionsActivity.this, "Could not load terms.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "Terms document does not exist: " + CONFIG_COLLECTION + "/" + TERMS_DOC_ID);
                    if (textViewTerms != null) {
                        textViewTerms.setText("Terms and Conditions not found.");
                    }
                    Toast.makeText(TermsConditionsActivity.this, "Terms not found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Error fetching terms from Firestore", task.getException());
                if (textViewTerms != null) {
                    textViewTerms.setText("Failed to load Terms and Conditions. Please check your internet connection.");
                }
                if (task.getException() instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException e = (FirebaseFirestoreException) task.getException();
                    // Handle specific Firestore errors if needed
                    Toast.makeText(TermsConditionsActivity.this, "Error: " + e.getCode(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(TermsConditionsActivity.this, "An error occurred.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Go back to the previous activity
        return true;
    }
}