package com.example.volunteersApp.ui.profile;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.BuildConfig;
import com.example.volunteersApp.Gallery;
import com.example.volunteersApp.LoginActivity;
import com.example.volunteersApp.R;
// REMOVED: import com.example.volunteersApp.ui.profile.SettingsFragment;
import com.example.volunteersApp.Wallet;
import com.example.volunteersApp.databinding.FragmentVolunteerProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
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
import java.util.Objects;

public class VolunteerProfileFragment extends Fragment {

    private static final String TAG = "VolunteerProfileFragment";

    private FragmentVolunteerProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private StorageReference storageRef;
    private DocumentReference userDocRef;
    private ListenerRegistration userProfileListenerRegistration;

    private Uri cameraImageUri;
    private NavController navController;

    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_PROFILE_PICTURE_URL = "profilePictureUrl";
    private static final String FIELD_ROLE = "role";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();
        initializeActivityLaunchers();
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentVolunteerProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        setupToolbar();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), R.string.user_not_logged_in, Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());
        setupClickListeners();
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity && binding != null) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbarProfile);
        }

        if (navController != null && binding != null) {
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
            NavigationUI.setupWithNavController(binding.toolbarProfile, navController, appBarConfiguration);
            binding.toolbarProfile.setTitle(getString(R.string.title_profile));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && userDocRef != null) {
            loadUserProfileFromFirestore();
        } else if (currentUser == null) {
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
                        Toast.makeText(getContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraImageUri != null) {
                        processImageUri(cameraImageUri);
                    } else {
                        Log.d(TAG, "Camera capture failed or URI is null.");
                    }
                });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processImageUri(uri);
                    }
                });
    }

    private void processImageUri(Uri imageUri) {
        if (binding == null || getContext() == null) return;
        try {
            Bitmap selectedBitmap = decodeAndRotateBitmap(requireContext(), imageUri);
            if (selectedBitmap != null) {
                binding.profileDP.setImageBitmap(selectedBitmap);
                uploadProfileImageToStorage(selectedBitmap);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error processing image URI: " + imageUri, e);
            Toast.makeText(getContext(), R.string.failed_to_process_image, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupClickListeners() {
        if (binding == null) return;

        binding.DPcamera.setOnClickListener(v -> showImageSourceDialog());
        binding.profilelogout.setOnClickListener(v -> logoutUser());


        // *** THIS IS THE FIX ***
        // The click listener for settings now navigates to the main SettingsFragment.
        binding.profilesettings.setOnClickListener(v -> {
            if (navController != null) {
                try {
                    // CORRECT NAVIGATION: Go to the main settings menu first.
                    navController.navigate(R.id.action_nav_profile_to_settings);
                } catch (IllegalArgumentException e) {
                    // Updated the log message to reflect the correct, intended destination.
                    Log.e(TAG, "Navigation to SettingsFragment failed. Is 'action_nav_profile_to_settings' defined in your nav_graph?", e);
                    Toast.makeText(getContext(), R.string.error_navigation_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.profileachievement.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), Gallery.class)));

        binding.profilewallet.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), Wallet.class)));

        if (binding.btnBecomeOrganizer != null) {
            binding.btnBecomeOrganizer.setOnClickListener(v -> {
                if (navController != null) {
                    try {
                        NavDirections action = VolunteerProfileFragmentDirections.actionNavProfileToBecomeOrganizerFragment();
                        navController.navigate(action);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Navigation to BecomeOrganizerFragment failed. Is the action defined?", e);
                        Toast.makeText(getContext(), R.string.error_navigation_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            Log.w(TAG, "btn_become_organizer not found in layout. Cannot set click listener.");
        }
    }

    private void showImageSourceDialog() {
        if (getContext() == null) return;
        CharSequence[] items = {
                getString(R.string.take_photo),
                getString(R.string.choose_from_gallery),
                getString(R.string.cancel)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.add_photo_title));
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals(getString(R.string.take_photo))) {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            } else if (items[item].equals(getString(R.string.choose_from_gallery))) {
                pickImageLauncher.launch("image/*");
            } else if (items[item].equals(getString(R.string.cancel))) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void launchCamera() {
        if (getContext() == null) return;
        try {
            cameraImageUri = createImageFileUri(requireContext());
            if (cameraImageUri != null) {
                takePictureLauncher.launch(cameraImageUri);
            } else {
                Toast.makeText(getContext(), R.string.could_not_create_image_file, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException ex) {
            Log.e(TAG, "IOException in launchCamera: ", ex);
            Toast.makeText(getContext(), R.string.error_preparing_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createImageFileUri(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MyAppImages");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            Log.e(TAG, "failed to create directory: " + storageDir.getAbsolutePath());
            return null;
        }
        File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        return FileProvider.getUriForFile(context,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                imageFile);
    }

    private void loadUserProfileFromFirestore() {
        if (userDocRef == null) {
            Log.w(TAG, "userDocRef is null, cannot load profile.");
            if (mAuth.getCurrentUser() != null) {
                userDocRef = db.collection("users").document(mAuth.getCurrentUser().getUid());
            } else {
                navigateToLogin();
                return;
            }
        }
        if (binding == null) return;

        binding.progressBarProfile.setVisibility(View.VISIBLE);

        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
        }

        userProfileListenerRegistration = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (!isAdded() || binding == null) return;

            binding.progressBarProfile.setVisibility(View.GONE);
            if (e != null) {
                Log.w(TAG, "Listen failed for user profile.", e);
                Toast.makeText(getContext(),getString(R.string.failed_to_load_profile, e.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                String name = snapshot.getString(FIELD_USERNAME);
                String email = snapshot.getString(FIELD_EMAIL);
                String phone = snapshot.getString(FIELD_PHONE);
                String profilePicUrl = snapshot.getString(FIELD_PROFILE_PICTURE_URL);
                String userRole = snapshot.getString(FIELD_ROLE);

                FirebaseUser fbUser = mAuth.getCurrentUser();
                if ((email == null || email.isEmpty()) && fbUser != null) {
                    email = fbUser.getEmail();
                }

                binding.profilename.setText(name != null ? name : getString(R.string.n_a));
                binding.profileemail.setText(email != null ? email : getString(R.string.n_a));
                binding.profilephno.setText(phone != null ? phone : getString(R.string.n_a));

                if (profilePicUrl != null && !profilePicUrl.trim().isEmpty()) {
                    Picasso.get()
                            .load(profilePicUrl)
                            .placeholder(R.drawable.ic_placeholder_profile)
                            .error(R.drawable.ic_broken_image)
                            .fit()
                            .centerCrop()
                            .into(binding.profileDP);
                } else {
                    binding.profileDP.setImageResource(R.drawable.ic_placeholder_profile);
                }

                if (binding.btnBecomeOrganizer != null) {
                    if ("organizer".equalsIgnoreCase(userRole)) {
                        binding.btnBecomeOrganizer.setVisibility(View.GONE);
                    } else if ("pending_organizer".equalsIgnoreCase(userRole)) {
                        binding.btnBecomeOrganizer.setText(R.string.organizer_application_pending);
                        binding.btnBecomeOrganizer.setEnabled(false);
                        binding.btnBecomeOrganizer.setVisibility(View.VISIBLE);
                    }
                    else {
                        binding.btnBecomeOrganizer.setText(R.string.button_become_organizer);
                        binding.btnBecomeOrganizer.setEnabled(true);
                        binding.btnBecomeOrganizer.setVisibility(View.VISIBLE);
                    }
                }

            } else {
                Log.d(TAG, "Current user data: null (document does not exist)");
                binding.profilename.setText(getString(R.string.n_a));
                binding.profileemail.setText(mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : getString(R.string.n_a));
                binding.profilephno.setText(getString(R.string.n_a));
                binding.profileDP.setImageResource(R.drawable.ic_placeholder_profile);
                if (binding.btnBecomeOrganizer != null) {
                    binding.btnBecomeOrganizer.setVisibility(View.VISIBLE);
                    binding.btnBecomeOrganizer.setEnabled(true);
                    binding.btnBecomeOrganizer.setText(R.string.button_become_organizer);
                }
            }
        });
    }

    private void uploadProfileImageToStorage(Bitmap bitmap) {
        if (binding == null || getContext() == null) return;
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || bitmap == null) {
            Toast.makeText(getContext(), R.string.error_not_logged_in_or_no_image, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBarProfile.setVisibility(View.VISIBLE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        byte[] imageData = baos.toByteArray();

        final StorageReference profileImageRef = storageRef.child("profile_images/" + currentUser.getUid() + ".jpg");

        profileImageRef.putBytes(imageData).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw Objects.requireNonNull(task.getException());
            }
            return profileImageRef.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (!isAdded() || binding == null) return;

            if (task.isSuccessful()) {
                Uri downloadUri = task.getResult();
                if (downloadUri != null) {
                    updateProfileImageUrlInFirestore(downloadUri.toString());
                } else {
                    binding.progressBarProfile.setVisibility(View.GONE);
                    Toast.makeText(getContext(), R.string.failed_to_get_download_url, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Download URL was null");
                }
            } else {
                binding.progressBarProfile.setVisibility(View.GONE);
                Toast.makeText(getContext(), getString(R.string.image_upload_failed, Objects.requireNonNull(task.getException()).getMessage()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Image upload failed", task.getException());
            }
        });
    }

    private void updateProfileImageUrlInFirestore(String downloadUrl) {
        if (userDocRef != null && binding != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_PROFILE_PICTURE_URL, downloadUrl);

            userDocRef.update(updates)
                    .addOnSuccessListener(aVoid -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBarProfile.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.profile_image_updated, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBarProfile.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.failed_to_update_image_url_firestore, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to update URL in Firestore", e);
                    });
        }
    }

    private Bitmap decodeAndRotateBitmap(@NonNull Context context, @NonNull Uri selectedImage) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(selectedImage)) {
            if (input == null) return null;

            BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
            onlyBoundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, onlyBoundsOptions);

            if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1)) return null;

            int originalSize = Math.max(onlyBoundsOptions.outHeight, onlyBoundsOptions.outWidth);
            double ratio = (originalSize > 1024) ? (originalSize / 1024.0) : 1.0;

            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inSampleSize = (int) Math.pow(2, (int) Math.round(Math.log(ratio) / Math.log(2.0)));

            try (InputStream new_input = context.getContentResolver().openInputStream(selectedImage)) {
                Bitmap bitmap = BitmapFactory.decodeStream(new_input, null, bitmapOptions);
                if (bitmap == null) return null;
                try (InputStream exif_input = context.getContentResolver().openInputStream(selectedImage)) {
                    ExifInterface ei = new ExifInterface(exif_input);
                    int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    return rotateAndResizeBitmap(bitmap, orientation, 800);
                }
            }
        }
    }

    private static Bitmap rotateAndResizeBitmap(Bitmap img, int exifOrientation, int maxSize) {
        Matrix matrix = new Matrix();
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
        }

        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        if (img != rotatedImg) img.recycle();

        int width = rotatedImg.getWidth();
        int height = rotatedImg.getHeight();
        if (width <= maxSize && height <= maxSize) return rotatedImg;

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }

        Bitmap finalBitmap = Bitmap.createScaledBitmap(rotatedImg, width, height, true);
        if (rotatedImg != finalBitmap) rotatedImg.recycle();
        return finalBitmap;
    }

    private void logoutUser() {
        mAuth.signOut();
        navigateToLogin();
    }

    private void navigateToLogin() {
        if (getActivity() == null) return;
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
            userProfileListenerRegistration = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
