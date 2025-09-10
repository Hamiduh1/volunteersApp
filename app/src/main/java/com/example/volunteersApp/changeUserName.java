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
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions; // For merging data

import java.util.HashMap;
import java.util.Map;
// Removed: import com.google.firebase.database.DatabaseReference;
// Removed: import com.google.firebase.database.FirebaseDatabase;
// Removed: import java.util.Objects;

public class changeUserName extends AppCompatActivity {

    private static final String TAG = "ChangeUserNameActivity";

    private EditText editTextNewUserName;
    private EditText editTextConfirmUserName;
    private Button buttonSubmitUserName;
    private ProgressBar progressBarUserName;

    // Firestore
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Firestore constants
    private static final String USERS_COLLECTION = "users"; // Or your specific collection name
    private static final String NAME_FIELD = "name";       // Or "displayName", "username" etc.

    public changeUserName() {
        // Default constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username); // Ensure this layout is updated

        Toolbar toolbar = findViewById(R.id.toolbar_change_username); // Add to your XML
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Update User Name");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editTextNewUserName = findViewById(R.id.editTextNewUserName);         // Update ID in XML
        editTextConfirmUserName = findViewById(R.id.editTextConfirmUserName); // Update ID in XML
        buttonSubmitUserName = findViewById(R.id.buttonSubmitUserName);     // Update ID in XML
        progressBarUserName = findViewById(R.id.progressBarUserName);     // Add to XML

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        buttonSubmitUserName.setOnClickListener(v -> attemptUserNameUpdate());
    }

    private void attemptUserNameUpdate() {
        String newUserName = editTextNewUserName.getText().toString().trim();
        String confirmUserName = editTextConfirmUserName.getText().toString().trim();

        if (TextUtils.isEmpty(newUserName)) {
            editTextNewUserName.setError("User name cannot be empty.");
            editTextNewUserName.requestFocus();
            return;
        }
        // Add any other specific validation for username (e.g., length, characters)
        if (newUserName.length() < 3) {
            editTextNewUserName.setError("User name must be at least 3 characters.");
            editTextNewUserName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(confirmUserName)) {
            editTextConfirmUserName.setError("Confirm user name.");
            editTextConfirmUserName.requestFocus();
            return;
        }

        if (!newUserName.equals(confirmUserName)) {
            editTextConfirmUserName.setError("User names do not match.");
            editTextConfirmUserName.requestFocus();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(changeUserName.this, "No user logged in. Please re-login.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(changeUserName.this, LoginActivity.class));
            finishAffinity();
            return;
        }

        String userId = currentUser.getUid();
        setLoading(true);

        Map<String, Object> userData = new HashMap<>();
        userData.put(NAME_FIELD, newUserName);
        // If you also want to update the Firebase Auth display name (optional):
        // updateUserProfileInAuth(currentUser, newUserName);

        DocumentReference userDocRef = db.collection(USERS_COLLECTION).document(userId);

        userDocRef.set(userData, SetOptions.merge()) // Or .update(NAME_FIELD, newUserName)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Log.d(TAG, "User name updated successfully in Firestore for UID: " + userId);
                    Toast.makeText(changeUserName.this, "User name changed successfully!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(changeUserName.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Error updating user name in Firestore for UID: " + userId, e);
                    Toast.makeText(changeUserName.this, "Failed to change user name: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /*
    // Optional: Also update the display name in Firebase Authentication
    private void updateUserProfileInAuth(FirebaseUser user, String newName) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                // .setPhotoUri(Uri.parse("some_uri_if_you_have_one")) // Example for photo
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User profile display name updated in Firebase Auth.");
                    } else {
                        Log.w(TAG, "Failed to update user profile display name in Firebase Auth.", task.getException());
                        // Don't necessarily show a user-facing error here unless it's critical,
                        // as the Firestore update is the primary goal for this screen.
                    }
                });
    }
    */

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            buttonSubmitUserName.setVisibility(View.GONE);
            progressBarUserName.setVisibility(View.VISIBLE);
        } else {
            buttonSubmitUserName.setVisibility(View.VISIBLE);
            progressBarUserName.setVisibility(View.GONE);
        }
        editTextNewUserName.setEnabled(!isLoading);
        editTextConfirmUserName.setEnabled(!isLoading);
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