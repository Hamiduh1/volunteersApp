package com.example.volunteersApp.auth; // Changed package

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.volunteersApp.R; // Ensure R is imported from your app's package
import com.example.volunteersApp.databinding.ActivityForgotPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class ForgotPasswordActivity extends AppCompatActivity { // Renamed class

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth mAuth;
    private static final String TAG = "ForgotPasswordActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        setupToolbar();

        binding.resetButton.setOnClickListener(v -> handlePasswordReset()); // Changed ID, see XML
    }

    private void setupToolbar() {
        Toolbar toolbar = binding.forgotPasswordToolbar; // Changed ID, see XML
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.title_forgot_password)); // Use string resource
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void handlePasswordReset() {
        String email = binding.emailEditText.getText().toString().trim(); // Changed ID

        if (!validateEmail(email)) {
            return;
        }

        setLoadingState(true);
        binding.messageTextView.setText(""); // Clear previous messages

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoadingState(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Password reset email sent successfully to: " + email);
                        binding.messageTextView.setText(getString(R.string.info_reset_link_sent)); // Use string resource
                        Toast.makeText(ForgotPasswordActivity.this, getString(R.string.toast_reset_link_sent), Toast.LENGTH_LONG).show();
                    } else {
                        Log.e(TAG, "Error sending password reset email to: " + email, task.getException());
                        String errorMessage = getString(R.string.error_failed_to_send_reset_email);
                        if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                            errorMessage = getString(R.string.error_no_account_found_with_email);
                            binding.emailEditText.setError(errorMessage);
                            binding.emailEditText.requestFocus();
                        } else if (task.getException() != null) {
                            Log.e(TAG, "Specific error: " + task.getException().getMessage());
                            // Potentially append task.getException().getLocalizedMessage() for more detail
                        }
                        binding.messageTextView.setText(errorMessage);
                        Toast.makeText(ForgotPasswordActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            binding.emailEditText.setError(getString(R.string.error_email_required));
            binding.emailEditText.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.setError(getString(R.string.error_invalid_email_format));
            binding.emailEditText.requestFocus();
            return false;
        }
        binding.emailEditText.setError(null); // Clear error if valid
        return true;
    }

    private void setLoadingState(boolean isLoading) {
        binding.loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE); // Changed ID
        binding.resetButton.setEnabled(!isLoading);
        binding.emailEditText.setEnabled(!isLoading); // Also disable email field during loading
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
