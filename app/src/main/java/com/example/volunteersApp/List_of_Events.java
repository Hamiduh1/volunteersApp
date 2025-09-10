package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
// import android.widget.ProgressBar; // For showing loading state
// import android.widget.TextView; // For empty state message
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.models.EventModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch; // For more complex multi-deletes if needed outside transaction

import java.util.ArrayList;
import java.util.List;
import java.util.Objects; // For requireNonNull

public class List_of_Events extends AppCompatActivity implements HostedEventAdapter.OnEventListener {

    private static final String TAG = "ListOfEventsActivity"; // Standardized TAG

    // Firestore Constants
    private static final String USERS_COLLECTION = "users";
    private static final String HOSTED_EVENTS_SUBCOLLECTION = "hosted_events";
    private static final String EVENTS_COLLECTION = "events";
    private static final String FIELD_EVENT_TIMESTAMP = "eventTimestamp"; // Field to order by in hosted_events

    // UI Elements
    private RecyclerView recyclerViewHostedEvents;
    // private ProgressBar progressBarLoadingEvents; // Optional
    // private TextView textViewEmptyEvents; // Optional

    private HostedEventAdapter eventAdapter;
    private List<EventModel> hostedEventList; // Holds the full EventModel objects

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_of_events); // Ensure this layout exists

        // progressBarLoadingEvents = findViewById(R.id.progressBar_list_events); // Initialize if added
        // textViewEmptyEvents = findViewById(R.id.textView_empty_list_events); // Initialize if added

        Toolbar toolbar = findViewById(R.id.toolbar_list_of_events); // Ensure this ID exists
        setSupportActionBar(toolbar);
        // Set title using string resource
        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.title_my_hosted_events);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        recyclerViewHostedEvents = findViewById(R.id.recyclerViewHostedEvents); // Ensure this ID exists
        recyclerViewHostedEvents.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHostedEvents.setHasFixedSize(true); // Optimization if item sizes don't change

        hostedEventList = new ArrayList<>();
        eventAdapter = new HostedEventAdapter(this, this); // Pass context and listener
        recyclerViewHostedEvents.setAdapter(eventAdapter);

        // Optional: Initially hide list, show progress bar
        // if (progressBarLoadingEvents != null) progressBarLoadingEvents.setVisibility(View.VISIBLE);
        // recyclerViewHostedEvents.setVisibility(View.GONE);
        // if (textViewEmptyEvents != null) textViewEmptyEvents.setVisibility(View.GONE);

        loadHostedEvents();
    }

    private void showLoading(boolean isLoading) {
        // if (progressBarLoadingEvents != null) {
        //     progressBarLoadingEvents.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // }
        // if (!isLoading) { // Only adjust list/empty view visibility when loading is finished
        //     recyclerViewHostedEvents.setVisibility(hostedEventList.isEmpty() ? View.GONE : View.VISIBLE);
        //     if (textViewEmptyEvents != null) {
        //         textViewEmptyEvents.setVisibility(hostedEventList.isEmpty() ? View.VISIBLE : View.GONE);
        //         if (hostedEventList.isEmpty()) {
        //             textViewEmptyEvents.setText(R.string.no_hosted_events_found);
        //         }
        //     }
        // } else { // While loading, hide both
        //    recyclerViewHostedEvents.setVisibility(View.GONE);
        //    if (textViewEmptyEvents != null) textViewEmptyEvents.setVisibility(View.GONE);
        // }
    }


    private void loadHostedEvents() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.toast_please_log_in_to_see_events, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No user logged in, cannot load hosted events.");
            // Consider redirecting to login activity or finishing
            // startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        String userId = currentUser.getUid();
        Log.d(TAG, "Loading hosted event IDs for user: " + userId);
        // showLoading(true); // Show progress bar

        db.collection(USERS_COLLECTION).document(userId).collection(HOSTED_EVENTS_SUBCOLLECTION)
                .orderBy(FIELD_EVENT_TIMESTAMP, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No hosted event entries found for user: " + userId);
                        Toast.makeText(List_of_Events.this, R.string.toast_you_havent_hosted_events, Toast.LENGTH_LONG).show();
                        hostedEventList.clear();
                        eventAdapter.submitList(new ArrayList<>());
                        // showLoading(false); // Hide progress, show empty state
                        return;
                    }

                    Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " hosted event entries.");
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot hostedEventDoc : queryDocumentSnapshots) {
                        eventIds.add(hostedEventDoc.getId());
                    }
                    fetchFullEventDetails(eventIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching hosted event IDs for user: " + userId, e);
                    Toast.makeText(List_of_Events.this, R.string.toast_failed_to_load_hosted_events, Toast.LENGTH_SHORT).show();
                    hostedEventList.clear(); // Clear list on failure
                    eventAdapter.submitList(new ArrayList<>()); // Update adapter
                    // showLoading(false); // Hide progress
                });
    }

    private void fetchFullEventDetails(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            Log.d(TAG, "Event ID list is empty, no details to fetch.");
            hostedEventList.clear();
            eventAdapter.submitList(new ArrayList<>());
            // showLoading(false); // Hide progress, potentially show empty state if this was the only path
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String eventId : eventIds) {
            tasks.add(db.collection(EVENTS_COLLECTION).document(eventId).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            List<EventModel> fetchedEvents = new ArrayList<>();
            for (Object result : results) {
                if (result instanceof DocumentSnapshot) {
                    DocumentSnapshot eventDoc = (DocumentSnapshot) result;
                    if (eventDoc.exists()) {
                        try {
                            EventModel event = eventDoc.toObject(EventModel.class);
                            if (event != null) {
                                // @DocumentId in EventModel should handle setting the ID.
                                // If not, or for explicit control:
                                // event.setEventId(eventDoc.getId());
                                fetchedEvents.add(event);
                            } else {
                                Log.w(TAG, "Failed to convert DocumentSnapshot to EventModel for ID: " + eventDoc.getId());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error deserializing event document: " + eventDoc.getId(), e);
                        }
                    } else {
                        Log.w(TAG, "Event document with ID " + eventDoc.getId() + " not found in main " + EVENTS_COLLECTION + " collection.");
                        // This means an event ID in user's hosted_events doesn't exist in the main events collection
                        // Consider how to handle this - e.g., cleanup stale reference (more complex) or just log.
                    }
                }
            }

            hostedEventList.clear();
            hostedEventList.addAll(fetchedEvents); // List is now ordered as per eventIds (from hosted_events query)
            eventAdapter.submitList(new ArrayList<>(hostedEventList)); // Submit a new copy

            if (fetchedEvents.isEmpty() && !eventIds.isEmpty()) {
                Toast.makeText(List_of_Events.this, R.string.toast_some_event_details_not_loaded, Toast.LENGTH_LONG).show();
            } else if (fetchedEvents.isEmpty()) {
                Toast.makeText(List_of_Events.this, R.string.toast_no_events_to_display, Toast.LENGTH_SHORT).show();
            }
            // showLoading(false);

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching one or more full event details.", e);
            Toast.makeText(List_of_Events.this, R.string.toast_failed_to_load_event_details, Toast.LENGTH_SHORT).show();
            hostedEventList.clear();
            eventAdapter.submitList(new ArrayList<>());
            // showLoading(false);
        });
    }

    @Override
    public void onEventClick(@NonNull EventModel event) {
        Log.d(TAG, "Clicked event: " + event.getTitle() + " (ID: " + event.getEventId() + ")");
        Toast.makeText(List_of_Events.this,
                getString(R.string.toast_view_details_for_param, event.getTitle()),
                Toast.LENGTH_SHORT).show();
        // TODO: Navigate to an EventDetailActivity or similar
        // Intent intent = new Intent(List_of_Events.this, EventDetailActivity.class);
        // intent.putExtra("EVENT_ID", event.getEventId());
        // startActivity(intent);
    }

    @Override
    public void onOptionMenuClick(@NonNull EventModel event, @NonNull View anchorView) {
        Log.d(TAG, "Options menu clicked for event: " + event.getTitle());
        showPopupMenu(event, anchorView);
    }

    private void showPopupMenu(EventModel event, View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.hosted_event_options_menu, popup.getMenu()); // Ensure this menu exists

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit_event) {
                Toast.makeText(List_of_Events.this,
                        getString(R.string.toast_edit_param, event.getTitle()),
                        Toast.LENGTH_SHORT).show();
                // TODO: Implement navigation to an EditEventActivity
                // Intent intent = new Intent(List_of_Events.this, EditEventActivity.class);
                // intent.putExtra("EVENT_ID", event.getEventId());
                // startActivity(intent);
                return true;
            } else if (itemId == R.id.action_delete_event) {
                confirmDeleteEvent(event);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDeleteEvent(final EventModel event) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_delete_event)
                .setMessage(getString(R.string.dialog_message_confirm_delete_param, event.getTitle()))
                .setPositiveButton(R.string.button_delete, (dialog, which) -> deleteEventFromFirestore(event))
                .setNegativeButton(R.string.button_cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert) // Optional: add an icon
                .show();
    }

    private void deleteEventFromFirestore(final EventModel eventToDelete) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || eventToDelete.getEventId() == null || eventToDelete.getEventId().isEmpty()) {
            Toast.makeText(this, R.string.toast_error_cannot_delete_event_user_or_id_missing, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot delete event. User: " + (currentUser != null ? currentUser.getUid() : "null") +
                    ", Event ID: " + (eventToDelete.getEventId() != null ? eventToDelete.getEventId() : "null"));
            return;
        }
        String userId = currentUser.getUid();
        String eventId = eventToDelete.getEventId();

        Log.d(TAG, "Attempting to delete event: " + eventId + " for user: " + userId);
        // showLoading(true); // Indicate activity

        db.runTransaction(transaction -> {
            DocumentReference eventRefGlobal = db.collection(EVENTS_COLLECTION).document(eventId);
            DocumentReference userHostedEventRef = db.collection(USERS_COLLECTION).document(userId)
                    .collection(HOSTED_EVENTS_SUBCOLLECTION).document(eventId);

            // It's good practice to check if the main event document exists before trying to delete,
            // though the transaction will handle it gracefully if it doesn't.
            // DocumentSnapshot eventSnapshot = transaction.get(eventRefGlobal);
            // if (!eventSnapshot.exists()) {
            //     Log.w(TAG, "Event " + eventId + " not found in /" + EVENTS_COLLECTION +
            //           " collection during delete transaction. Proceeding with user list deletion.");
            // }

            transaction.delete(eventRefGlobal);
            transaction.delete(userHostedEventRef);
            return null; // Transaction success
        }).addOnSuccessListener(aVoid -> {
            Log.i(TAG, "Event " + eventId + " and its reference in user's " + HOSTED_EVENTS_SUBCOLLECTION + " deleted.");
            Toast.makeText(List_of_Events.this,
                    getString(R.string.toast_event_deleted_successfully_param, eventToDelete.getTitle()),
                    Toast.LENGTH_SHORT).show();

            // Remove from local list and update adapter
            // Create a new list for DiffUtil to correctly detect changes.
            List<EventModel> updatedList = new ArrayList<>(hostedEventList);
            boolean removed = updatedList.removeIf(event -> event.getEventId().equals(eventId)); // More robust removal
            if (removed) {
                hostedEventList = updatedList;
                eventAdapter.submitList(new ArrayList<>(hostedEventList));
            } else {
                Log.w(TAG, "Event " + eventId + " was not found in local list after successful Firestore deletion.");
                loadHostedEvents(); // Reload to ensure consistency if local list was out of sync
            }
            // showLoading(false);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to delete event " + eventId, e);
            Toast.makeText(List_of_Events.this,
                    getString(R.string.toast_failed_to_delete_event_param, eventToDelete.getTitle(), e.getMessage()),
                    Toast.LENGTH_LONG).show();
            // showLoading(false);
            // Consider reloading events to ensure UI consistency if the delete failed.
            // loadHostedEvents();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Finishes current activity and goes to the previous one in the stack
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Example String Resources to add to res/values/strings.xml:
    // <string name="title_my_hosted_events">My Hosted Events</string>
    // <string name="toast_please_log_in_to_see_events">Please log in to see your events.</string>
    // <string name="toast_you_havent_hosted_events">You haven\'t hosted any events yet.</string>
    // <string name="toast_failed_to_load_hosted_events">Failed to load hosted events.</string>
    // <string name="toast_some_event_details_not_loaded">Some event details could not be loaded.</string>
    // <string name="toast_no_events_to_display">No events to display.</string>
    // <string name="toast_failed_to_load_event_details">Failed to load details for some events.</string>
    // <string name="toast_view_details_for_param">View details for: %1$s</string>
    // <string name="toast_edit_param">Edit: %1$s</string>
    // <string name="dialog_title_delete_event">Delete Event</string>
    // <string name="dialog_message_confirm_delete_param">Are you sure you want to delete \'%1$s\'?</string>
    // <string name="button_delete">Delete</string>
    // <string name="button_cancel">Cancel</string>
    // <string name="toast_error_cannot_delete_event_user_or_id_missing">Error: Cannot delete event. User not logged in or event ID missing.</string>
    // <string name="toast_event_deleted_successfully_param">\'%1$s\' deleted successfully.</string>
    // <string name="toast_failed_to_delete_event_param">Failed to delete \'%1$s\'. Error: %2$s</string>
    // <string name="no_hosted_events_found">You haven\'t hosted any events yet.\nTap the + button to create one!</string> <!-- For empty state TextView -->

}
