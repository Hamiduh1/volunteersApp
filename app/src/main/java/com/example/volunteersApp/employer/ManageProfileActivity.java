package com.example.volunteersApp.employer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
// androidx.core.content.ContextCompat; // For permissions if targeting older APIs (Keep if used)

// import android.Manifest; // Keep if you add camera permissions
import android.content.Intent;
// import android.content.pm.PackageManager; // Keep if you add camera permissions
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.ActivityManageProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore SDK imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions; // For merging data if needed
// Firebase Storage SDK imports (remain the same)
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
// import com.google.firebase.storage.UploadTask; // Keep if you need to inspect UploadTask details

import java.util.UUID;
// java.util.Date; // Import if you manually handle Date objects for Firestore timestamps

public class ManageProfileActivity extends AppCompatActivity {

    private static final String TAG = "ManageProfileActivity";
    private static final String EMPLOYERS_COLLECTION = "Employers"; // Firestore collection name

    private ActivityManageProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Firestore specific
    private FirebaseFirestore db;
    private DocumentReference employerProfileDocRef; // Reference to the employer's document

    // Firebase Storage (remains largely the same)
    private StorageReference storageProfileImagesRef;

    private Uri imageUri;
    private String currentProfileImageUrl = null;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    imageUri = result.getData().getData();
                    Glide.with(this).load(imageUri).circleCrop().into(binding.imageViewProfile);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.manage_organization_profile_title));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.user_not_logged_in_error), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        // DocumentReference for the current employer's profile
        // The document ID will be the user's UID for uniqueness
        employerProfileDocRef = db.collection(EMPLOYERS_COLLECTION).document(currentUser.getUid());

        // Initialize Firebase Storage (Path remains the same concept)
        storageProfileImagesRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images").child(currentUser.getUid());

        loadProfileData();

        binding.imageViewProfile.setOnClickListener(v -> openImagePicker());
        binding.fabChangeProfileImage.setOnClickListener(v -> openImagePicker());
        binding.buttonSaveChanges.setOnClickListener(v -> saveProfileChanges());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void loadProfileData() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.buttonSaveChanges.setEnabled(false);

        employerProfileDocRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSaveChanges.setEnabled(true);

                    if (documentSnapshot.exists()) {
                        EmployerProfile profile = documentSnapshot.toObject(EmployerProfile.class);
                        if (profile != null) {
                            // Use getters from your EmployerProfile model
                            binding.editTextOrganizationName.setText(profile.getOrganizationName() != null ? profile.getOrganizationName() : "");
                            binding.editTextContactEmail.setText(profile.getContactEmail() != null && !profile.getContactEmail().isEmpty() ? profile.getContactEmail() : currentUser.getEmail());
                            binding.editTextOrganizationDescription.setText(profile.getDescription() != null ? profile.getDescription() : "");
                            binding.editTextOrganizationLocation.setText(profile.getLocation() != null ? profile.getLocation() : "");

                            currentProfileImageUrl = profile.getProfileImageUrl();
                            if (currentProfileImageUrl != null && !currentProfileImageUrl.isEmpty()) {
                                Glide.with(ManageProfileActivity.this)
                                        .load(currentProfileImageUrl)
                                        .circleCrop()
                                        .placeholder(R.drawable.ic_default_profile)
                                        .error(R.drawable.ic_default_profile)
                                        .into(binding.imageViewProfile);
                            } else {
                                loadDefaultProfileImage();
                            }
                            Log.d(TAG, "Profile data loaded successfully from Firestore.");
                        } else {
                            Log.w(TAG, "Profile data exists but failed to parse into EmployerProfile object.");
                            // Handle case where data might be malformed or model mismatch
                            prefillWithDefaults();
                        }
                    } else {
                        Log.d(TAG, "No profile found for user in Firestore. Pre-filling with defaults.");
                        prefillWithDefaults(); // No existing profile, pre-fill with defaults
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSaveChanges.setEnabled(true);
                    Log.e(TAG, "Error loading profile data from Firestore", e);
                    Toast.makeText(ManageProfileActivity.this, getString(R.string.profile_load_failed_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    loadDefaultProfileImage(); // Load default on error too
                });
    }

    private void prefillWithDefaults() {
        binding.editTextContactEmail.setText(currentUser.getEmail()); // Pre-fill email
        // Set other fields to empty or default values as needed
        binding.editTextOrganizationName.setText("");
        binding.editTextOrganizationDescription.setText("");
        binding.editTextOrganizationLocation.setText("");
        loadDefaultProfileImage();
    }

    private void loadDefaultProfileImage() {
        Glide.with(ManageProfileActivity.this)
                .load(R.drawable.ic_default_profile)
                .circleCrop()
                .into(binding.imageViewProfile);
    }

    private void saveProfileChanges() {
        String orgName = binding.editTextOrganizationName.getText().toString().trim();
        String contactEmail = binding.editTextContactEmail.getText().toString().trim();
        String description = binding.editTextOrganizationDescription.getText().toString().trim();
        String location = binding.editTextOrganizationLocation.getText().toString().trim();

        if (orgName.isEmpty()) {
            binding.editTextOrganizationName.setError(getString(R.string.organization_name_required_error));
            binding.editTextOrganizationName.requestFocus();
            return;
        }
        // Add other validations as needed (e.g., email format)

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.buttonSaveChanges.setEnabled(false);

        if (imageUri != null) {
            uploadImageAndSaveProfile(orgName, contactEmail, description, location);
        } else {
            // No new image, save text data with existing image URL (if any)
            saveProfileToFirestore(orgName, contactEmail, description, location, currentProfileImageUrl);
        }
    }

    private void uploadImageAndSaveProfile(String orgName, String contactEmail, String description, String location) {
        final String imageFileName = UUID.randomUUID().toString() + ".jpg"; // Or use a more structured name
        StorageReference fileReference = storageProfileImagesRef.child(imageFileName);

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                    String newImageUrl = uri.toString();
                    Log.d(TAG, "Image uploaded successfully. URL: " + newImageUrl);

                    // If there was an old image and it's different, delete it from Storage
                    if (currentProfileImageUrl != null && !currentProfileImageUrl.isEmpty() && !currentProfileImageUrl.equals(newImageUrl)) {
                        try {
                            StorageReference oldImageRef = FirebaseStorage.getInstance().getReferenceFromUrl(currentProfileImageUrl);
                            oldImageRef.delete()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Old profile image deleted from Storage."))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to delete old profile image from Storage.", e));
                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Invalid URL for old profile image, cannot delete: " + currentProfileImageUrl, e);
                        }
                    }
                    currentProfileImageUrl = newImageUrl; // Update current image URL
                    saveProfileToFirestore(orgName, contactEmail, description, location, newImageUrl);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get image download URL", e);
                    handleSaveError(e.getMessage());
                }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Image upload failed", e);
                    handleSaveError("Image upload failed: " + e.getMessage());
                })
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    Log.d(TAG, "Upload is " + progress + "% done");
                    // You could update a UI progress bar here
                });
    }

    private void saveProfileToFirestore(String orgName, String contactEmail, String description, String location, String imageUrl) {
        // Create an EmployerProfile object using the constructor that doesn't require lastUpdatedTimestamp
        // (as @ServerTimestamp in the model will handle it)
        EmployerProfile profileToSave = new EmployerProfile(orgName, contactEmail, description, location, imageUrl);
        // If your EmployerProfile model doesn't set profileImageUrl in constructor or has a setter:
        // profileToSave.setProfileImageUrl(imageUrl);

        // Save to Firestore. This will create the document if it doesn't exist,
        // or overwrite it if it does.
        // If you want to merge (update specific fields without overwriting others not in the model),
        // use .set(profileToSave, SetOptions.merge())
        employerProfileDocRef.set(profileToSave)
                .addOnSuccessListener(aVoid -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSaveChanges.setEnabled(true);
                    imageUri = null; // Reset imageUri after successful save
                    Log.d(TAG, "Profile updated successfully in Firestore for user: " + currentUser.getUid());
                    Toast.makeText(ManageProfileActivity.this, getString(R.string.profile_updated_success), Toast.LENGTH_SHORT).show();
                    // finish(); // Optionally close activity
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update profile in Firestore", e);
                    handleSaveError(getString(R.string.profile_update_failed_error, e.getMessage()));
                });
    }

    private void handleSaveError(String errorMessage) {
        binding.progressBar.setVisibility(View.GONE);
        binding.buttonSaveChanges.setEnabled(true);
        Toast.makeText(ManageProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Or NavUtils.navigateUpFromSameTask(this); for more complex navigation
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Consider adding onRequestPermissionsResult if you add camera/storage permission requests
    // for older Android versions or specific scenarios.
}