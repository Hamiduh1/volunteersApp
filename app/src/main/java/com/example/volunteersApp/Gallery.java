package com.example.volunteersApp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // For Firestore snapshot listener
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.volunteersApp.databinding.ActivityGalleryBinding;
// Firestore imports
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
// Keep Firebase Storage imports
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Gallery extends AppCompatActivity {

    private static final String TAG = "GalleryActivity";
    private static final String FIREBASE_STORAGE_PATH = "gallery_uploads/"; // Consider a more specific path
    private static final String FIRESTORE_COLLECTION_PATH = "galleryUploads"; // Firestore collection name

    private ActivityGalleryBinding binding;

    // Firestore specific
    private FirebaseFirestore db;
    private CollectionReference uploadsCollectionRef;
    private ListenerRegistration imagesListenerRegistration;

    // Firebase Storage specific (remains the same)
    private StorageReference storageReference;

    private ImageAdapter imageAdapter;
    private List<ImgUpload> uploadedImages; // Ensure ImgUpload is Firestore compatible

    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        initializeFirebase(); // This will now initialize Firestore and Storage
        setupRecyclerView();
        setupImagePicker();

        binding.galleryUploadProgress.setVisibility(View.INVISIBLE);

        binding.uploadPhoto.setOnClickListener(v -> {
            String eventName = binding.eventName.getText().toString().trim(); // Still using eventName for file naming
            if (TextUtils.isEmpty(eventName)) {
                binding.eventName.setError("Event Name is required!");
                Toast.makeText(this, "Please enter an Event Name.", Toast.LENGTH_SHORT).show();
            } else {
                openImageChooser();
            }
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Event Gallery");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeFirebase() {
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        uploadsCollectionRef = db.collection(FIRESTORE_COLLECTION_PATH);

        // Initialize Firebase Storage (remains the same)
        storageReference = FirebaseStorage.getInstance().getReference(FIREBASE_STORAGE_PATH);
    }

    private void setupRecyclerView() {
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        uploadedImages = new ArrayList<>();
        imageAdapter = new ImageAdapter(this, uploadedImages);
        binding.recyclerView.setAdapter(imageAdapter);
    }

    private void setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        Uri imageUri = result.getData().getData();
                        String eventNameInput = binding.eventName.getText().toString().trim();
                        if (!TextUtils.isEmpty(eventNameInput)) {
                            uploadImageToFirebase(imageUri, eventNameInput);
                        } else {
                            Toast.makeText(this, "Event name was cleared. Please re-enter.", Toast.LENGTH_LONG).show();
                            binding.eventName.setError("Event Name is required!");
                        }
                    } else {
                        Toast.makeText(this, "No image selected or selection cancelled.", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void openImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        pickImageLauncher.launch(intent);
    }

    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String extension = mime.getExtensionFromMimeType(contentResolver.getType(uri));
        if (extension == null) {
            extension = MimeTypeMap.getFileExtensionFromUrl(String.valueOf(uri));
        }
        if (TextUtils.isEmpty(extension)) {
            Log.e(TAG, "Could not determine file extension for URI: " + uri);
            return "jpg"; // Default
        }
        return extension;
    }

    private void uploadImageToFirebase(Uri imageUri, final String eventName) { // eventName is used for file name
        if (imageUri == null) {
            Toast.makeText(this, "Image URI is null, cannot upload.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String fileExtension = getFileExtension(imageUri);
        if (TextUtils.isEmpty(fileExtension)) {
            Toast.makeText(this, "Could not determine image type. Upload failed.", Toast.LENGTH_LONG).show();
            return;
        }

        // Keep eventName in the filename for easier identification in Storage,
        // but it might not be the primary identifier in Firestore document.
        final String fileName = eventName.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() + "." + fileExtension;
        final StorageReference fileReference = storageReference.child(fileName);

        binding.galleryUploadProgress.setVisibility(View.VISIBLE);
        binding.galleryUploadProgress.setProgress(0);

        UploadTask uploadTask = fileReference.putFile(imageUri);

        uploadTask.addOnProgressListener(snapshot -> {
            double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
            binding.galleryUploadProgress.setProgress((int) progress);
            Log.d(TAG, "Upload is " + progress + "% done");
        }).addOnSuccessListener(taskSnapshot -> {
            fileReference.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                Log.d(TAG, "File Uploaded Successfully. Download URL: " + downloadUri.toString());
                Toast.makeText(Gallery.this, "Upload successful!", Toast.LENGTH_SHORT).show();

                // Create ImgUpload object.
                // Assuming ImgUpload has fields like 'eventName' and 'imageUrl'.
                // You might want to add a server timestamp here.
               // ImgUpload newImageUpload = new ImgUpload(eventName, downloadUri.toString());
                // If ImgUpload has a setter for a timestamp:
                // newImageUpload.setUploadTimestamp(com.google.firebase.Timestamp.now());
                // In Gallery.java
                ImgUpload newImageUpload = new ImgUpload(); // Use the no-argument constructor
                newImageUpload.setName(eventName);         // Assuming a setName(String name) method exists
                newImageUpload.setImageUrl(downloadUri.toString()); // Assuming setImageUrl(String url) exists
                // Optionally, add a timestamp if your ImgUpload class supports it
                // newImageUpload.setTimestamp(com.google.firebase.Timestamp.now());

                // Save metadata to Firestore
                uploadsCollectionRef.add(newImageUpload) // Firestore auto-generates document ID
                        .addOnSuccessListener(documentReference -> {
                            Log.d(TAG, "Image metadata saved to Firestore with ID: " + documentReference.getId());
                            // Optionally, clear the event name input after successful upload
                            // binding.eventName.setText("");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save image metadata to Firestore", e);
                            Toast.makeText(Gallery.this, "Failed to save image details: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                binding.galleryUploadProgress.setVisibility(View.INVISIBLE);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed to get download URL", e);
                Toast.makeText(Gallery.this, "Upload succeeded but failed to get URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                binding.galleryUploadProgress.setVisibility(View.INVISIBLE);
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Upload failed", e);
            Toast.makeText(Gallery.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            binding.galleryUploadProgress.setVisibility(View.INVISIBLE);
        });
    }

    private void attachFirestoreReadListener() {
        if (imagesListenerRegistration == null) {
            // Consider ordering by a timestamp if you add one to ImgUpload
            // Query query = uploadsCollectionRef.orderBy("uploadTimestamp", Query.Direction.DESCENDING);
            Query query = uploadsCollectionRef; // Simple query for all uploads

            imagesListenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot snapshots,
                                    @Nullable FirebaseFirestoreException e) {
                    if (e != null) {
                        Log.e(TAG, "Firestore listen error", e);
                        Toast.makeText(Gallery.this, "Failed to load images: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        binding.galleryUploadProgress.setVisibility(View.INVISIBLE);
                        return;
                    }

                    if (snapshots != null) {
                        uploadedImages.clear(); // Clear before adding new/updated data
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            ImgUpload imgUpload = dc.getDocument().toObject(ImgUpload.class);
                            // You can also get the document ID: String docId = dc.getDocument().getId();
                            // And set it to your ImgUpload object if it has a field for it.
                            // imgUpload.setDocumentId(docId);

                            switch (dc.getType()) {
                                case ADDED:
                                    uploadedImages.add(imgUpload);
                                    Log.d(TAG, "New image added: " + imgUpload.getTitle());
                                    break;
                                case MODIFIED:
                                    // Handle modified if necessary, for simplicity, we'll re-add/update
                                    // For a more sophisticated update, you'd find and replace the item.
                                    // This simple example just clears and re-adds all, which works
                                    // but might not be the most efficient for very large lists.
                                    // For now, the outer loop will handle repopulating.
                                    Log.d(TAG, "Modified image: " + imgUpload.getTitle());
                                    break;
                                case REMOVED:
                                    // Remove the item from your list
                                    // uploadedImages.removeIf(upload -> upload.getDocumentId().equals(docId));
                                    Log.d(TAG, "Removed image: " + imgUpload.getTitle());
                                    break;
                            }
                        }

                        // Alternative simpler way to get all documents directly (less granular than DocumentChanges):
                        /*
                        uploadedImages.clear();
                        if (!snapshots.isEmpty()) {
                            for (DocumentSnapshot document : snapshots.getDocuments()) {
                                ImgUpload imgUpload = document.toObject(ImgUpload.class);
                                if (imgUpload != null) {
                                    // String docId = document.getId();
                                    // imgUpload.setDocumentId(docId); // If you have a field for it
                                    uploadedImages.add(imgUpload);
                                } else {
                                    Log.w(TAG, "Null ImgUpload object found in Firestore snapshot for key: " + document.getId());
                                }
                            }
                        }
                        */

                        // Re-sort if necessary, e.g., if you have a client-side sort order
                        // or rely on Firestore's query order.

                        if (uploadedImages.isEmpty()) {
                            Toast.makeText(Gallery.this, "No images in gallery yet.", Toast.LENGTH_SHORT).show();
                        }
                        imageAdapter.notifyDataSetChanged();
                    } else {
                        Log.d(TAG, "Current data: null");
                        Toast.makeText(Gallery.this, "Gallery is empty or error fetching data.", Toast.LENGTH_SHORT).show();
                    }
                    binding.galleryUploadProgress.setVisibility(View.INVISIBLE);
                }
            });
            Log.d(TAG, "Attached Firestore images event listener.");
        }
    }

    private void detachFirestoreReadListener() {
        if (imagesListenerRegistration != null) {
            imagesListenerRegistration.remove();
            imagesListenerRegistration = null;
            Log.d(TAG, "Detached Firestore images event listener.");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachFirestoreReadListener(); // Attach Firestore listener
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachFirestoreReadListener(); // Detach Firestore listener
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