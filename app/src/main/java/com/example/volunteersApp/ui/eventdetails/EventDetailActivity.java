/** we no longer use EventDetailActivity.java insteady we use EventDetailFragment.java

package com.example.volunteersApp.ui.eventdetails; // Your actual package

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
// Removed redundant ContextCompat import

import com.bumptech.glide.Glide;
import com.example.volunteersApp.EditEventActivity;
import com.example.volunteersApp.R;
import com.example.volunteersApp.models.EventDetails; // Ensure this is your updated model
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class EventDetailActivity extends AppCompatActivity {

    private static final String TAG = "EventDetailActivity";
    public static final String EXTRA_EVENT_ID = "event_id";

    private TextView textViewEventName, textViewEventDate, textViewEventTime,
            textViewEventLocation, textViewEventDescription, textViewEventType,
            textViewSkillsRequired, textViewVolunteerLimit, textViewOrganizerName,
            textViewVolunteersAppliedCount, textViewEventStatus;
    private ImageView imageViewEventBanner;
    private ProgressBar progressBarEventDetail;
    private Toolbar toolbarEventDetail;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private String eventId;
    private EventDetails currentEvent;
    private boolean isOrganizer = false;

    private final ActivityResultLauncher<Intent> editEventLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Event updated successfully!", Toast.LENGTH_SHORT).show();
                    loadEventDetails(); // Reload to reflect changes
                } else if (result.getResultCode() == EditEventActivity.RESULT_EVENT_DELETED) {
                    Toast.makeText(this, "Event deleted.", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity as event is gone
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        if (getIntent() != null && getIntent().hasExtra(EXTRA_EVENT_ID)) {
            eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        } else {
            Log.e(TAG, "Event ID not passed to EventDetailActivity.");
            Toast.makeText(this, R.string.event_not_found_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initializeViews();
        setupToolbar();
        loadEventDetails();
    }

    private void initializeViews() {
        toolbarEventDetail = findViewById(R.id.toolbar_event_detail);
        imageViewEventBanner = findViewById(R.id.imageViewEventDetailHeader);
        textViewEventName = findViewById(R.id.textViewEventDetailName);

        // This assumes R.id.textViewEventDetailDateTime is the ID for the combined date/time TextView
        // If you have separate TextViews, assign them to textViewEventDate and textViewEventTime respectively
        TextView textViewEventDetailDateTime = findViewById(R.id.textViewEventDetailDateTime);
        textViewEventDate = textViewEventDetailDateTime; // For combined view
        textViewEventTime = textViewEventDetailDateTime; // For combined view

        textViewEventLocation = findViewById(R.id.textViewEventDetailLocation);
        textViewEventDescription = findViewById(R.id.textViewEventDetailDescription);
        textViewOrganizerName = findViewById(R.id.textViewEventDetailOrganizer);
        textViewEventType = findViewById(R.id.textViewEventDetailCategory); // Mapped from XML's category
        textViewSkillsRequired = findViewById(R.id.textViewEventDetailSkills); // Added in XML
        textViewVolunteerLimit = findViewById(R.id.textViewEventDetailSlots); // Mapped from XML's slots
        textViewVolunteersAppliedCount = findViewById(R.id.textViewVolunteersAppliedCount); // Added in XML
        textViewEventStatus = findViewById(R.id.textViewEventDetailStatus); // Added in XML

        progressBarEventDetail = findViewById(R.id.progressBarEventDetail);
    }

    private void setupToolbar() {
        if (toolbarEventDetail != null) {
            setSupportActionBar(toolbarEventDetail);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(""); // Will be set dynamically
            }
        }
    }

    private void loadEventDetails() {
        if (eventId == null) {
            Log.w(TAG, "loadEventDetails: eventId is null.");
            Toast.makeText(this, R.string.event_not_found_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setLoadingState(true);

        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get().addOnCompleteListener(task -> {
            setLoadingState(false);
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    currentEvent = document.toObject(EventDetails.class);
                    if (currentEvent != null) {
                        currentEvent.setEventId(document.getId());
                        populateEventDetails();
                        checkIfUserIsOrganizer();
                        invalidateOptionsMenu(); // Refresh menu based on organizer status
                    } else {
                        Log.e(TAG, "Failed to parse event document to EventDetails object.");
                        Toast.makeText(this, R.string.error_loading_event_details_generic, Toast.LENGTH_SHORT).show();
                        // Consider finishing if event data is corrupted
                    }
                } else {
                    Log.d(TAG, "No such event document with ID: " + eventId);
                    Toast.makeText(this, R.string.event_not_found_error, Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Log.e(TAG, "Error getting event details: ", task.getException());
                String errorMessage = task.getException() != null ? task.getException().getMessage() : getString(R.string.unknown_error);
                // CORRECTED: Use Activity's getString for formatted strings
                Toast.makeText(this, getString(R.string.error_loading_event_details, errorMessage), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoadingState(boolean isLoading) {
        if (progressBarEventDetail != null) {
            progressBarEventDetail.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        // You might want to hide the main content container while loading
        // View contentContainer = findViewById(R.id.your_main_content_container_id_in_xml);
        // if (contentContainer != null) {
        //     contentContainer.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        // }
    }

    private void populateEventDetails() {
        if (currentEvent == null) {
            Log.w(TAG, "populateEventDetails: currentEvent is null.");
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentEvent.getTitle());
        }
        textViewEventName.setText(currentEvent.getTitle());
        textViewEventDescription.setText(currentEvent.getDescription());

        // CORRECTED: Use Activity's getString for all formatted string resources
        textViewEventLocation.setText(getString(R.string.label_location_colon_xml, currentEvent.getLocationName()));

        if (textViewEventType != null) {
            textViewEventType.setText(getString(R.string.label_type_colon_xml, currentEvent.getType()));
        }

        if (textViewSkillsRequired != null && currentEvent.getSkillsRequired() != null && !currentEvent.getSkillsRequired().isEmpty() && !currentEvent.getSkillsRequired().equalsIgnoreCase("None")) {
            textViewSkillsRequired.setText(getString(R.string.label_skills_colon_xml, currentEvent.getSkillsRequired()));
            textViewSkillsRequired.setVisibility(View.VISIBLE);
        } else if (textViewSkillsRequired != null) {
            textViewSkillsRequired.setVisibility(View.GONE);
        }

        textViewOrganizerName.setText(getString(R.string.label_hosted_by_colon_xml, currentEvent.getOrganizerName()));

        if (currentEvent.getVolunteerLimit() != null && currentEvent.getVolunteerLimit() > 0) {
            textViewVolunteerLimit.setText(getString(R.string.label_limit_colon_xml, String.valueOf(currentEvent.getVolunteerLimit())));
            textViewVolunteerLimit.setVisibility(View.VISIBLE);
        } else {
            textViewVolunteerLimit.setText(getString(R.string.label_limit_colon_xml, getString(R.string.no_limit_text))); // Use a string resource
            textViewVolunteerLimit.setVisibility(View.VISIBLE); // Or GONE if you prefer to hide it
        }

        if (currentEvent.getEventTimestamp() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String formattedDate = dateFormat.format(currentEvent.getEventTimestamp().toDate());
            String formattedTime = timeFormat.format(currentEvent.getEventTimestamp().toDate());

            if (textViewEventDate == textViewEventTime && textViewEventDate != null) { // Combined view
                textViewEventDate.setText(getString(R.string.label_date_time_colon_xml, formattedDate, formattedTime));
            } else { // Separate views (ensure distinct IDs in initializeViews if this is the case)
                if (textViewEventDate != null) {
                    textViewEventDate.setText(getString(R.string.label_date_colon_xml, formattedDate));
                }
                if (textViewEventTime != null) {
                    textViewEventTime.setText(getString(R.string.label_time_colon_xml, formattedTime));
                }
            }
        } else {
            String notAvailable = getString(R.string.not_available_short); // Use "N/A" string resource
            if (textViewEventDate == textViewEventTime && textViewEventDate != null) {
                textViewEventDate.setText(getString(R.string.label_date_time_colon_xml, notAvailable, ""));
            } else {
                if (textViewEventDate != null) textViewEventDate.setText(getString(R.string.label_date_colon_xml, notAvailable));
                if (textViewEventTime != null) textViewEventTime.setText(getString(R.string.label_time_colon_xml, ""));
            }
        }

        if (textViewVolunteersAppliedCount != null) {
            int appliedCount = (currentEvent.getAppliedVolunteerUids() != null) ? currentEvent.getAppliedVolunteerUids().size() : 0;
            textViewVolunteersAppliedCount.setText(getString(R.string.label_applicants_colon_xml, String.valueOf(appliedCount)));
            textViewVolunteersAppliedCount.setVisibility(View.VISIBLE);
        }

        if (textViewEventStatus != null && currentEvent.getStatus() != null) {
            textViewEventStatus.setText(getString(R.string.label_status_colon_xml, currentEvent.getStatus()));
            textViewEventStatus.setVisibility(View.VISIBLE);
        } else if (textViewEventStatus != null){
            textViewEventStatus.setVisibility(View.GONE);
        }


        if (currentEvent.getImageUrl() != null && !currentEvent.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(currentEvent.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(imageViewEventBanner);
            imageViewEventBanner.setVisibility(View.VISIBLE);
        } else {
            imageViewEventBanner.setImageResource(R.drawable.ic_image_placeholder);
            // Consider imageViewEventBanner.setVisibility(View.GONE); if no image
        }
    }

    private void checkIfUserIsOrganizer() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null && currentEvent != null && currentEvent.getOrganizerId() != null) {
            isOrganizer = firebaseUser.getUid().equals(currentEvent.getOrganizerId());
        } else {
            isOrganizer = false;
        }
        Log.d(TAG, "Is current user the organizer? " + isOrganizer);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.event_detail_organizer_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (currentEvent != null) {
            MenuItem editItem = menu.findItem(R.id.action_edit_event_details);
            if (editItem != null) editItem.setVisible(isOrganizer);

            MenuItem deleteItem = menu.findItem(R.id.action_delete_event_details);
            if (deleteItem != null) deleteItem.setVisible(isOrganizer);

            // If you have an "Apply" button in the menu:
            // MenuItem applyItem = menu.findItem(R.id.action_apply_for_event);
            // if (applyItem != null) {
            //     applyItem.setVisible(!isOrganizer && currentEvent.isOpenForRegistration());
            // }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_edit_event_details) {
            if (isOrganizer && currentEvent != null) {
                openEditEventActivity();
            } else {
                Toast.makeText(this, R.string.not_authorized_to_edit, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemId == R.id.action_delete_event_details) {
            if (isOrganizer && currentEvent != null) {
                showDeleteConfirmationDialog();
            } else {
                Toast.makeText(this, R.string.not_authorized_to_delete, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        // Handle other menu items like "Apply"
        return super.onOptionsItemSelected(item);
    }

    private void openEditEventActivity() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, R.string.cannot_edit_missing_details, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, EditEventActivity.class);
        intent.putExtra(EditEventActivity.EXTRA_EDIT_EVENT_ID, currentEvent.getEventId());
        editEventLauncher.launch(intent);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_event_title)
                .setMessage(R.string.confirm_delete_event_message)
                .setPositiveButton(R.string.delete_confirm, (dialog, which) -> deleteEvent())
                .setNegativeButton(android.R.string.cancel, null)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .show();
    }

    private void deleteEvent() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, R.string.cannot_delete_missing_id, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoadingState(true);

        String eventIdToDelete = currentEvent.getEventId();
        String imageUrlToDelete = currentEvent.getImageUrl();

        db.collection("events").document(eventIdToDelete)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event document successfully deleted: " + eventIdToDelete);
                    Toast.makeText(EventDetailActivity.this, R.string.delete_event_success, Toast.LENGTH_SHORT).show();
                    if (imageUrlToDelete != null && !imageUrlToDelete.isEmpty()) {
                        deleteImageFromStorage(imageUrlToDelete); // This will call finish and setLoadingState(false)
                    } else {
                        setLoadingState(false);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoadingState(false);
                    Log.e(TAG, "Error deleting event document: " + eventIdToDelete, e);
                    // CORRECTED: Use Activity's getString
                    Toast.makeText(EventDetailActivity.this, getString(R.string.delete_event_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteImageFromStorage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty() || !imageUrl.startsWith("gs://")) {
            Log.d(TAG, "No valid image URL to delete from storage or URL format incorrect: " + imageUrl);
            setLoadingState(false);
            finish(); // Finish as event document deletion was successful
            return;
        }

        try {
            StorageReference photoRef = storage.getReferenceFromUrl(imageUrl);
            photoRef.delete().addOnCompleteListener(task -> { // Use onCompleteListener for final actions
                if (task.isSuccessful()) {
                    Log.d(TAG, "Event image successfully deleted from storage: " + imageUrl);
                } else {
                    Log.e(TAG, "Error deleting event image from storage: " + imageUrl, task.getException());
                    // Optionally, inform user that image deletion failed but event doc was deleted
                }
                setLoadingState(false);
                finish(); // Always finish and set loading state to false
            });
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid image URL provided for deletion: " + imageUrl, e);
            setLoadingState(false);
            finish();
        }
    }

    // --- Ensure these string resources are defined in res/values/strings.xml ---
    // <string name="event_not_found_error">Event not found or could not be loaded.</string>
    // <string name="error_loading_event_details_generic">Error loading event details.</string>
    // <string name="unknown_error">Unknown error</string>
    // <string name="error_loading_event_details">Error loading event details: %s</string>
    // <string name="label_location_colon_xml">Location: %s</string>
    // <string name="label_type_colon_xml">Category: %s</string>
    // <string name="label_skills_colon_xml">Skills Required: %s</string>
    // <string name="label_hosted_by_colon_xml">Organizer: %s</string>
    // <string name="label_limit_colon_xml">Volunteer Slots: %s</string>
    // <string name="no_limit_text">No limit</string>
    // <string name="label_date_colon_xml">Date: %s</string>
    // <string name="label_time_colon_xml">Time: %s</string>
    // <string name="label_date_time_colon_xml">%1$s at %2$s</string>
    // <string name="not_available_short">N/A</string>
    // <string name="label_applicants_colon_xml">Applicants: %s</string>
    // <string name="label_status_colon_xml">Status: %s</string>
    // <string name="not_authorized_to_edit">You are not authorized to edit this event.</string>
    // <string name="not_authorized_to_delete">You are not authorized to delete this event.</string>
    // <string name="cannot_edit_missing_details">Cannot edit event: details missing.</string>
    // <string name="confirm_delete_event_title">Confirm Delete</string>
    // <string name="confirm_delete_event_message">Are you sure you want to delete this event? This action cannot be undone.</string>
    // <string name="delete_confirm">Delete</string>
    // <string name="cannot_delete_missing_id">Cannot delete: Event ID missing.</string>
    // <string name="delete_event_success">Event deleted successfully.</string>
    // <string name="delete_event_error">Error deleting event: %s</string>

    // --- For menu items in res/menu/event_detail_organizer_menu.xml ---
    // <menu xmlns:android="http://schemas.android.com/apk/res/android"
    //    xmlns:app="http://schemas.android.com/apk/res-auto">
    //    <item
    //        android:id="@+id/action_edit_event_details"
    //        android:title="Edit Event"
    //        android:icon="@drawable/ic_edit" <!-- Optional icon -->
    //        app:showAsAction="ifRoom" />
    //    <item
    //        android:id="@+id/action_delete_event_details"
    //        android:title="Delete Event"
    //        android:icon="@drawable/ic_delete" <!-- Optional icon -->
    //        app:showAsAction="ifRoom" />
    // </menu>
}
**/
