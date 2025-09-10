package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
// import androidx.appcompat.widget.Toolbar; // Uncomment if you add Toolbar back via binding

import com.example.volunteersApp.auth.ForgotPasswordActivity;
import com.example.volunteersApp.databinding.ActivityLoginBinding;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.example.volunteersApp.R;
import com.example.volunteersApp.MainActivity;
//import com.example.volunteersApp.employer.EmployerMainActivity;
import com.example.volunteersApp.organizer.OrganizerMainActivity;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final String TAG = "LoginActivity";

    // Define constants for user types to avoid typos
    private static final String USER_TYPE_VOLUNTEER = "volunteer";
    private static final String USER_TYPE_ORGANIZER = "organizer";
    private static final String USER_TYPE_EMPLOYER = "employer";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Toolbar setup (if you have one in your binding and want to use it)
        // Ensure binding.loginToolbar exists if you uncomment this
        // setSupportActionBar(binding.loginToolbar);
        // if (getSupportActionBar() != null) {
        //     getSupportActionBar().setTitle(getString(R.string.login_toolbar_title));
        // }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: " + currentUser.getUid() + ". Attempting to route.");
            showLoading(true);
            // We don't know the role they last selected, so we just fetch their stored role
            fetchUserTypeAndRoute(currentUser, null); // Pass null for selectedRoleUi for auto-login
        } else {
            Log.d(TAG, "No user signed in. Displaying login form.");
            showLoading(false);
        }

        binding.forgotPasswordTextView.setOnClickListener(v -> {
            Log.d(TAG, "Forgot Password clicked");
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
        });

        binding.loginButton.setOnClickListener(v -> {
            Log.d(TAG, "Login button clicked");
            validateAndLoginUser();
        });

        binding.signupTextView.setOnClickListener(v -> {
            Log.d(TAG, "Sign Up text clicked");
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
            // Consider passing the selected role to SignUpActivity if you want to pre-fill it there
            // Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
            // intent.putExtra("SELECTED_ROLE", getSelectedRole());
            // startActivity(intent);
        });

        addTextWatchers();
    }

    private void addTextWatchers() {
        TextWatcher errorClearer = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear errors on text change
                if (binding.emailInputLayout.isErrorEnabled()) {
                    binding.emailInputLayout.setError(null);
                }
                if (binding.passwordInputLayout.isErrorEnabled()) {
                    binding.passwordInputLayout.setError(null);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        binding.emailEditText.addTextChangedListener(errorClearer);
        binding.passwordEditText.addTextChangedListener(errorClearer);
    }

    private String getSelectedRole() {
        int selectedId = binding.userTypeRadioGroup.getCheckedRadioButtonId();
        if (selectedId == binding.radioVolunteer.getId()) {
            return USER_TYPE_VOLUNTEER;
        } else if (selectedId == binding.radioOrganizer.getId()) {
            return USER_TYPE_ORGANIZER;
        } else if (selectedId == binding.radioEmployer.getId()) {
            return USER_TYPE_EMPLOYER;
        }
        return null; // Should ideally not happen if one is always selected or validated
    }

    private void validateAndLoginUser() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        final String selectedRoleUi = getSelectedRole();

        boolean isValid = true;
        if (TextUtils.isEmpty(email)) {
            binding.emailInputLayout.setError(getString(R.string.error_email_required));
            binding.emailEditText.requestFocus();
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            binding.emailEditText.requestFocus();
            isValid = false;
        } else {
            binding.emailInputLayout.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            binding.passwordInputLayout.setError(getString(R.string.error_password_required));
            if (isValid) binding.passwordEditText.requestFocus(); // Only focus if email was valid
            isValid = false;
        } else {
            binding.passwordInputLayout.setError(null);
        }

        if (selectedRoleUi == null) { // Check if a role is selected
            Toast.makeText(this, getString(R.string.error_role_not_selected), Toast.LENGTH_LONG).show();
            // You might want to highlight the radio group or show a specific error message near it
            isValid = false;
        }

        if (!isValid) {
            Log.w(TAG, "Login validation failed.");
            return;
        }

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(LoginActivity.this, task -> {
                    // No need to call showLoading(false) here immediately if fetchUserTypeAndRoute handles it.
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            fetchUserTypeAndRoute(user, selectedRoleUi);
                        } else {
                            showLoading(false);
                            Log.e(TAG, "Firebase user is null after successful signInWithEmail.");
                            Toast.makeText(LoginActivity.this, getString(R.string.login_failed_user_data_unavailable), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        showLoading(false);
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        handleLoginFailure(task.getException());
                    }
                });
    }

    private void handleLoginFailure(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            binding.emailInputLayout.setError(getString(R.string.error_user_not_found));
            binding.emailEditText.requestFocus();
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            binding.passwordInputLayout.setError(getString(R.string.error_incorrect_password));
            binding.passwordEditText.requestFocus();
        } else if (exception instanceof FirebaseNetworkException) {
            Toast.makeText(LoginActivity.this, getString(R.string.error_network_issue), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(LoginActivity.this, getString(R.string.login_failed_generic) + (exception != null ? ": " + exception.getMessage() : ""), Toast.LENGTH_LONG).show();
            Log.e(TAG, "signInWithEmail:genericFailure", exception);
        }
    }

    /**
     * Fetches the user's type from Cloud Firestore and routes them.
     * If selectedRoleUi is null (e.g., auto-login), it skips the role mismatch check.
     */
    private void fetchUserTypeAndRoute(FirebaseUser user, @androidx.annotation.Nullable final String selectedRoleUi) {
        if (user == null) {
            Log.e(TAG, "fetchUserTypeAndRoute called with null user.");
            showLoading(false);
            return;
        }

        // showLoading(true) should have been called by the initiator
        DocumentReference userDocRef = db.collection("users").document(user.getUid());

        userDocRef.get().addOnCompleteListener(task -> {
            showLoading(false); // Hide loading indicator regardless of Firestore outcome
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    String firestoreUserType = document.getString("userType");

                    if (firestoreUserType != null) {
                        Log.d(TAG, "Firestore userType: " + firestoreUserType + ". Selected UI role (if any): " + selectedRoleUi);

                        // If selectedRoleUi is provided (not auto-login), check for mismatch
                        if (selectedRoleUi != null && !firestoreUserType.equals(selectedRoleUi)) {
                            Log.w(TAG, "Role mismatch. Firestore: " + firestoreUserType + ", UI Selection: " + selectedRoleUi);
                            Toast.makeText(LoginActivity.this, getString(R.string.error_role_mismatch), Toast.LENGTH_LONG).show();
                            mAuth.signOut(); // Sign out due to role mismatch
                            return;
                        }

                        // Proceed with routing based on the confirmed firestoreUserType
                        Toast.makeText(LoginActivity.this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();
                        Intent intent = null;
                        switch (firestoreUserType) {
                            case USER_TYPE_VOLUNTEER:
                                Log.d(TAG, "Routing to Volunteer Dashboard (MainActivity)");
                                intent = new Intent(LoginActivity.this, MainActivity.class); // Volunteer Home
                                break;
                            case USER_TYPE_ORGANIZER:
                                Log.d(TAG, "Routing to Organizer Dashboard (e.g., OrganizerMainActivity)");
                                intent = new Intent(LoginActivity.this, OrganizerMainActivity.class); // CHANGE TO YOUR OrganizerActivity
                                break;
                            case USER_TYPE_EMPLOYER:
                                Log.d(TAG, "Routing to Employer Dashboard (EmployerMainActivity)");
                                intent = new Intent(LoginActivity.this, EmployerMainActivity.class); // Employer Home
                                break;
                            default:
                                Log.w(TAG, "Unknown user type in Firestore: " + firestoreUserType + " for user: " + user.getUid());
                                Toast.makeText(LoginActivity.this, getString(R.string.error_unknown_user_type), Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                                return;
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Log.w(TAG, "User type field is missing or null in Firestore for user: " + user.getUid());
                        Toast.makeText(LoginActivity.this, getString(R.string.error_user_type_missing), Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                    }
                } else {
                    Log.w(TAG, "User document not found in Firestore for UID: " + user.getUid());
                    Toast.makeText(LoginActivity.this, getString(R.string.error_user_data_not_found), Toast.LENGTH_LONG).show();
                    mAuth.signOut(); // User in Auth but no data in Firestore
                }
            } else {
                Log.e(TAG, "Firestore get failed: ", task.getException());
                String errorMessage = getString(R.string.error_database_operation_failed);
                if (task.getException() instanceof FirebaseFirestoreException) {
                    errorMessage += ": " + ((FirebaseFirestoreException) task.getException()).getCode();
                } else if (task.getException() != null) {
                    errorMessage += ": " + task.getException().getMessage();
                }
                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                mAuth.signOut(); // Sign out on DB error
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (binding.loadingProgressBar != null) {
            binding.loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        binding.loginButton.setEnabled(!isLoading);
        binding.emailEditText.setEnabled(!isLoading);
        binding.passwordEditText.setEnabled(!isLoading);
        // Disable RadioGroup during loading
        binding.userTypeRadioGroup.setEnabled(!isLoading);
        for (int i = 0; i < binding.userTypeRadioGroup.getChildCount(); i++) {
            binding.userTypeRadioGroup.getChildAt(i).setEnabled(!isLoading);
        }
    }
}
