package com.example.volunteersApp;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.volunteersApp.databinding.Eventdescription2Binding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore Imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
// import com.google.firebase.firestore.SetOptions; // For merging data if needed

import java.util.List;
import java.util.Objects;

public class EventDescription2 extends AppCompatActivity {

    private static final String TAG = "EventDescription2FS"; // FS for Firestore

    // Firestore Collection/Field Constants
    private static final String COLLECTION_EVENTS = "events"; // Top-level collection for events

    // Event document fields
    private static final String FIELD_NAME = "name";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_EVENT_DATE = "eventDate"; // Or "date"
    private static final String FIELD_PAYMENT = "payment";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_CLOSE_ENTRIES = "closeEntries"; // Boolean
    private static final String FIELD_UNAPPROVED_USER_IDS = "unapprovedUserIds"; // Array of String
    private static final String FIELD_APPROVED_USER_IDS = "approvedUserIds";   // Array of String
    // organizerId might be a field within the event document if not part of the path
    // private static final String FIELD_HOST_ID = "organizerId";


    private Eventdescription2Binding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private DocumentReference eventDocRef;
    private ListenerRegistration eventListenerRegistration; // Combined listener for details and status

    private String eventId;
    // organizerId might not be needed for Firestore path if events are in a top-level collection
    // and organizerId is a field within the event document itself.
    private String organizerId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = Eventdescription2Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // Make sure this is called

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        eventId = getIntent().getStringExtra("EventId");
        // organizerId is used to construct the Realtime DB path. For Firestore,
        // if events are a top-level collection, eventId is sufficient for the path.
        // organizerId might be retrieved and used for logic/display if it's a field in the event document.
        organizerId = getIntent().getStringExtra("organizerId"); // Assuming organizerId is also passed

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "EventId is null or empty. Cannot load event details.");
            Toast.makeText(this, R.string.error_event_id_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // If you are using organizerId as part of the path (e.g., /hosts/{organizerId}/events/{eventId})
        // ensure organizerId is valid here.
        // For a top-level 'events' collection:
        if (organizerId == null || organizerId.isEmpty()) {
            // This check might be optional if organizerId is not part of the Firestore path
            // but just a piece of data associated with the event.
            Log.w(TAG, "organizerId is null or empty. This might be okay if not used in Firestore path.");
            // If organizerId IS critical for path or logic, then treat as error:
            // Toast.makeText(this, R.string.error_event_host_missing, Toast.LENGTH_LONG).show();
            // finish();
            // return;
        }

        // Assuming a top-level 'events' collection
        eventDocRef = db.collection(COLLECTION_EVENTS).document(eventId);
        Log.d(TAG, "Firestore path for event: " + eventDocRef.getPath());

        binding.buttonRegister.setOnClickListener(v -> registerForEvent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventDocRef != null) {
            attachCombinedEventListener();
        } else {
            Log.e(TAG, "eventDocRef is null in onStart. Cannot attach listener.");
            Toast.makeText(this, R.string.error_event_data_unavailable, Toast.LENGTH_SHORT).show();
            updateRegistrationButtonUI(false, getString(R.string.status_error), R.color.grey_disabled_button, R.color.dark_grey_text);
            clearEventDetailsUI();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventListenerRegistration != null) {
            eventListenerRegistration.remove();
        }
    }

    private void attachCombinedEventListener() {
        if (eventListenerRegistration != null) {
            eventListenerRegistration.remove();
        }
        eventListenerRegistration = eventDocRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "Listen failed for event " + eventId, e);
                    Toast.makeText(EventDescription2.this, R.string.error_could_not_load_event_details, Toast.LENGTH_SHORT).show();
                    clearEventDetailsUI();
                    updateRegistrationButtonUI(false, getString(R.string.status_error), R.color.grey_disabled_button, R.color.dark_grey_text);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Event data received for EventId: " + eventId);
                    // Populate Event Details
                    binding.textViewEventName.setText(snapshot.getString(FIELD_NAME));
                    binding.textViewLocationValue.setText(snapshot.getString(FIELD_LOCATION));
                    binding.textViewDateValue.setText(snapshot.getString(FIELD_EVENT_DATE));
                    binding.textViewPaymentValue.setText(snapshot.getString(FIELD_PAYMENT));
                    binding.textViewDescriptionValue.setText(snapshot.getString(FIELD_DESCRIPTION));

                    // Update Registration Status
                    updateRegistrationStatusUI(snapshot);
                } else {
                    Log.w(TAG, "Event data snapshot does not exist for EventId: " + eventId);
                    Toast.makeText(EventDescription2.this, R.string.error_event_details_not_found, Toast.LENGTH_SHORT).show();
                    clearEventDetailsUI();
                    updateRegistrationButtonUI(false, getString(R.string.status_event_unavailable), R.color.grey_disabled_button, R.color.dark_grey_text);
                }
            }
        });
    }

    private void updateRegistrationStatusUI(DocumentSnapshot eventSnapshot) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            updateRegistrationButtonUI(false, getString(R.string.sign_in_to_register), R.color.grey_disabled_button, R.color.dark_grey_text);
            return;
        }
        final String currentUserId = currentUser.getUid();

        Boolean isClosed = eventSnapshot.getBoolean(FIELD_CLOSE_ENTRIES);
        if (Boolean.TRUE.equals(isClosed)) { // Check for true explicitly
            updateRegistrationButtonUI(false, getString(R.string.entries_closed_button), R.color.grey_disabled_button, R.color.dark_grey_text);
            return;
        }

        List<String> approvedUserIds = (List<String>) eventSnapshot.get(FIELD_APPROVED_USER_IDS);
        if (approvedUserIds != null && approvedUserIds.contains(currentUserId)) {
            updateRegistrationButtonUI(false, getString(R.string.registered_and_accepted), R.color.green_approved, R.color.white);
            return;
        }

        List<String> unapprovedUserIds = (List<String>) eventSnapshot.get(FIELD_UNAPPROVED_USER_IDS);
        if (unapprovedUserIds != null && unapprovedUserIds.contains(currentUserId)) {
            updateRegistrationButtonUI(false, getString(R.string.registered_pending_approval), R.color.orange_pending, R.color.white);
            return;
        }

        updateRegistrationButtonUI(true, getString(R.string.register_button_text), R.color.colorPrimary, R.color.white);
    }


    private void registerForEvent() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(EventDescription2.this, R.string.error_must_be_signed_in_to_register, Toast.LENGTH_SHORT).show();
            return;
        }
        if (eventDocRef == null) {
            Toast.makeText(EventDescription2.this, R.string.error_cannot_register_for_event, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.buttonRegister.setEnabled(false); // Disable immediately

        // Atomically add the user's ID to the 'unapprovedUserIds' array
        eventDocRef.update(FIELD_UNAPPROVED_USER_IDS, FieldValue.arrayUnion(currentUser.getUid()))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EventDescription2.this, R.string.successfully_registered_pending, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "User " + currentUser.getUid() + " added to unapproved list for event " + eventId);
                    // The listener will automatically update the UI.
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EventDescription2.this, getString(R.string.registration_failed_with_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to add user to unapproved list for event: " + eventId, e);
                    // Re-enable button on failure to allow retry.
                    // Consider checking the specific error type if needed.
                    // The listener might eventually correct the state, but immediate feedback is good.
                    binding.buttonRegister.setEnabled(true);
                });
    }

    private void updateRegistrationButtonUI(boolean enabled, String text, int backgroundColorResId, int textColorResId) {
        if (binding == null || binding.buttonRegister == null) {
            Log.w(TAG, "updateRegistrationButtonUI: Binding or buttonRegister is null.");
            return;
        }

        binding.buttonRegister.setEnabled(enabled);
        binding.buttonRegister.setText(text);
        try {
            binding.buttonRegister.setBackgroundColor(ContextCompat.getColor(this, backgroundColorResId));
            binding.buttonRegister.setTextColor(ContextCompat.getColor(this, textColorResId));
        } catch (Exception e) {
            Log.e(TAG, "Error setting button colors. Ensure color resources R.color." +
                    getResources().getResourceEntryName(backgroundColorResId) + " and R.color." +
                    getResources().getResourceEntryName(textColorResId) + " exist.", e);
            // Provide default fallback colors
            binding.buttonRegister.setBackgroundColor(ContextCompat.getColor(this, R.color.grey_disabled_button)); // Ensure this default exists
            binding.buttonRegister.setTextColor(ContextCompat.getColor(this, R.color.black)); // Ensure this default exists
        }
    }

    // getStringValue helper is less needed with Firestore's snapshot.getString(),
    // which returns null if the field doesn't exist.
    // Handling nulls directly or using a POJO is more common.

    private void clearEventDetailsUI() {
        String na = getString(R.string.default_na); // Make sure R.string.default_na exists
        if (binding == null) {
            Log.w(TAG, "clearEventDetailsUI: Binding is null.");
            return;
        }
        binding.textViewEventName.setText(na);
        binding.textViewLocationValue.setText(na);
        binding.textViewDateValue.setText(na);
        binding.textViewPaymentValue.setText(na);
        binding.textViewDescriptionValue.setText(na);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}