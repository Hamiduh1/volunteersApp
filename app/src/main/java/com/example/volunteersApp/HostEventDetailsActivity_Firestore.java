// Renamed and migrating to Firestore: HostEventDetailsActivity_Firestore.java
package com.example.volunteersApp;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu; // Import Menu
import android.view.MenuInflater; // Import MenuInflater
import android.view.MenuItem;
import android.view.View; // Import View
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher; // For activity result
import androidx.activity.result.contract.ActivityResultContracts; // For activity result
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.volunteersApp.organizer.CreateEventActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HostEventDetailsActivity_Firestore extends AppCompatActivity {

    private static final String TAG = "HostEventDetailsFirestore";
    public static final String EXTRA_EVENT_ID = "EventId";

    // UI Elements
    private TextView textViewEventName, textViewLocation, textViewDate, textViewPayment, textViewDescription, textViewEntryStatus;
    private Button buttonCloseEntries, buttonOpenEntries, buttonChatbox; // Removed buttonEditEvent declaration here, will be in menu
    private ListView listViewRequestedVolunteers;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference eventDocumentReference;
    private ListenerRegistration eventDetailsListenerRegistration;

    // Data
    private List<String> requestedVolunteerIds;
    private RequestedVolunteersList requestedVolunteersAdapter;

    private String currentEventId;
    private String currentOrganizerId;
    private String eventOrganizerIdFromSnapshot; // To store the organizer ID from the loaded event

    private Drawable activeButtonStyle;
    private Drawable inactiveButtonStyle;

    // Activity Result Launcher for when CreateEventActivity finishes (e.g., after an update)
    // We might not need to do anything specific on return if the snapshot listener handles updates.
    private final ActivityResultLauncher<Intent> editEventLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Event might have been updated.
                    // The snapshot listener should ideally pick up changes automatically.
                    // If not, you might need a manual refresh here.
                    Log.d(TAG, "Returned from edit event. Data should refresh via listener.");
                    // Toast.makeText(this, "Event details might have been updated.", Toast.LENGTH_SHORT).show();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // IMPORTANT: Ensure you have a layout file named 'activity_host_event_details.xml'
        // and that it contains all the referenced views (including a Toolbar with id 'toolbar_host_event_details')
        setContentView(R.layout.activity_host_event_details);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.auth_not_logged_in_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentOrganizerId = currentUser.getUid();

        currentEventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID is missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        eventDocumentReference = db.collection("events").document(currentEventId);

        initializeUI();
        setupToolbar(); // Consolidate toolbar setup

        activeButtonStyle = ContextCompat.getDrawable(this, R.drawable.round_button1);
        inactiveButtonStyle = ContextCompat.getDrawable(this, R.drawable.round_button);

        setupButtonListeners();
        setupListView();
        loadEventDetails();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_host_event_details);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Title will be set in loadEventDetails
        }
    }

    private void initializeUI() {
        textViewEventName = findViewById(R.id.textViewHostEventName);
        textViewLocation = findViewById(R.id.textViewHostEventLocation);
        textViewDate = findViewById(R.id.textViewHostEventDate);
        textViewPayment = findViewById(R.id.textViewHostEventPayment); // Make sure this ID exists in your layout
        textViewDescription = findViewById(R.id.textViewHostEventDescription);
        textViewEntryStatus = findViewById(R.id.textViewEntryStatus);

        buttonCloseEntries = findViewById(R.id.button_close_event_entries);
        buttonOpenEntries = findViewById(R.id.button_open_event_entries);
        buttonChatbox = findViewById(R.id.button_event_chatbox);

        listViewRequestedVolunteers = findViewById(R.id.listView_requested_volunteers);

        // Hide edit-related buttons initially, show them if user is the host
        // The actual menu item's visibility will be handled in onPrepareOptionsMenu
    }

    private void setupButtonListeners() {
        buttonChatbox.setOnClickListener(v -> {
            Intent intent = new Intent(HostEventDetailsActivity_Firestore.this, ChatBox.class);
            intent.putExtra(ChatBox.EXTRA_EVENT_ID, currentEventId); // Use constant from ChatBox if available
            intent.putExtra(ChatBox.EXTRA_USER_ID, currentOrganizerId); // Pass current user ID
            startActivity(intent);
        });

        buttonCloseEntries.setOnClickListener(v -> setEntryStatus(true));
        buttonOpenEntries.setOnClickListener(v -> setEntryStatus(false));
    }

    private void setEntryStatus(boolean closeEntries) {
        if (eventDocumentReference == null || !currentOrganizerId.equals(eventOrganizerIdFromSnapshot)) {
            Toast.makeText(this, "You are not authorized to change entry status.", Toast.LENGTH_SHORT).show();
            return;
        }

        eventDocumentReference.update("closeEntries", closeEntries)
                .addOnSuccessListener(aVoid -> {
                    String status = closeEntries ? "CLOSED" : "OPEN";
                    Toast.makeText(HostEventDetailsActivity_Firestore.this,
                            "Entries are now " + status,
                            Toast.LENGTH_SHORT).show();
                    // UI update will be handled by the SnapshotListener
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HostEventDetailsActivity_Firestore.this,
                            "Failed to update entry status: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update entry status", e);
                });
    }

    private void setupListView() {
        requestedVolunteerIds = new ArrayList<>();
        // Ensure RequestedVolunteersList adapter can handle UIDs and fetch details from a 'users' collection
        requestedVolunteersAdapter = new RequestedVolunteersList(
                this,
                requestedVolunteerIds,
                currentEventId,
                currentOrganizerId
        );
        listViewRequestedVolunteers.setAdapter(requestedVolunteersAdapter);
        listViewRequestedVolunteers.setOnItemClickListener((parent, view, position, id) -> {
            String volunteerId = requestedVolunteerIds.get(position);
            // Implement approval/rejection dialog and logic
            showApproveRejectDialog(volunteerId); // Example method call
            // Toast.makeText(this, "Clicked volunteer (Firestore): " + volunteerId, Toast.LENGTH_SHORT).show();
        });
    }

    private void showApproveRejectDialog(String volunteerId) {
        // Placeholder for your dialog logic
        // You would typically use AlertDialog.Builder
        // For now, let's directly call approve for demonstration, but a dialog is better UX.
        // Example:
        // new AlertDialog.Builder(this)
        // .setTitle("Manage Volunteer")
        // .setMessage("Approve or reject " + volunteerId + "?")
        // .setPositiveButton("Approve", (dialog, which) -> approveVolunteer(volunteerId))
        // .setNegativeButton("Reject", (dialog, which) -> rejectVolunteer(volunteerId)) // You'd need a rejectVolunteer method
        // .setNeutralButton("Cancel", null)
        // .show();
        Toast.makeText(this, "Conceptual: Show approve/reject dialog for " + volunteerId, Toast.LENGTH_LONG).show();
        // approveVolunteer(volunteerId); // Call this from the dialog's positive button
    }


    private void approveVolunteer(String volunteerId) {
        if (eventDocumentReference == null || !currentOrganizerId.equals(eventOrganizerIdFromSnapshot)) {
            Toast.makeText(this, "You are not authorized to manage volunteers for this event.", Toast.LENGTH_SHORT).show();
            return;
        }
        // ... (rest of your approveVolunteer logic using a transaction)
        // Ensure you handle removing from 'unApprovedVolunteerIds' and adding to 'approvedVolunteerIds'
        // Also, consider adding to a 'registeredVolunteers' list in the user's document.

        db.runTransaction(transaction -> {
            DocumentSnapshot eventSnapshot = transaction.get(eventDocumentReference);
            if (!eventSnapshot.exists()) {
                throw new FirebaseFirestoreException("Event not found!", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            // Check if already approved to prevent duplicate operations if UI is slow
            List<String> approvedIds = (List<String>) eventSnapshot.get("approvedVolunteerIds");
            if (approvedIds != null && approvedIds.contains(volunteerId)) {
                // Already approved, maybe just remove from unapproved if still there
                transaction.update(eventDocumentReference, "unApprovedVolunteerIds", FieldValue.arrayRemove(volunteerId));
                return "Volunteer already approved."; // Or some other indicator
            }

            transaction.update(eventDocumentReference, "unApprovedVolunteerIds", FieldValue.arrayRemove(volunteerId));
            transaction.update(eventDocumentReference, "approvedVolunteerIds", FieldValue.arrayUnion(volunteerId));

            // Optional: Update the user's document
            DocumentReference userDocRef = db.collection("users").document(volunteerId);
            transaction.update(userDocRef, "registeredEvents", FieldValue.arrayUnion(currentEventId));
            // Consider also adding to a subcollection in user's doc if more event details per user are needed

            return null; // Indicates success for the transaction's lambda
        }).addOnSuccessListener(result -> {
            if (result instanceof String) { // Check if it was the "already approved" message
                Toast.makeText(HostEventDetailsActivity_Firestore.this, (String) result, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(HostEventDetailsActivity_Firestore.this, volunteerId + " approved.", Toast.LENGTH_SHORT).show();
            }
            // Snapshot listener will refresh UI
        }).addOnFailureListener(e -> {
            Toast.makeText(HostEventDetailsActivity_Firestore.this, "Failed to approve: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to approve volunteer", e);
        });
    }


    private void loadEventDetails() {
        if (eventDocumentReference == null) return;

        eventDetailsListenerRegistration = eventDocumentReference.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                Toast.makeText(HostEventDetailsActivity_Firestore.this, "Failed to load event details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "Current data: " + snapshot.getData());

                eventOrganizerIdFromSnapshot = snapshot.getString("organizerId"); // Store for authorization checks

                String name = snapshot.getString("eventName");
                String location = snapshot.getString("location");
                String date = snapshot.getString("date"); // Consider storing as Timestamp and formatting
                String time = snapshot.getString("time"); // Consider storing as Timestamp and formatting

                Object paymentObj = snapshot.get("payment"); // Could be Double or Long from Firestore
                String paymentString = "N/A";
                if (paymentObj instanceof Number) {
                    paymentString = String.format("%.2f", ((Number) paymentObj).doubleValue());
                } else if (paymentObj != null) {
                    paymentString = String.valueOf(paymentObj); // Fallback
                }


                String description = snapshot.getString("description");
                Boolean isClosed = snapshot.getBoolean("closeEntries");
                if (isClosed == null) isClosed = false;

                // --- UI Updates ---
                if (getSupportActionBar() != null && name != null) {
                    getSupportActionBar().setTitle(name);
                }
                textViewEventName.setText(name != null ? name : "N/A");
                textViewLocation.setText(location != null ? "Location: " + location : "Location: N/A");
                String dateTime = (date != null ? date : "") + (time != null ? " at " + time : "");
                textViewDate.setText(!dateTime.trim().isEmpty() ? "When: " + dateTime.trim() : "Date/Time: N/A");
                textViewPayment.setText("Payment: $" + paymentString); // Assuming payment is in dollars
                textViewDescription.setText(description != null ? description : "No description available.");

                updateEntryStatusUI(isClosed);

                // Control visibility of host-specific controls
                boolean isCurrentUserHost = currentOrganizerId.equals(eventOrganizerIdFromSnapshot);
                buttonCloseEntries.setVisibility(isCurrentUserHost ? View.VISIBLE : View.GONE);
                buttonOpenEntries.setVisibility(isCurrentUserHost ? View.VISIBLE : View.GONE);
                invalidateOptionsMenu(); // Important to re-evaluate the menu for edit button


                // --- Load Requested Volunteers (Unapproved) ---
                requestedVolunteerIds.clear();
                List<String> unapprovedIds = (List<String>) snapshot.get("unApprovedVolunteerIds");
                if (unapprovedIds != null) {
                    requestedVolunteerIds.addAll(unapprovedIds);
                }

                if (requestedVolunteerIds.isEmpty()) {
                    Log.d(TAG, "No unapproved volunteers found.");
                    // Consider showing a "No pending requests" message in the UI
                }
                requestedVolunteersAdapter.notifyDataSetChanged();

            } else {
                Log.d(TAG, "Current data: null or event does not exist");
                Toast.makeText(HostEventDetailsActivity_Firestore.this, "Event data not found or was deleted.", Toast.LENGTH_LONG).show();
                finish(); // Event no longer exists, close details view
            }
        });
    }

    private void updateEntryStatusUI(boolean isClosed) {
        if (isClosed) {
            textViewEntryStatus.setText("Entries: CLOSED");
            textViewEntryStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            if (activeButtonStyle != null && inactiveButtonStyle != null) {
                buttonCloseEntries.setBackground(activeButtonStyle);
                buttonOpenEntries.setBackground(inactiveButtonStyle);
            }
            buttonCloseEntries.setEnabled(false);
            buttonOpenEntries.setEnabled(true);
        } else {
            textViewEntryStatus.setText("Entries: OPEN");
            textViewEntryStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            if (activeButtonStyle != null && inactiveButtonStyle != null) {
                buttonOpenEntries.setBackground(activeButtonStyle);
                buttonCloseEntries.setBackground(inactiveButtonStyle);
            }
            buttonOpenEntries.setEnabled(false);
            buttonCloseEntries.setEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show edit if the current user is the organizer
        if (currentOrganizerId != null && currentOrganizerId.equals(eventOrganizerIdFromSnapshot)) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_host_event_details, menu); // Create this menu XML file
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Dynamically show/hide based on whether the current user is the event organizer
        MenuItem editItem = menu.findItem(R.id.action_edit_event);
        if (editItem != null) {
            editItem.setVisible(currentOrganizerId != null && currentOrganizerId.equals(eventOrganizerIdFromSnapshot));
        }
        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_edit_event) { // Handle edit action
            if (currentOrganizerId.equals(eventOrganizerIdFromSnapshot)) {
                Intent intent = new Intent(HostEventDetailsActivity_Firestore.this, CreateEventActivity.class);
                intent.putExtra(CreateEventActivity.EXTRA_EDIT_EVENT_ID, currentEventId); // Pass event ID for editing
                editEventLauncher.launch(intent);
            } else {
                Toast.makeText(this, "You are not authorized to edit this event.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        // ... handle other menu items if any
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventDetailsListenerRegistration != null) {
            eventDetailsListenerRegistration.remove();
        }
    }
}
