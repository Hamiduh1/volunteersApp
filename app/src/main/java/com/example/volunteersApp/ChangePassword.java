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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Added

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;


public class ChangePassword   extends AppCompatActivity {

    private static final String TAG = "ChangePasswordActivity";

    private EditText editTextOldPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmNewPassword;
    private Button buttonChangePassword;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password); // Ensure you have this layout

        Toolbar toolbar = findViewById(R.id.toolbar_activity_change_password); // Add to your XML
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Change Password");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editTextOldPassword = findViewById(R.id.editTextChangeOldPassword); // Update IDs in your XML
        editTextNewPassword = findViewById(R.id.editTextChangeNewPassword);
        editTextConfirmNewPassword = findViewById(R.id.editTextChangeConfirmPassword);
        buttonChangePassword = findViewById(R.id.buttonSubmitChangePassword);
        progressBar = findViewById(R.id.progressBarChangePassword); // Add to your XML

        mAuth = FirebaseAuth.getInstance();

        buttonChangePassword.setOnClickListener(v -> attemptPasswordChange());
    }

    private void attemptPasswordChange() {
        String oldPassword = editTextOldPassword.getText().toString().trim();
        String newPassword = editTextNewPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmNewPassword.getText().toString().trim();

        if (TextUtils.isEmpty(oldPassword)) {
            editTextOldPassword.setError("Old password is required.");
            editTextOldPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(newPassword)) {
            editTextNewPassword.setError("New password is required.");
            editTextNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) { // Firebase default minimum
            editTextNewPassword.setError("Password must be at least 6 characters.");
            editTextNewPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            editTextConfirmNewPassword.setError("Confirm new password.");
            editTextConfirmNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            editTextConfirmNewPassword.setError("Passwords do not match.");
            editTextConfirmNewPassword.requestFocus();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(ChangePassword.this, "No user logged in. Please re-login.", Toast.LENGTH_LONG).show();
            // Redirect to login or finish activity
            startActivity(new Intent(ChangePassword.this, LoginActivity.class));
            finishAffinity(); // Finishes this and all parent activities
            return;
        }

        String email = currentUser.getEmail();
        if (email == null) {
            Toast.makeText(ChangePassword.this, "User email not found. Cannot change password.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        // Get auth credentials from the user for re-authentication
        AuthCredential credential = EmailAuthProvider.getCredential(email, oldPassword);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(reauthTask -> {
                    if (reauthTask.isSuccessful()) {
                        Log.d(TAG, "User re-authenticated successfully.");
                        currentUser.updatePassword(newPassword)
                                .addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        Log.d(TAG, "User password updated successfully.");
                                        Toast.makeText(ChangePassword.this, "Password changed successfully. Please log in with your new password.", Toast.LENGTH_LONG).show();
                                        setLoading(false);
                                        // Optional: Sign out the user
                                        // mAuth.signOut();
                                        Intent intent = new Intent(ChangePassword.this, LoginActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        setLoading(false);
                                        String errorMessage = "Failed to update password.";
                                        try {
                                            throw updateTask.getException();
                                        } catch (FirebaseAuthWeakPasswordException e) {
                                            errorMessage = "New password is too weak.";
                                            editTextNewPassword.setError(errorMessage);
                                            editTextNewPassword.requestFocus();
                                        } catch (Exception e) {
                                            errorMessage = e.getMessage() != null ? e.getMessage() : errorMessage;
                                            Log.e(TAG, "Error updating password", e);
                                        }
                                        Toast.makeText(ChangePassword.this, errorMessage, Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        setLoading(false);
                        String errorMessage = "Re-authentication failed. Incorrect old password or another issue.";
                        try {
                            throw reauthTask.getException();
                        } catch (FirebaseAuthInvalidCredentialsException e) {
                            errorMessage = "Incorrect old password.";
                            editTextOldPassword.setError(errorMessage);
                            editTextOldPassword.requestFocus();
                        } catch (Exception e) {
                            errorMessage = e.getMessage() != null ? e.getMessage() : errorMessage;
                            Log.e(TAG, "Error re-authenticating", e);
                        }
                        Toast.makeText(ChangePassword.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            buttonChangePassword.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            buttonChangePassword.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }
        editTextOldPassword.setEnabled(!isLoading);
        editTextNewPassword.setEnabled(!isLoading);
        editTextConfirmNewPassword.setEnabled(!isLoading);
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