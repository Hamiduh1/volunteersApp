package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Added

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions; // For merging data if needed

import java.util.HashMap;
import java.util.Map;
// Removed: import com.google.firebase.database.DatabaseReference;
// Removed: import com.google.firebase.database.FirebaseDatabase;
// Removed: import java.util.Objects; // Not strictly needed for mAuth.getUid() if user is checked

public class ChangePhoneNumber extends AppCompatActivity {

    private static final String TAG = "ChangePhoneNumber";

    private EditText editTextNewPhoneNumber;
    private EditText editTextConfirmPhoneNumber;
    private Button buttonSubmitPhoneNumber;
    private ProgressBar progressBarPhoneNumber; // Added

    // Firestore
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Firestore constants
    private static final String USERS_COLLECTION = "users";
    private static final String PHONE_FIELD = "phone"; // Or "phoneNumber", "userPhone" etc.

    // Default constructor - Keep if needed for any specific reason, though Activities
    // are typically instantiated by the Android system.
    public ChangePhoneNumber() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_phone_number); // Ensure this layout is updated

        Toolbar toolbar = findViewById(R.id.toolbar_change_phone_number); // Add to your XML
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Update Phone Number");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editTextNewPhoneNumber = findViewById(R.id.editTextNewPhoneNumber);       // Update ID in XML
        editTextConfirmPhoneNumber = findViewById(R.id.editTextConfirmPhoneNumber); // Update ID in XML
        buttonSubmitPhoneNumber = findViewById(R.id.buttonSubmitPhoneNumber);   // Update ID in XML
        progressBarPhoneNumber = findViewById(R.id.progressBarPhoneNumber);   // Add to XML

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        buttonSubmitPhoneNumber.setOnClickListener(v -> attemptPhoneNumberUpdate());
    }

    private void attemptPhoneNumberUpdate() {
        String newPhoneNumber = editTextNewPhoneNumber.getText().toString().trim();
        String confirmPhoneNumber = editTextConfirmPhoneNumber.getText().toString().trim();

        if (TextUtils.isEmpty(newPhoneNumber)) {
            editTextNewPhoneNumber.setError("New phone number is required.");
            editTextNewPhoneNumber.requestFocus();
            return;
        }
        // Basic validation for 10 digits. You might want more sophisticated validation.
        if (newPhoneNumber.length() != 10 || !TextUtils.isDigitsOnly(newPhoneNumber)) {
            editTextNewPhoneNumber.setError("Enter a valid 10-digit phone number.");
            editTextNewPhoneNumber.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(confirmPhoneNumber)) {
            editTextConfirmPhoneNumber.setError("Confirm phone number.");
            editTextConfirmPhoneNumber.requestFocus();
            return;
        }

        if (!newPhoneNumber.equals(confirmPhoneNumber)) {
            editTextConfirmPhoneNumber.setError("Phone numbers do not match.");
            editTextConfirmPhoneNumber.requestFocus();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(ChangePhoneNumber.this, "No user logged in. Please re-login.", Toast.LENGTH_LONG).show();
            // Redirect to login or finish activity
            startActivity(new Intent(ChangePhoneNumber.this, LoginActivity.class));
            finishAffinity();
            return;
        }

        String userId = currentUser.getUid();
        setLoading(true);

        // Create a Map or a POJO to update the phone number in Firestore
        Map<String, Object> userData = new HashMap<>();
        userData.put(PHONE_FIELD, newPhoneNumber);
        // You could add other fields to update at the same time if needed
        // e.g., userData.put("phoneLastUpdatedAt", FieldValue.serverTimestamp());

        DocumentReference userDocRef = db.collection(USERS_COLLECTION).document(userId);

        // Using .set(userData, SetOptions.merge()) will create the document if it doesn't exist,
        // or update/add the 'phone' field if it does exist without overwriting other fields.
        // If you are certain the document always exists, you can use .update(PHONE_FIELD, newPhoneNumber)
        userDocRef.set(userData, SetOptions.merge()) // Or .update(PHONE_FIELD, newPhoneNumber)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Log.d(TAG, "User phone number updated successfully in Firestore for UID: " + userId);
                    Toast.makeText(ChangePhoneNumber.this, "Phone number changed successfully!", Toast.LENGTH_SHORT).show();

                    // Navigate back to MainActivity or a profile screen
                    Intent intent = new Intent(ChangePhoneNumber.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Error updating phone number in Firestore for UID: " + userId, e);
                    Toast.makeText(ChangePhoneNumber.this, "Failed to change phone number: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            buttonSubmitPhoneNumber.setVisibility(View.GONE);
            progressBarPhoneNumber.setVisibility(View.VISIBLE);
        } else {
            buttonSubmitPhoneNumber.setVisibility(View.VISIBLE);
            progressBarPhoneNumber.setVisibility(View.GONE);
        }
        editTextNewPhoneNumber.setEnabled(!isLoading);
        editTextConfirmPhoneNumber.setEnabled(!isLoading);
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