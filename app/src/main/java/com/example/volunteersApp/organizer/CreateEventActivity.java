package com.example.volunteersApp.organizer;

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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.R;
// Import the correct Event model
import com.example.volunteersApp.models.Event; // MODIFIED: Using Event model
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
// import java.util.ArrayList; // Not directly needed for Event model fields
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CreateEventActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT_EVENT_ID = "com.example.volunteersApp.EDIT_EVENT_ID"; // Add this line



    private static final String TAG = "CreateEventActivity";
    private static final String EVENT_STATUS_UPCOMING = "upcoming";
    private static final String EVENTS_COLLECTION = "events";
    private static final String USERS_COLLECTION = "users";
    private static final String USER_HOSTED_EVENTS_SUBCOLLECTION = "hostedEvents";

    private ImageView imageViewEventBanner;
    private Button buttonSelectImage;
    private TextInputEditText editTextEventName, editTextEventDescription, editTextEventDate,
            editTextEventTime, editTextEventLocation, editTextVolunteerLimit,
            editTextSkillsRequired, editTextEventPayment, editTextRequiredVolunteers;
    private AutoCompleteTextView autoCompleteEventType; // This will map to 'category' in Event model
    private TextInputLayout tilEventName, tilEventDescription, tilEventDate, tilEventTime,
            tilEventLocation, tilEventType, tilVolunteerLimit,
            tilSkillsRequired, tilEventPayment, tilRequiredVolunteers;
    private Button buttonCreateEvent;
    private ProgressBar progressBarCreateEvent;
    private Toolbar toolbar;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    private Uri imageUri;
    private Calendar selectedDateTimeCalendar;
    private boolean isFormEdited = false;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    imageUri = result.getData().getData();
                    Glide.with(this).load(imageUri)
                            .placeholder(R.drawable.ic_placeholder_image) // Make sure these drawables exist
                            .error(R.drawable.ic_image_error)       // Make sure these drawables exist
                            .into(imageViewEventBanner);
                    imageViewEventBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    buttonSelectImage.setText(R.string.button_change_image_text); // Make sure this string exists
                    isFormEdited = true;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);
        String eventIdToEdit = getIntent().getStringExtra(EXTRA_EDIT_EVENT_ID); // Now this will work


        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        selectedDateTimeCalendar = Calendar.getInstance();

        initializeViews();
        setupToolbar();
        setupEventListeners();
        setupEventTypeDropdown();
        setupFocusChangeListeners();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.auth_must_be_logged_in_to_create, Toast.LENGTH_LONG).show();
            finish();
            return; // Important: return after finish() to prevent further execution
        }
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbarCreateEvent);
        imageViewEventBanner = findViewById(R.id.imageViewEventBanner);
        buttonSelectImage = findViewById(R.id.buttonSelectImage);
        editTextEventName = findViewById(R.id.editTextEventName);
        editTextEventDescription = findViewById(R.id.editTextEventDescription);
        editTextEventDate = findViewById(R.id.editTextEventDate);
        editTextEventTime = findViewById(R.id.editTextEventTime);
        editTextEventLocation = findViewById(R.id.editTextEventLocation);
        autoCompleteEventType = findViewById(R.id.autoCompleteEventType);
        editTextVolunteerLimit = findViewById(R.id.editTextVolunteerLimit); // Maps to 'volunteersNeeded'
        editTextSkillsRequired = findViewById(R.id.editTextSkillsRequired); // Not directly in Event.java
        editTextEventPayment = findViewById(R.id.editTextEventPayment);     // Not directly in Event.java
        editTextRequiredVolunteers = findViewById(R.id.editTextRequiredVolunteers); // Not directly in Event.java

        buttonCreateEvent = findViewById(R.id.buttonCreateEvent);
        progressBarCreateEvent = findViewById(R.id.progressBarCreateEvent);

        tilEventName = findViewById(R.id.tilEventName);
        tilEventDescription = findViewById(R.id.tilEventDescription);
        tilEventDate = findViewById(R.id.tilEventDate);
        tilEventTime = findViewById(R.id.tilEventTime);
        tilEventLocation = findViewById(R.id.tilEventLocation);
        tilEventType = findViewById(R.id.tilEventType);
        tilVolunteerLimit = findViewById(R.id.tilVolunteerLimit);
        tilSkillsRequired = findViewById(R.id.tilSkillsRequired);
        tilEventPayment = findViewById(R.id.tilEventPayment);
        tilRequiredVolunteers = findViewById(R.id.tilRequiredVolunteers);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_activity_create_event)); // Ensure string exists
        }
    }

    private void setupEventTypeDropdown() {
        // Ensure R.array.event_types_array exists in your strings.xml or arrays.xml
        String[] eventTypes = getResources().getStringArray(R.array.event_types_array);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                eventTypes);
        autoCompleteEventType.setAdapter(adapter);

        autoCompleteEventType.setOnItemClickListener((parent, view, position, id) -> {
            if (tilEventType != null) {
                tilEventType.setError(null);
            }
            isFormEdited = true;
        });
    }

    private void setupFocusChangeListeners() {
        View.OnFocusChangeListener textChangeListener = (v, hasFocus) -> {
            if (!hasFocus) {
                isFormEdited = true;
            }
        };
        editTextEventName.setOnFocusChangeListener(textChangeListener);
        editTextEventDescription.setOnFocusChangeListener(textChangeListener);
        editTextEventLocation.setOnFocusChangeListener(textChangeListener);
        editTextVolunteerLimit.setOnFocusChangeListener(textChangeListener);
        // Fields not directly in Event.java but kept for UI consistency
        editTextSkillsRequired.setOnFocusChangeListener(textChangeListener);
        editTextEventPayment.setOnFocusChangeListener(textChangeListener);
        editTextRequiredVolunteers.setOnFocusChangeListener(textChangeListener);
    }


    private void setupEventListeners() {
        buttonSelectImage.setOnClickListener(v -> openImageChooser());
        imageViewEventBanner.setOnClickListener(v -> openImageChooser()); // Allow click on image too

        editTextEventDate.setOnClickListener(v -> showDatePickerDialog());
        editTextEventTime.setOnClickListener(v -> showTimePickerDialog());

        buttonCreateEvent.setOnClickListener(v -> {
            if (validateInput()) {
                createEvent();
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
                    isFormEdited = true;
                    if (tilEventDate != null) tilEventDate.setError(null);
                },
                selectedDateTimeCalendar.get(Calendar.YEAR),
                selectedDateTimeCalendar.get(Calendar.MONTH),
                selectedDateTimeCalendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    private void updateDateEditText() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        editTextEventDate.setText(sdf.format(selectedDateTimeCalendar.getTime()));
    }

    private void showTimePickerDialog() {
        Calendar now = Calendar.getInstance();
        int currentHour = selectedDateTimeCalendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = selectedDateTimeCalendar.get(Calendar.MINUTE);

        boolean isToday = selectedDateTimeCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                selectedDateTimeCalendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);

        if (isToday) {
            if (currentHour < now.get(Calendar.HOUR_OF_DAY) ||
                    (currentHour == now.get(Calendar.HOUR_OF_DAY) && currentMinute < now.get(Calendar.MINUTE))) {
                currentHour = now.get(Calendar.HOUR_OF_DAY);
                currentMinute = now.get(Calendar.MINUTE);
            }
        }

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    Calendar tempChosenDateTime = (Calendar) selectedDateTimeCalendar.clone();
                    tempChosenDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    tempChosenDateTime.set(Calendar.MINUTE, minute);
                    tempChosenDateTime.set(Calendar.SECOND, 0);
                    tempChosenDateTime.set(Calendar.MILLISECOND, 0);

                    Calendar currentCal = Calendar.getInstance();
                    currentCal.set(Calendar.SECOND, 0);
                    currentCal.set(Calendar.MILLISECOND, 0);

                    if (tempChosenDateTime.before(currentCal)) {
                        Toast.makeText(this, R.string.validation_error_past_time_today, Toast.LENGTH_SHORT).show();
                        if (tilEventTime != null) tilEventTime.setError(getString(R.string.validation_error_datetime_past));
                    } else {
                        selectedDateTimeCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedDateTimeCalendar.set(Calendar.MINUTE, minute);
                        updateTimeEditText();
                        isFormEdited = true;
                        if (tilEventTime != null) tilEventTime.setError(null);
                    }
                },
                currentHour,
                currentMinute,
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
    }

    private boolean validateInput() {
        boolean isValid = true;

        // Clear previous errors (Ensure all R.string values exist)
        if (tilEventName != null) tilEventName.setError(null);
        if (tilEventDescription != null) tilEventDescription.setError(null);
        if (tilEventDate != null) tilEventDate.setError(null);
        if (tilEventTime != null) tilEventTime.setError(null);
        if (tilEventLocation != null) tilEventLocation.setError(null);
        if (tilEventType != null) tilEventType.setError(null);
        if (tilVolunteerLimit != null) tilVolunteerLimit.setError(null);
        // For fields not in Event.java, validation might still be useful for UI feedback
        if (tilSkillsRequired != null) tilSkillsRequired.setError(null);
        if (tilEventPayment != null) tilEventPayment.setError(null);
        if (tilRequiredVolunteers != null) tilRequiredVolunteers.setError(null);


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
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            Calendar selectedCalCopy = (Calendar) selectedDateTimeCalendar.clone();
            selectedCalCopy.set(Calendar.SECOND, 0);
            selectedCalCopy.set(Calendar.MILLISECOND, 0);
            if (selectedCalCopy.before(now)) {
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
        if (TextUtils.isEmpty(editTextVolunteerLimit.getText())) {
            if (tilVolunteerLimit != null) tilVolunteerLimit.setError(getString(R.string.validation_error_limit_required));
            isValid = false;
        } else {
            try {
                int limit = Integer.parseInt(editTextVolunteerLimit.getText().toString());
                if (limit < 0) { // Typically should be > 0, or 0 if "unlimited" is handled that way
                    if (tilVolunteerLimit != null) tilVolunteerLimit.setError(getString(R.string.validation_error_limit_non_negative));
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                if (tilVolunteerLimit != null) tilVolunteerLimit.setError(getString(R.string.validation_error_limit_invalid));
                isValid = false;
            }
        }

        // Optional validation for fields not in Event.java model
        // Skills required (typically optional)
        // if (TextUtils.isEmpty(editTextSkillsRequired.getText())) {
        //    if (tilSkillsRequired != null) tilSkillsRequired.setError(getString(R.string.validation_error_skills_required));
        //    isValid = false; // If mandatory
        // }

        // Required Volunteers (if you want to validate it despite not being in Event.java)
        // String requiredVolunteersStr = editTextRequiredVolunteers.getText().toString().trim();
        // if (!TextUtils.isEmpty(requiredVolunteersStr)) { // Or make it mandatory
        //     try {
        //         int required = Integer.parseInt(requiredVolunteersStr);
        //         if (required < 0) {
        //             if (tilRequiredVolunteers != null) tilRequiredVolunteers.setError(getString(R.string.validation_error_required_vol_non_negative));
        //             isValid = false;
        //         }
        //     } catch (NumberFormatException e) {
        //         if (tilRequiredVolunteers != null) tilRequiredVolunteers.setError(getString(R.string.validation_error_required_vol_invalid));
        //         isValid = false;
        //     }
        // }

        // Payment (if you want to validate it)
        // String paymentStr = editTextEventPayment.getText().toString().trim();
        // if (!TextUtils.isEmpty(paymentStr)) { // Or make it mandatory
        //     try {
        //         double payment = Double.parseDouble(paymentStr);
        //         if (payment < 0) {
        //             if (tilEventPayment != null) tilEventPayment.setError(getString(R.string.validation_error_payment_non_negative));
        //             isValid = false;
        //         }
        //     } catch (NumberFormatException e) {
        //         if (tilEventPayment != null) tilEventPayment.setError(getString(R.string.validation_error_payment_invalid));
        //         isValid = false;
        //     }
        // }

        // Banner image can be optional. If mandatory:
        // if (imageUri == null) {
        //     Toast.makeText(this, R.string.validation_error_banner_required, Toast.LENGTH_SHORT).show();
        //     isValid = false;
        // }
        return isValid;
    }

    private void createEvent() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.auth_not_logged_in_error, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoadingState(true);

        if (imageUri != null) {
            uploadImageAndSaveEvent(currentUser);
        } else {
            saveEventToFirestore(currentUser, null);
        }
    }

    private void setLoadingState(boolean isLoading) {
        progressBarCreateEvent.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        buttonCreateEvent.setEnabled(!isLoading);
        // Disable form fields during loading
        editTextEventName.setEnabled(!isLoading);
        editTextEventDescription.setEnabled(!isLoading);
        editTextEventDate.setEnabled(!isLoading);
        editTextEventTime.setEnabled(!isLoading);
        editTextEventLocation.setEnabled(!isLoading);
        autoCompleteEventType.setEnabled(!isLoading);
        editTextVolunteerLimit.setEnabled(!isLoading);
        editTextSkillsRequired.setEnabled(!isLoading); // Still disable UI even if data not saved
        editTextEventPayment.setEnabled(!isLoading);   // Still disable UI
        editTextRequiredVolunteers.setEnabled(!isLoading); // Still disable UI
        buttonSelectImage.setEnabled(!isLoading);

        if (toolbar != null && toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setAlpha(isLoading ? 130 : 255);
        }
        // Prevent back press during crucial operations by disabling toolbar or handling onBackPressed
        if (toolbar != null) {
            toolbar.setEnabled(!isLoading);
        }
    }


    private void uploadImageAndSaveEvent(FirebaseUser user) {
        final StorageReference imageFileRef = storageReference.child("event_images/" + UUID.randomUUID().toString() + ".jpg");

        UploadTask uploadTask = imageFileRef.putFile(imageUri);
        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                if (task.getException() != null) throw task.getException();
                throw new Exception(getString(R.string.error_image_upload_task_failed_unknown)); // Generic error
            }
            return imageFileRef.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Uri downloadUri = task.getResult();
                Log.d(TAG, "Image uploaded: " + downloadUri.toString());
                saveEventToFirestore(user, downloadUri.toString());
            } else {
                Log.e(TAG, "Image upload failed", task.getException());
                String errorMsg = getString(R.string.error_image_upload_failed) +
                        (task.getException() != null ? ": " + task.getException().getMessage() : "");
                Toast.makeText(CreateEventActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                setLoadingState(false);
            }
        });
    }

    // MODIFIED: This method now uses the Event.java model
    private void saveEventToFirestore(FirebaseUser user, @Nullable String imageUrl) {
        String eventName = editTextEventName.getText().toString().trim();
        String description = editTextEventDescription.getText().toString().trim();
        String location = editTextEventLocation.getText().toString().trim();
        String category = autoCompleteEventType.getText().toString().trim(); // Maps to category

        int volunteersNeeded = 0;
        try {
            String limitStr = editTextVolunteerLimit.getText().toString().trim();
            if (!TextUtils.isEmpty(limitStr)) {
                volunteersNeeded = Integer.parseInt(limitStr);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing volunteer limit (volunteersNeeded).", e);
            Toast.makeText(this, R.string.error_volunteer_limit_parse_failed, Toast.LENGTH_SHORT).show();
            setLoadingState(false);
            return;
        }

        // Fields like skillsRequired, payment, requiredVolunteers are not in Event.java
        // String skillsRequiredInput = editTextSkillsRequired.getText().toString().trim();
        // double paymentInput = 0.0; // Parse if needed
        // int requiredVolunteersInput = 0; // Parse if needed

        Timestamp eventFirestoreTimestamp = new Timestamp(selectedDateTimeCalendar.getTime());
        String organizerId = user.getUid();
        String organizerName = user.getDisplayName();
        if (TextUtils.isEmpty(organizerName) || "null".equalsIgnoreCase(organizerName)) {
            organizerName = user.getEmail(); // Fallback to email
            if (TextUtils.isEmpty(organizerName)) {
                organizerName = getString(R.string.default_organizer_name); // Fallback to default
            }
        }

        CollectionReference eventsCollectionRef = db.collection(EVENTS_COLLECTION);
        DocumentReference newEventDocRef = eventsCollectionRef.document(); // Firestore generates ID
        String eventId = newEventDocRef.getId();

        // --- Create Event POJO instance ---
        Event newEvent = new Event();
        newEvent.setEventId(eventId); // Set the generated ID
        newEvent.setOrganizerId(organizerId);
        newEvent.setEventName(eventName);
        newEvent.setDescription(description);
        newEvent.setEventTimestamp(eventFirestoreTimestamp);
        newEvent.setLocation(location);
        newEvent.setCategory(category);
        newEvent.setOrganizerName(organizerName);
        if (imageUrl != null) {
            newEvent.setImageUrl(imageUrl);
        }
        newEvent.setVolunteersNeeded(volunteersNeeded);
        newEvent.setVolunteersRegistered(0); // Default for new event
        newEvent.setStatus(EVENT_STATUS_UPCOMING); // Default status
        // createdAtTimestamp will be set by @ServerTimestamp in Event.java model

        // --- Prepare for Firestore Batch Write ---
        DocumentReference userHostedEventRef = db.collection(USERS_COLLECTION)
                .document(organizerId)
                .collection(USER_HOSTED_EVENTS_SUBCOLLECTION)
                .document(eventId);

        WriteBatch batch = db.batch();

        // 1. Set the main event document using the Event POJO
        batch.set(newEventDocRef, newEvent);

        // 2. Set the document in the user's hostedEvents subcollection (summary)
        Map<String, Object> hostedEventData = new HashMap<>();
        hostedEventData.put("eventName", newEvent.getTitle());
        hostedEventData.put("eventTimestamp", newEvent.getEventTimestamp());
        hostedEventData.put("location", newEvent.getLocationName());
        if (newEvent.getImageUrl() != null) {
            hostedEventData.put("imageUrl", newEvent.getImageUrl());
        }
        hostedEventData.put("status", newEvent.getStatus()); // Also include status here
        hostedEventData.put("createdAt", FieldValue.serverTimestamp()); // For sorting user's hosted events
        batch.set(userHostedEventRef, hostedEventData);

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event (using Event.java model) and user hosted ref created: " + eventId);
                    Toast.makeText(CreateEventActivity.this, R.string.success_event_created, Toast.LENGTH_SHORT).show();
                    setLoadingState(false);
                    isFormEdited = false;
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error batch writing event (Event.java model)", e);
                    String errorMsg = getString(R.string.error_event_creation_failed) +
                            (e.getMessage() != null ? ": " + e.getMessage() : "");
                    Toast.makeText(CreateEventActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    setLoadingState(false);
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            handleBackNavigation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Use custom back navigation handler
        super.onBackPressed();
        handleBackNavigation();
        // Do not call super.onBackPressed() directly if dialog is shown,
        // as finish() will be called from dialog's positive button.
    }

    private void handleBackNavigation() {
        if (isFormEdited && progressBarCreateEvent.getVisibility() == View.GONE) {
            showExitConfirmationDialog();
        } else {
            super.onBackPressed(); // Or finish();
        }
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_exit_confirmation_title)
                .setMessage(R.string.dialog_exit_confirmation_message)
                .setPositiveButton(R.string.dialog_exit_positive_button, (dialog, which) -> {
                    isFormEdited = false;
                    finish();
                })
                .setNegativeButton(R.string.dialog_exit_negative_button, null)
                .show();
    }
}
