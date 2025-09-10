package com.example.volunteersApp;

import android.Manifest; // For permission constant
import android.app.Activity;
import android.content.Context; // For rotateImageIfRequired context
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore; // For TakePicture contract if needed more explicitly
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.example.volunteersApp.databinding.ActivityProfileBinding;
// Assuming you have a User model, if not, direct field access from snapshot is fine
// import com.example.volunteersApp.models.User;
import com.example.volunteersApp.ui.myevents.MyEventsFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
// import com.google.firebase.firestore.DocumentSnapshot; // Not directly used in lambda param name
// import com.google.firebase.firestore.FirebaseFirestoreException; // Not directly used in lambda param name
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Profile extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    // View Binding
    private ActivityProfileBinding binding;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private StorageReference storageRootRef; // Root storage reference
    private DocumentReference userDocRef;
    private ListenerRegistration userProfileListenerRegistration;

    // Image picking
    private Uri cameraImageUri;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    // Firestore Field Constants
    // Consider moving these to a dedicated Constants class or User model if widely used
    private static final String USERS_COLLECTION = "users"; // Firestore collection name
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_PROFILE_PICTURE_URL = "profilePictureUrl";

    // Firebase Storage Path Constant
    private static final String STORAGE_PATH_PROFILE_IMAGES = "profile_images/";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storageRootRef = FirebaseStorage.getInstance().getReference();

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.toast_user_not_logged_in, Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        setupToolbar();

        // Firestore document reference
        userDocRef = db.collection(USERS_COLLECTION).document(currentUser.getUid());

        initializeActivityLaunchers();
        setupClickListeners();
        // User profile data will be loaded in onStart via loadUserProfileFromFirestore()
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbarProfile);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_profile); // String resource
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && userDocRef != null) { // Ensure userDocRef is initialized
            loadUserProfileFromFirestore();
        } else if (currentUser == null) {
            // This case should ideally be caught in onCreate, but as a safeguard:
            Log.w(TAG, "User became null in onStart, navigating to login.");
            navigateToLogin();
        }
    }

    private void initializeActivityLaunchers() {
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchCamera();
                    } else {
                        Toast.makeText(this, R.string.toast_camera_permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraImageUri != null) {
                        processImageUri(cameraImageUri, "camera");
                    } else {
                        Log.d(TAG, "Camera capture failed or cameraImageUri is null.");
                        if (cameraImageUri == null && success) {
                            Toast.makeText(this, R.string.toast_error_saving_camera_image, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processImageUri(uri, "gallery");
                    }
                });
    }

    private void processImageUri(Uri imageUri, String source) {
        try (InputStream imageStream = getContentResolver().openInputStream(imageUri)) {
            if (imageStream == null) {
                throw new IOException("Unable to open input stream for URI: " + imageUri);
            }
            Bitmap selectedBitmap = BitmapFactory.decodeStream(imageStream);
            if (selectedBitmap != null) {
                selectedBitmap = rotateImageIfRequired(Profile.this, selectedBitmap, imageUri); // Pass activity context
                selectedBitmap = getResizedBitmap(selectedBitmap, 800); // Max size 800px

                binding.profileDP.setImageBitmap(selectedBitmap); // Preview
                uploadProfileImageToStorage(selectedBitmap);    // Upload
            } else {
                Toast.makeText(this, getString(R.string.toast_failed_to_decode_image_from_source, source), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error processing " + source + " image", e);
            Toast.makeText(this, getString(R.string.toast_failed_to_process_image_from_source, source), Toast.LENGTH_SHORT).show();
        }
    }


    private void setupClickListeners() {
        binding.DPcamera.setOnClickListener(v -> showImageSourceDialog());
        binding.profilelogout.setOnClickListener(v -> logoutUser());
        binding.profilelogout1.setOnClickListener(v -> logoutUser()); // Assuming this is intentional

        binding.profilesettings.setOnClickListener(v ->
                startActivity(new Intent(Profile.this, SettingsActivity.class)));

        binding.profileachievement.setOnClickListener(v ->
                startActivity(new Intent(Profile.this, Gallery.class))); // Assuming Gallery is an Activity

        binding.profilewallet.setOnClickListener(v ->
                startActivity(new Intent(Profile.this, Wallet.class))); // Assuming Wallet is an Activity

        binding.profilemyevents.setOnClickListener(v ->
                startActivity(new Intent(Profile.this, MyEventsFragment.class))); // Assuming MyEvents is an Activity
    }

    private void showImageSourceDialog() {
        // Using string resources for dialog items
        final CharSequence[] items = {
                getString(R.string.dialog_item_take_photo),
                getString(R.string.dialog_item_choose_from_gallery),
                getString(R.string.dialog_item_cancel)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(Profile.this);
        builder.setTitle(R.string.dialog_title_add_photo);
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals(getString(R.string.dialog_item_take_photo))) {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            } else if (items[item].equals(getString(R.string.dialog_item_choose_from_gallery))) {
                pickImageLauncher.launch("image/*");
            } else if (items[item].equals(getString(R.string.dialog_item_cancel))) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void launchCamera() {
        try {
            File photoFile = createImageFile(); // Use this method to get a File
            cameraImageUri = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".fileprovider", // Make sure this matches AndroidManifest.xml
                    photoFile);

            if (cameraImageUri != null) {
                takePictureLauncher.launch(cameraImageUri);
            } else {
                Toast.makeText(this, R.string.toast_could_not_create_image_uri, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException ex) {
            Log.e(TAG, "IOException in launchCamera: ", ex);
            Toast.makeText(this, R.string.toast_error_preparing_camera, Toast.LENGTH_SHORT).show();
        }
    }

    // Modified to return File, URI is then generated by FileProvider in launchCamera
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // App-specific directory

        // Create "MyAppImages" subdirectory if it doesn't exist
        File appImageDir = new File(storageDir, "MyAppImages");
        if (!appImageDir.exists() && !appImageDir.mkdirs()) {
            Log.e(TAG, "Failed to create directory: " + appImageDir.getAbsolutePath());
            // Optionally throw an IOException here if directory creation is critical
        }
        return File.createTempFile(imageFileName, ".jpg", appImageDir);
    }

    private void loadUserProfileFromFirestore() {
        if (userDocRef == null) {
            Log.e(TAG, "userDocRef is null, cannot load profile.");
            if (mAuth.getCurrentUser() != null) { // Attempt to re-initialize if user exists
                userDocRef = db.collection(USERS_COLLECTION).document(mAuth.getCurrentUser().getUid());
            } else {
                navigateToLogin(); // Should not happen if onStart check passed
                return;
            }
        }

        binding.progressBarProfile.setVisibility(View.VISIBLE);

        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
        }

        userProfileListenerRegistration = userDocRef.addSnapshotListener((snapshot, e) -> {
            binding.progressBarProfile.setVisibility(View.GONE);
            if (e != null) {
                Log.w(TAG, "Listen failed for user profile.", e);
                Toast.makeText(Profile.this,
                        getString(R.string.toast_failed_to_load_profile_param, e.getMessage()),
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                // Option 1: Direct field access (as in your original code)
                String name = snapshot.getString(FIELD_USERNAME);
                String email = snapshot.getString(FIELD_EMAIL);
                String phone = snapshot.getString(FIELD_PHONE);
                String profilePicUrl = snapshot.getString(FIELD_PROFILE_PICTURE_URL);

                // Option 2: Using a User POJO (if you have one and it matches Firestore structure)
                // User user = snapshot.toObject(User.class);
                // if (user != null) {
                //     name = user.getName(); // Assuming User.java has getName()
                //     email = user.getEmail();
                //     phone = user.getPhone(); // Assuming User.java has getPhone()
                //     profilePicUrl = user.getProfileImageUrl();
                // } else { // Handle case where toObject fails
                //    Log.w(TAG, "Failed to parse snapshot to User object.");
                // }


                FirebaseUser firebaseUser = mAuth.getCurrentUser(); // Re-fetch for safety or use instance variable
                if ((email == null || email.isEmpty()) && firebaseUser != null) {
                    email = firebaseUser.getEmail(); // Fallback to Auth email
                }

                binding.profilename.setText(name != null ? name : getString(R.string.text_na));
                binding.profileemail.setText(email != null ? email : getString(R.string.text_na));
                binding.profilephno.setText(phone != null ? phone : getString(R.string.text_na));

                if (profilePicUrl != null && !profilePicUrl.trim().isEmpty()) {
                    Picasso.get()
                            .load(profilePicUrl)
                            .placeholder(R.drawable.ic_placeholder_profile) // Ensure this drawable exists
                            .error(R.drawable.ic_broken_image)         // Ensure this drawable exists
                            .fit()
                            .centerCrop()
                            .into(binding.profileDP);
                } else {
                    binding.profileDP.setImageResource(R.drawable.ic_placeholder_profile); // Default if no URL
                }
            } else {
                Log.d(TAG, "Current user data: null (document does not exist or snapshot is null)");
                Toast.makeText(Profile.this, R.string.toast_user_profile_not_found, Toast.LENGTH_SHORT).show();
                binding.profilename.setText(getString(R.string.text_na));
                binding.profileemail.setText(getString(R.string.text_na));
                binding.profilephno.setText(getString(R.string.text_na));
                binding.profileDP.setImageResource(R.drawable.ic_placeholder_profile);
            }
        });
    }

    private void uploadProfileImageToStorage(Bitmap bitmap) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || bitmap == null) {
            Toast.makeText(this, R.string.toast_error_no_user_or_image_for_upload, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBarProfile.setVisibility(View.VISIBLE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos); // Quality 85
        byte[] imageData = baos.toByteArray();

        // Path: profile_images/UID.jpg
        final StorageReference profileImageRef = storageRootRef.child(STORAGE_PATH_PROFILE_IMAGES + currentUser.getUid() + ".jpg");

        UploadTask uploadTask = profileImageRef.putBytes(imageData);
        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                if (task.getException() != null) {
                    throw task.getException();
                }
                // Fallback for generic error if exception is null, though unlikely for !isSuccessful
                throw new IOException("Image upload task failed without specific exception.");
            }
            return profileImageRef.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Uri downloadUri = task.getResult();
                if (downloadUri != null) {
                    updateProfileImageUrlInFirestore(downloadUri.toString());
                } else {
                    binding.progressBarProfile.setVisibility(View.GONE);
                    Toast.makeText(Profile.this, R.string.toast_failed_to_get_download_url, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Download URL was null after successful upload task.");
                }
            } else {
                binding.progressBarProfile.setVisibility(View.GONE);
                Log.e(TAG, "Image upload failed", task.getException());
                String errorMessage = task.getException() != null ? task.getException().getMessage() : getString(R.string.unknown_error);
                Toast.makeText(Profile.this, getString(R.string.toast_image_upload_failed_param, errorMessage), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateProfileImageUrlInFirestore(String downloadUrl) {
        if (userDocRef == null) {
            Log.e(TAG, "userDocRef is null, cannot update profile image URL.");
            Toast.makeText(this, R.string.toast_error_updating_firestore_no_ref, Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_PROFILE_PICTURE_URL, downloadUrl);

        userDocRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    binding.progressBarProfile.setVisibility(View.GONE);
                    Toast.makeText(Profile.this, R.string.toast_profile_image_updated, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    binding.progressBarProfile.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to update URL in Firestore", e);
                    Toast.makeText(Profile.this,
                            getString(R.string.toast_failed_to_update_firestore_url_param, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // --- Image Utility Methods ---
    // Pass Context instead of Activity if only context is needed.
    private static Bitmap rotateImageIfRequired(@NonNull Context context, @NonNull Bitmap img, @NonNull Uri selectedImage) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(selectedImage)) {
            if (input == null) {
                Log.w(TAG, "InputStream is null for URI: " + selectedImage);
                return img; // Return original if stream cannot be opened
            }

            ExifInterface ei = new ExifInterface(input);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return rotateImage(img, 90);
                case ExifInterface.ORIENTATION_ROTATE_180: return rotateImage(img, 180);
                case ExifInterface.ORIENTATION_ROTATE_270: return rotateImage(img, 270);
                default: return img;
            }
        }
        // InputStream is auto-closed by try-with-resources
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        if (degree == 0 || img == null) return img;
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        if (img != rotatedImg && !img.isRecycled()) { // Avoid recycling if it's the same instance or already recycled
            img.recycle();
        }
        return rotatedImg;
    }

    public static Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        if (image == null) return null; // Handle null input
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= 0 || height <= 0) return image; // Invalid dimensions

        if (width <= maxSize && height <= maxSize) {
            return image; // No resize needed
        }

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) { // Landscape
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else { // Portrait or square
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }

        if (width <= 0 || height <= 0) { // Check again after calculation
            Log.w(TAG, "Calculated zero or negative dimensions for resizing.");
            return image; // Return original if calculated dimensions are invalid
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(image, width, height, true);
        if (image != resizedBitmap && !image.isRecycled()) {
            image.recycle();
        }
        return resizedBitmap;
    }
    // --- End Image Utility Methods ---

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, R.string.toast_logged_out, Toast.LENGTH_SHORT).show();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(Profile.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity(); // Finishes this and all activities in task associated with it
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Default behavior is to finish current activity (same as onBackPressed)
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // onBackPressed() is handled by super.onBackPressed() which finishes the activity by default.

    @Override
    protected void onStop() {
        super.onStop();
        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
            userProfileListenerRegistration = null;
            Log.d(TAG, "User profile listener removed in onStop.");
        }
    }

    // onDestroy is generally for final cleanup. Listener is best removed in onStop.
}
