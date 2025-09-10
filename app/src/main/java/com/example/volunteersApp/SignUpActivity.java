package com.example.volunteersApp; // Changed package

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.MenuItem; // Added for Toolbar back button
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Added for Toolbar

import com.example.volunteersApp.databinding.ActivitySignUpBinding;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest; // For setting display name
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SignUpActivity extends AppCompatActivity {

 private ActivitySignUpBinding binding;
 private FirebaseAuth mAuth;
 private FirebaseFirestore db;

 private String selectedUserType = ""; // Default to empty, validation will catch it

 private static final String TAG = "SignUpActivity";
 private static final String USER_TYPE_VOLUNTEER = "volunteer";
 private static final String USER_TYPE_ORGANIZER = "organizer";
 private static final String USER_TYPE_EMPLOYER = "employer"; // <-- ADD THIS NEW CONSTANT

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  binding = ActivitySignUpBinding.inflate(getLayoutInflater());
  setContentView(binding.getRoot());

  mAuth = FirebaseAuth.getInstance();
  db = FirebaseFirestore.getInstance();

  setupToolbar();
  setupRadioGroupListener();
  setupClickListeners();

  // Pre-select a default user type if desired, e.g., volunteer
  // binding.radioVolunteer.setChecked(true);
 }

 private void setupToolbar() {
  Toolbar toolbar = binding.signUpToolbar;
  setSupportActionBar(toolbar);
  if (getSupportActionBar() != null) {
   getSupportActionBar().setTitle(getString(R.string.title_sign_up));
   getSupportActionBar().setDisplayHomeAsUpEnabled(true);
   getSupportActionBar().setDisplayShowHomeEnabled(true);
  }
 }

 private void setupRadioGroupListener() {
  binding.userTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
   // It's good practice to use if-else if for mutually exclusive RadioButton checks
   if (checkedId == R.id.radioVolunteer) {
    selectedUserType = USER_TYPE_VOLUNTEER;
    // Hide employer/organizer specific fields, show volunteer specific
    // binding.employerSpecificLayout.setVisibility(View.GONE);
    // binding.organizerSpecificLayout.setVisibility(View.GONE);
    // binding.volunteerSpecificLayout.setVisibility(View.VISIBLE);
   } else if (checkedId == R.id.radioOrganizer) {
    selectedUserType = USER_TYPE_ORGANIZER;
    // Hide volunteer/employer specific fields, show organizer specific
    // binding.volunteerSpecificLayout.setVisibility(View.GONE);
    // binding.employerSpecificLayout.setVisibility(View.GONE);
    // binding.organizerSpecificLayout.setVisibility(View.VISIBLE);
   } else if (checkedId == R.id.radioEmployer) { // <-- ADD THIS CONDITION
    selectedUserType = USER_TYPE_EMPLOYER;
    // Hide volunteer/organizer specific fields, show employer specific
    // binding.volunteerSpecificLayout.setVisibility(View.GONE);
    // binding.organizerSpecificLayout.setVisibility(View.GONE);
    // binding.employerSpecificLayout.setVisibility(View.VISIBLE);
   } else {
    selectedUserType = ""; // Should not happen if a radio button is always selected after initial
   }
   Log.d(TAG, "Selected user type: " + selectedUserType);
  });
 }

 private void setupClickListeners() {
  binding.registerButton.setOnClickListener(v -> handleRegistration());

  binding.loginTextView.setOnClickListener(v -> {
   Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
   startActivity(intent);
   finish();
  });
 }

 private void handleRegistration() {
  String username = Objects.requireNonNull(binding.usernameEditText.getText()).toString().trim();
  String phone = Objects.requireNonNull(binding.phoneEditText.getText()).toString().trim();
  String email = Objects.requireNonNull(binding.emailEditText.getText()).toString().trim();
  String password = Objects.requireNonNull(binding.passwordEditText.getText()).toString().trim();
  String confirmPassword = Objects.requireNonNull(binding.confirmPasswordEditText.getText()).toString().trim();

  // Example: Add fields specific to employer if they exist in your XML
  // String companyName = "";
  // if (USER_TYPE_EMPLOYER.equals(selectedUserType)) {
  //     companyName = Objects.requireNonNull(binding.companyNameEditText.getText()).toString().trim();
  // }


  if (!validateInputs(username, phone, email, password, confirmPassword, selectedUserType /*, companyName - if added */)) {
   return;
  }

  if (!isNetworkConnected()) {
   Toast.makeText(SignUpActivity.this, getString(R.string.error_network_connection), Toast.LENGTH_LONG).show();
   return;
  }

  setLoadingState(true);

  mAuth.createUserWithEmailAndPassword(email, password)
          .addOnCompleteListener(this, authTask -> {
           if (authTask.isSuccessful()) {
            FirebaseUser firebaseUser = mAuth.getCurrentUser();
            if (firebaseUser != null) {
             updateUserProfile(firebaseUser, username, () -> {
              saveUserDetailsToFirestore(firebaseUser.getUid(), username, email, phone, selectedUserType /*, companyName */);
             });
            } else {
             setLoadingState(false);
             Log.e(TAG, "Firebase user is null after successful auth creation.");
             Toast.makeText(SignUpActivity.this, getString(R.string.error_internal), Toast.LENGTH_SHORT).show();
            }
           } else {
            setLoadingState(false);
            handleAuthFailure(authTask.getException());
           }
          });
 }

 private void updateUserProfile(FirebaseUser firebaseUser, String username, Runnable onComplete) {
  UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
          .setDisplayName(username)
          .build();

  firebaseUser.updateProfile(profileUpdates)
          .addOnCompleteListener(task -> {
           if (task.isSuccessful()) {
            Log.d(TAG, "User profile updated with display name: " + username);
           } else {
            Log.w(TAG, "Failed to update user profile display name.", task.getException());
           }
           if (onComplete != null) {
            onComplete.run();
           }
          });
 }


 private boolean validateInputs(String username, String phone, String email, String password, String confirmPassword, String userType /*, String companyName - if added */) {
  // ... (existing validations for username, phone, email, password, confirmPassword)

  // Username
  if (TextUtils.isEmpty(username)) {
   binding.usernameInputLayout.setError(getString(R.string.error_username_required));
   binding.usernameEditText.requestFocus();
   return false;
  } else {
   binding.usernameInputLayout.setError(null);
  }

  // Phone
  if (TextUtils.isEmpty(phone)) {
   binding.phoneInputLayout.setError(getString(R.string.error_phone_required));
   binding.phoneEditText.requestFocus();
   return false;
  } else if (phone.length() < 10) { // Basic validation, adjust as needed
   binding.phoneInputLayout.setError(getString(R.string.error_invalid_phone_number));
   binding.phoneEditText.requestFocus();
   return false;
  } else {
   binding.phoneInputLayout.setError(null);
  }

  // Email
  if (TextUtils.isEmpty(email)) {
   binding.emailInputLayout.setError(getString(R.string.error_email_required));
   binding.emailEditText.requestFocus();
   return false;
  } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
   binding.emailInputLayout.setError(getString(R.string.error_invalid_email_format));
   binding.emailEditText.requestFocus();
   return false;
  } else {
   binding.emailInputLayout.setError(null);
  }

  // Password
  if (TextUtils.isEmpty(password)) {
   binding.passwordInputLayout.setError(getString(R.string.error_password_required));
   binding.passwordEditText.requestFocus();
   return false;
  } else if (password.length() < 6) {
   binding.passwordInputLayout.setError(getString(R.string.error_password_too_short));
   binding.passwordEditText.requestFocus();
   return false;
  } else {
   binding.passwordInputLayout.setError(null);
  }

  // Confirm Password
  if (TextUtils.isEmpty(confirmPassword)) {
   binding.confirmPasswordInputLayout.setError(getString(R.string.error_confirm_password_required));
   binding.confirmPasswordEditText.requestFocus();
   return false;
  } else if (!password.equals(confirmPassword)) {
   binding.confirmPasswordInputLayout.setError(getString(R.string.error_passwords_do_not_match));
   binding.confirmPasswordEditText.requestFocus();
   binding.confirmPasswordEditText.setText("");
   return false;
  } else {
   binding.confirmPasswordInputLayout.setError(null);
  }

  // User Type
  if (TextUtils.isEmpty(userType)) {
   Toast.makeText(SignUpActivity.this, getString(R.string.error_select_user_type), Toast.LENGTH_SHORT).show();
   binding.userTypeRadioGroup.requestFocus(); // Focus the group
   return false;
  }

  // Employer Specific Validation (Example)
  // if (USER_TYPE_EMPLOYER.equals(userType)) {
  //     if (TextUtils.isEmpty(companyName)) {
  //         binding.companyNameInputLayout.setError("Company name is required for employers.");
  //         binding.companyNameEditText.requestFocus();
  //         return false;
  //     } else {
  //         binding.companyNameInputLayout.setError(null);
  //     }
  // }

  return true;
 }

 private void saveUserDetailsToFirestore(String userId, String username, String email, String phone, String userType /*, String companyName - if added */) {
  Map<String, Object> userData = new HashMap<>();
  userData.put("uid", userId);
  userData.put("username", username);
  userData.put("email", email.toLowerCase());
  userData.put("phoneNumber", phone);
  userData.put("userType", userType); // This will now correctly save "employer" too
  userData.put("createdAt", FieldValue.serverTimestamp());
  userData.put("profileStatus", "active");

  // if (USER_TYPE_EMPLOYER.equals(userType) && !TextUtils.isEmpty(companyName)) {
  //    userData.put("companyName", companyName);
  // }
  // Add other fields based on userType if needed (e.g., organizationName for organizers)

  Log.d(TAG, "Attempting to save user data to Firestore: " + userData.toString());

  db.collection("users").document(userId)
          .set(userData)
          .addOnCompleteListener(task -> {
           setLoadingState(false);
           if (task.isSuccessful()) {
            Log.d(TAG, "User data saved successfully to Firestore for user: " + userId);
            Toast.makeText(SignUpActivity.this, getString(R.string.toast_registration_successful), Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
           } else {
            Log.e(TAG, "Failed to save user data to Firestore for user: " + userId, task.getException());
            Toast.makeText(SignUpActivity.this, getString(R.string.error_failed_to_save_user_details), Toast.LENGTH_LONG).show();

            FirebaseUser currentAuthUser = mAuth.getCurrentUser();
            if (currentAuthUser != null && currentAuthUser.getUid().equals(userId)) {
             currentAuthUser.delete().addOnCompleteListener(deleteTask -> {
              if (deleteTask.isSuccessful()) {
               Log.d(TAG, "User account deleted from Auth due to Firestore write failure.");
              } else {
               Log.e(TAG, "CRITICAL: Failed to delete user account from Auth after Firestore write failure.", deleteTask.getException());
              }
             });
            }
           }
          });
 }

 private void handleAuthFailure(Exception exception) {
  // setLoadingState(false) is called by the caller
  String errorMessage;
  View focusView = null;

  if (exception instanceof FirebaseAuthWeakPasswordException) {
   errorMessage = getString(R.string.error_password_too_weak);
   binding.passwordInputLayout.setError(errorMessage);
   focusView = binding.passwordEditText;
  } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
   errorMessage = getString(R.string.error_invalid_email_format_auth);
   binding.emailInputLayout.setError(errorMessage);
   focusView = binding.emailEditText;
  } else if (exception instanceof FirebaseAuthUserCollisionException) {
   errorMessage = getString(R.string.error_email_already_registered);
   binding.emailInputLayout.setError(errorMessage);
   focusView = binding.emailEditText;
  } else if (exception instanceof FirebaseNetworkException) {
   errorMessage = getString(R.string.error_network_connection);
  } else {
   errorMessage = getString(R.string.error_registration_failed) + (exception != null ? ": " + exception.getLocalizedMessage() : "");
   Log.e(TAG, "createUserWithEmail:failure", exception);
  }

  Toast.makeText(SignUpActivity.this, errorMessage, Toast.LENGTH_LONG).show();
  if (focusView != null) {
   focusView.requestFocus();
  }
 }

 private boolean isNetworkConnected() {
  ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
  if (cm == null) {
   Log.e(TAG, "ConnectivityManager is null");
   return false;
  }
  NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
  return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
 }

 private void setLoadingState(boolean isLoading) {
  binding.loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
  binding.registerButton.setEnabled(!isLoading);
  binding.usernameEditText.setEnabled(!isLoading);
  binding.phoneEditText.setEnabled(!isLoading);
  binding.emailEditText.setEnabled(!isLoading);
  binding.passwordEditText.setEnabled(!isLoading);
  binding.confirmPasswordEditText.setEnabled(!isLoading);
  // binding.companyNameEditText.setEnabled(!isLoading); // If you add it
  // binding.organizationNameEditText.setEnabled(!isLoading); // If you add it

  for (int i = 0; i < binding.userTypeRadioGroup.getChildCount(); i++) {
   binding.userTypeRadioGroup.getChildAt(i).setEnabled(!isLoading);
  }
  binding.loginTextView.setEnabled(!isLoading);
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
