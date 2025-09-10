package com.example.volunteersApp; // Your actual package

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.AdapterView;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;


import com.bumptech.glide.Glide;
import com.example.volunteersApp.models.EventDetails; // Your model
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class EditEventActivity extends AppCompatActivity {

    private static final String TAG = "EditEventActivity";
    public static final String EXTRA_EDIT_EVENT_ID = "edit_event_id";
    public static final int RESULT_EVENT_DELETED = 2; // Custom result code (if you add delete from here)

    private ImageView imageViewEventBanner;
    private Button buttonSelectImage;
    private TextInputEditText editTextEventName, editTextEventDescription, editTextEventDate,
            editTextEventTime, editTextEventLocation, editTextVolunteerLimit,
            editTextSkillsRequired;
    private AutoCompleteTextView autoCompleteEventType;
    private TextInputLayout tilEventName, tilEventDescription, tilEventDate, tilEventTime,
            tilEventLocation, tilEventType, tilVolunteerLimit,
            tilSkillsRequired;
    private Button buttonSaveChanges;
    private ProgressBar progressBarEditEvent;
    private Toolbar toolbar;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    private Uri newImageUri;
    private String existingImageUrl;
    private Calendar selectedDateTimeCalendar;
    private String eventIdToEdit;
    private EventDetails currentEventData; // To hold fetched event data to pre-fill

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    newImageUri = result.getData().getData();
                    Glide.with(this).load(newImageUri)
                            .placeholder(R.drawable.ic_placeholder_image)
                            .error(R.drawable.ic_image_error)
                            .into(imageViewEventBanner);
                    imageViewEventBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    buttonSelectImage.setText(R.string.button_change_image_text);
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_event); // Ensure you have this layout

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        selectedDateTimeCalendar = Calendar.getInstance(); // Initialize

        if (getIntent() != null && getIntent().hasExtra(EXTRA_EDIT_EVENT_ID)) {
            eventIdToEdit = getIntent().getStringExtra(EXTRA_EDIT_EVENT_ID);
        } else {
            Log.e(TAG, "No Event ID passed to EditEventActivity.");
            Toast.makeText(this, "Error: Event ID missing for edit.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeViews();
        setupToolbar();
        setupEventTypeDropdown();
        setupEventListeners();

        loadEventDataForEditing();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbarEditEvent); // Make sure this ID is in activity_edit_event.xml
        imageViewEventBanner = findViewById(R.id.imageViewEventBanner);
        buttonSelectImage = findViewById(R.id.buttonSelectImage);
        editTextEventName = findViewById(R.id.editTextEventName);
        editTextEventDescription = findViewById(R.id.editTextEventDescription);
        editTextEventDate = findViewById(R.id.editTextEventDate);
        editTextEventTime = findViewById(R.id.editTextEventTime);
        editTextEventLocation = findViewById(R.id.editTextEventLocation);
        autoCompleteEventType = findViewById(R.id.autoCompleteEventType);
        editTextVolunteerLimit = findViewById(R.id.editTextVolunteerLimit);
        editTextSkillsRequired = findViewById(R.id.editTextSkillsRequired);

        buttonSaveChanges = findViewById(R.id.buttonSaveChanges); // Ensure this ID is for the save button
        progressBarEditEvent = findViewById(R.id.progressBarEditEvent); // Ensure this ID

        tilEventName = findViewById(R.id.tilEventName);
        tilEventDescription = findViewById(R.id.tilEventDescription);
        tilEventDate = findViewById(R.id.tilEventDate);
        tilEventTime = findViewById(R.id.tilEventTime);
        tilEventLocation = findViewById(R.id.tilEventLocation);
        tilEventType = findViewById(R.id.tilEventType);
        tilVolunteerLimit = findViewById(R.id.tilVolunteerLimit);
        tilSkillsRequired = findViewById(R.id.tilSkillsRequired);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.edit_event_title);
        }
    }

    private void setupEventTypeDropdown() {
        String[] eventTypes = getResources().getStringArray(R.array.event_types_array);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, eventTypes);
        autoCompleteEventType.setAdapter(adapter);
        autoCompleteEventType.setOnItemClickListener((parent, view, position, id) -> {
            if (tilEventType != null) tilEventType.setError(null);
        });
        // For pre-selection, you'll set the text after data is loaded in populateFormWithEventData
    }

    private void setupEventListeners() {
        buttonSelectImage.setOnClickListener(v -> openImageChooser());
        imageViewEventBanner.setOnClickListener(v -> openImageChooser());
        editTextEventDate.setOnClickListener(v -> showDatePickerDialog());
        editTextEventTime.setOnClickListener(v -> showTimePickerDialog());
        buttonSaveChanges.setOnClickListener(v -> {
            if (validateInput()) {
                updateEvent();
            }
        });
    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, monthOfYear, dayOfMonth) -> {
                    selectedDateTimeCalendar.set(Calendar.YEAR, year);
                    selectedDateTimeCalendar.set(Calendar.MONTH, monthOfYear);
                    selectedDateTimeCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateEditText();
                },
                selectedDateTimeCalendar.get(Calendar.YEAR),
                selectedDateTimeCalendar.get(Calendar.MONTH),
                selectedDateTimeCalendar.get(Calendar.DAY_OF_MONTH));
        // Allow selecting today or future dates. Min date can be set to the start of today.
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        datePickerDialog.getDatePicker().setMinDate(today.getTimeInMillis());
        datePickerDialog.show();
    }

    private void updateDateEditText() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        editTextEventDate.setText(sdf.format(selectedDateTimeCalendar.getTime()));
        if (tilEventDate != null) tilEventDate.setError(null);
    }

    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    // Create a temporary calendar to check if the selected date and new time is in the past
                    Calendar tempChosenDateTime = (Calendar) selectedDateTimeCalendar.clone(); // Clone to preserve the selected date
                    tempChosenDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    tempChosenDateTime.set(Calendar.MINUTE, minute);
                    tempChosenDateTime.set(Calendar.SECOND, 0);
                    tempChosenDateTime.set(Calendar.MILLISECOND, 0);

                    Calendar now = Calendar.getInstance();
                    now.set(Calendar.SECOND, 0); // Normalize current time for comparison
                    now.set(Calendar.MILLISECOND, 0);

                    // Check if the selected date is today
                    boolean isToday = selectedDateTimeCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            selectedDateTimeCalendar.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                            selectedDateTimeCalendar.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH);

                    if (isToday && tempChosenDateTime.before(now)) {
                        Toast.makeText(this, R.string.validation_error_past_time_today, Toast.LENGTH_SHORT).show();
                    } else if (tempChosenDateTime.before(now) && !isToday && selectedDateTimeCalendar.before(now)) {
                        // This case handles if somehow a past date was selected (though DatePicker should prevent it)
                        // and then a time is picked.
                        Toast.makeText(this, R.string.validation_error_datetime_past, Toast.LENGTH_SHORT).show();
                    }
                    else {
                        selectedDateTimeCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedDateTimeCalendar.set(Calendar.MINUTE, minute);
                        updateTimeEditText();
                    }
                },
                selectedDateTimeCalendar.get(Calendar.HOUR_OF_DAY),
                selectedDateTimeCalendar.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(this));
        timePickerDialog.show();
    }

    private void updateTimeEditText() {
        SimpleDateFormat sdf;
        if (DateFormat.is24HourFormat(this)) {
            sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        } else {
            sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        }
        editTextEventTime.setText(sdf.format(selectedDateTimeCalendar.getTime()));
        if (tilEventTime != null) tilEventTime.setError(null);
    }

    private void loadEventDataForEditing() {
        setLoadingState(true);
        DocumentReference eventRef = db.collection("events").document(eventIdToEdit);
        eventRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                currentEventData = documentSnapshot.toObject(EventDetails.class);
                if (currentEventData != null) {
                    currentEventData.setEventId(documentSnapshot.getId()); // Store ID
                    existingImageUrl = currentEventData.getImageUrl(); // Store for later comparison
                    populateFormWithEventData();
                } else {
                    Toast.makeText(this, "Error loading event data.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Toast.makeText(this, "Event not found.", Toast.LENGTH_SHORT).show();
                finish();
            }
            setLoadingState(false);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching event data: ", e);
            Toast.makeText(this, "Failed to load event data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            setLoadingState(false);
            finish();
        });
    }

    private void populateFormWithEventData() {
        if (currentEventData == null) return;

        editTextEventName.setText(currentEventData.getTitle());
        editTextEventDescription.setText(currentEventData.getDescription());
        editTextEventLocation.setText(currentEventData.getLocationName());
        editTextSkillsRequired.setText(currentEventData.getSkillsRequired());
        editTextVolunteerLimit.setText(String.valueOf(currentEventData.getVolunteerLimit()));

        // Pre-fill AutoCompleteTextView for event type
        autoCompleteEventType.setText(currentEventData.getType(), false); // false to not filter

        // Pre-fill date and time
        if (currentEventData.getEventTimestamp() != null) {
            selectedDateTimeCalendar.setTime(currentEventData.getEventTimestamp().toDate());
            updateDateEditText();
            updateTimeEditText();
        }

        // Load existing image
        if (currentEventData.getImageUrl() != null && !currentEventData.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(currentEventData.getImageUrl())
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_image_error)
                    .into(imageViewEventBanner);
            imageViewEventBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            buttonSelectImage.setText(R.string.button_change_image_text);
        } else {
            buttonSelectImage.setText(R.string.button_select_image_text);
            imageViewEventBanner.setImageResource(R.drawable.ic_placeholder_image); // Default placeholder
        }
    }

    private boolean validateInput() {
        // This validation logic can be identical to CreateEventActivity
        // or slightly adjusted if needed for editing (e.g., image not mandatory to change)
        boolean isValid = true;

        if (tilEventName != null) tilEventName.setError(null);
        if (tilEventDescription != null) tilEventDescription.setError(null);
        if (tilEventDate != null) tilEventDate.setError(null);
        if (tilEventTime != null) tilEventTime.setError(null);
        if (tilEventLocation != null) tilEventLocation.setError(null);
        if (tilEventType != null) tilEventType.setError(null);
        if (tilSkillsRequired != null) tilSkillsRequired.setError(null);
        if (tilVolunteerLimit != null) tilVolunteerLimit.setError(null);

        if (TextUtils.isEmpty(editTextEventName.getText())) {
            if (tilEventName != null) tilEventName.setError(getString(R.string.validation_error_name_required));
            isValid = false;
        }
        if (TextUtils.isEmpty(editTextEventDescription.getText())) {
            if (tilEventDescription != null) tilEventDescription.setError(getString(R.string.validation_error_description_required));
            isValid = false;
        }
        if (TextUtils.isEmpty(editTextEventDate.getText())) {
            if (tilEventDate != null) tilEventDate.setError(getString(R.string.validation_error_date_required));
            isValid = false;
        }
        if (TextUtils.isEmpty(editTextEventTime.getText())) {
            if (tilEventTime != null) tilEventTime.setError(getString(R.string.validation_error_time_required));
            isValid = false;
        } else {
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0);

            Calendar selectedCalCopyForValidation = (Calendar) selectedDateTimeCalendar.clone();
            selectedCalCopyForValidation.set(Calendar.SECOND, 0); selectedCalCopyForValidation.set(Calendar.MILLISECOND, 0);

            if (selectedCalCopyForValidation.before(now)) {
                if (tilEventTime != null) tilEventTime.setError(getString(R.string.validation_error_datetime_past));
                if (tilEventDate != null && TextUtils.isEmpty(tilEventDate.getError())) {
                    tilEventDate.setError(getString(R.string.validation_error_datetime_past_detail));
                }
                isValid = false;
            }
        }
        if (TextUtils.isEmpty(editTextEventLocation.getText())) {
            if (tilEventLocation != null) tilEventLocation.setError(getString(R.string.validation_error_location_required));
            isValid = false;
        }
        if (TextUtils.isEmpty(autoCompleteEventType.getText().toString().trim())) {
            if (tilEventType != null) tilEventType.setError(getString(R.string.validation_error_type_required));
            isValid = false;
        }
        if (TextUtils.isEmpty(editTextSkillsRequired.getText())) {
            if (tilSkillsRequired != null) tilSkillsRequired.setError(getString(R.string.validation_error_skills_required));
            isValid = false;
        }
        if (TextUtils.isEmpty(editTextVolunteerLimit.getText())) {
            if (tilVolunteerLimit != null) tilVolunteerLimit.setError(getString(R.string.validation_error_limit_required));
            isValid = false;
        } else {
            try {
                int limit = Integer.parseInt(editTextVolunteerLimit.getText().toString());
                if (limit < 0) {
                    if (tilVolunteerLimit != null) tilVolunteerLimit.setError(getString(R.string.validation_error_limit_positive));
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                if (tilVolunteerLimit != null) tilVolunteerLimit.setError(getString(R.string.validation_error_limit_invalid));
                isValid = false;
            }
        }
        // Image is not strictly mandatory to change during an edit, so no validation for newImageUri here
        // unless you want to enforce one.
        return isValid;
    }

    private void updateEvent() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.auth_not_logged_in_error, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoadingState(true);

        // If a new image was selected, upload it.
        // Otherwise, use the existing image URL or null if no image was there.
        if (newImageUri != null) {
            uploadNewImageAndUpdateEvent(currentUser);
        } else {
            // No new image selected, update details with existing image URL
            saveEventUpdatesToFirestore(currentUser, existingImageUrl);
        }
    }

    private void setLoadingState(boolean isLoading) {
        progressBarEditEvent.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        buttonSaveChanges.setEnabled(!isLoading);
        // Disable other fields as in CreateEventActivity's setLoadingState
        editTextEventName.setEnabled(!isLoading);
        editTextEventDescription.setEnabled(!isLoading);
        // ... disable all input fields
        autoCompleteEventType.setEnabled(!isLoading);
        buttonSelectImage.setEnabled(!isLoading);
    }

    private void uploadNewImageAndUpdateEvent(FirebaseUser user) {
        // If there was an old image, delete it first (optional, or handle after new upload success)
        // For simplicity here, we'll upload new, then if successful, try to delete old one if different.

        final StorageReference imageFileRef = storageReference.child("event_images/" + UUID.randomUUID().toString() + ".jpg");
        UploadTask uploadTask = imageFileRef.putFile(newImageUri);

        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                if (task.getException() != null) throw task.getException();
                throw new Exception("Image upload task failed mysteriously.");
            }
            return imageFileRef.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Uri downloadUri = task.getResult();
                Log.d(TAG, "New image uploaded successfully: " + downloadUri.toString());

                // Attempt to delete old image if it existed and is different from the new one
                if (existingImageUrl != null && !existingImageUrl.isEmpty() && !existingImageUrl.equals(downloadUri.toString())) {
                    deleteOldImageFromStorage(existingImageUrl);
                }
                saveEventUpdatesToFirestore(user, downloadUri.toString());
            } else {
                Log.e(TAG, "New image upload failed or failed to get download URL", task.getException());
                Toast.makeText(EditEventActivity.this, "Image upload failed: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                setLoadingState(false);
            }
        });
    }

    private void deleteOldImageFromStorage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        try {
            StorageReference photoRef = storage.getReferenceFromUrl(imageUrl);
            photoRef.delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully deleted old event image: " + imageUrl))
                    .addOnFailureListener(exception -> Log.e(TAG, "Failed to delete old event image: " + imageUrl, exception));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid old image URL for deletion: " + imageUrl, e);
        }
    }


    private void saveEventUpdatesToFirestore(FirebaseUser user, @Nullable String newImageUrl) {
        DocumentReference eventRef = db.collection("events").document(eventIdToEdit);

        Map<String, Object> eventUpdates = new HashMap<>();
        eventUpdates.put("name", editTextEventName.getText().toString().trim());
        eventUpdates.put("description", editTextEventDescription.getText().toString().trim());
        eventUpdates.put("location", editTextEventLocation.getText().toString().trim());
        eventUpdates.put("type", autoCompleteEventType.getText().toString().trim());
        eventUpdates.put("skillsRequired", editTextSkillsRequired.getText().toString().trim());
        try {
            eventUpdates.put("volunteerLimit", Integer.parseInt(editTextVolunteerLimit.getText().toString().trim()));
        } catch (NumberFormatException e){
            Log.e(TAG, "Error parsing volunteer limit for update", e);
            eventUpdates.put("volunteerLimit", currentEventData != null ? currentEventData.getVolunteerLimit() : 0); // fallback
        }
        eventUpdates.put("eventTimestamp", new Timestamp(selectedDateTimeCalendar.getTime()));

        if (newImageUri != null && newImageUrl != null) { // If a new image was uploaded
            eventUpdates.put("imageUrl", newImageUrl);
        } else if (newImageUri == null && newImageUrl == null && currentEventData != null && currentEventData.getImageUrl() != null) {
            // No new image selected, and no explicit instruction to remove image. Keep existing.
            // So, don't add imageUrl to eventUpdates if it's meant to be unchanged.
            // If you want to allow REMOVING an image, you'd need a separate UI element for that.
            // For now, if newImageUri is null, we assume the image remains as it was or is removed if newImageUrl is explicitly set to null (which it isn't in this flow).
        } else if (newImageUri == null && newImageUrl == null) {
            // This case means no new image was selected AND there was no existing image or we want to remove it.
            // If you want to explicitly allow removing an image, you might pass a special signal or set imageUrl to FieldValue.delete().
            // For simplicity, if newImageUri is null, and existingImageUrl was also null/empty, imageUrl field isn't touched unless explicitly set.
            // If newImageUrl is null (because newImageUri was null), and existingImageUrl had a value, it means no change to image.
            // The logic can be tricky: if newImageUrl is null it means "no new image was uploaded". We should only update the "imageUrl" field if a new image was uploaded OR if we intend to clear it.
            // Let's refine: Only update imageUrl if newImageUrl is not null (meaning a new image was successfully uploaded and has a URL).
            // If you add a "remove image" button, then you would set eventUpdates.put("imageUrl", null); or FieldValue.delete().
        }

        if (newImageUrl != null) { // This means a new image was selected and uploaded, or we are intentionally setting it (e.g. to null if removing)
            eventUpdates.put("imageUrl", newImageUrl);
        }
        // If newImageUrl is null, it means no NEW image was uploaded. The existing imageUrl in Firestore remains unless explicitly cleared.


        eventRef.update(eventUpdates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event updated successfully!");
                    Toast.makeText(EditEventActivity.this, "Event updated successfully", Toast.LENGTH_SHORT).show();
                    setLoadingState(false);
                    setResult(RESULT_OK); // Signal EventDetailActivity to refresh
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating event", e);
                    Toast.makeText(EditEventActivity.this, "Error updating event: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setLoadingState(false);
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Consider warning about unsaved changes if form is dirty
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // You might want to add a method like isFormDirty() if you want to warn before exiting
    // private boolean isFormDirty() { ... }
}

