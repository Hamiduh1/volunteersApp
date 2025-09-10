package com.example.volunteersApp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RatingBar;
import android.widget.TextView;
// import android.widget.Toast; // Uncomment if needed

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp; // For EventModel's date/time
import com.google.firebase.firestore.DocumentReference;
// import com.google.firebase.firestore.DocumentSnapshot; // Not directly used in listener lambda parameter name
// import com.google.firebase.firestore.FirebaseFirestoreException; // Not directly used in listener lambda parameter name
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
// import com.google.firebase.firestore.QuerySnapshot; // Not used for document listener
import com.example.volunteersApp.R; // Ensure R is imported correctly
import com.example.volunteersApp.models.EventModel;

import java.text.SimpleDateFormat;
import java.util.Date; // For converting Timestamp
import java.util.List;
import java.util.Locale;

// Assuming ApprovedEvent class has getEventDetails() -> EventModel and getEventId() -> String
public class EventList4 extends ArrayAdapter<ApprovedEvent> {

    private static final String TAG = "EventList4Adapter"; // Standardized TAG
    private final Context context;
    private final FirebaseFirestore db;
    private final SimpleDateFormat displayDateTimeFormat =
            new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

    // String resources
    private final String strEventDataNotAvailable;
    private final String strDateInvalid;
    private final String strDateNotAvailable;
    private final String strLocationNotAvailable;
    private final String strEventNameNotAvailable;
    private final String strEventsCollectionName; // For Firestore collection


    public EventList4(@NonNull Context context, @NonNull List<ApprovedEvent> eventList) {
        super(context, R.layout.customlayout, eventList);
        this.context = context;
        this.db = FirebaseFirestore.getInstance();

        // Initialize string resources
        this.strEventDataNotAvailable = context.getString(R.string.event_data_not_available);
        this.strDateInvalid = context.getString(R.string.date_invalid);
        this.strDateNotAvailable = context.getString(R.string.date_not_available);
        this.strLocationNotAvailable = context.getString(R.string.location_not_available);
        this.strEventNameNotAvailable = context.getString(R.string.event_name_not_available);
        this.strEventsCollectionName = context.getString(R.string.firestore_collection_events); // e.g. "events"
    }

    private static class ViewHolder {
        TextView textViewEventName;
        TextView textViewEventDateTime; // Renamed for clarity
        TextView textViewEventLocation;
        RatingBar ratingBarAvg;

        String currentEventIdForRating;
        ListenerRegistration ratingListenerRegistration;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        View listViewItem = convertView;

        if (listViewItem == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            listViewItem = inflater.inflate(R.layout.customlayout, parent, false);
            holder = new ViewHolder();
            holder.textViewEventName = listViewItem.findViewById(R.id.textView_eventName);
            holder.textViewEventDateTime = listViewItem.findViewById(R.id.textView_eventDate); // Ensure ID matches
            holder.textViewEventLocation = listViewItem.findViewById(R.id.textView_eventLocation);
            holder.ratingBarAvg = listViewItem.findViewById(R.id.avg_rating); // Ensure ID matches
            listViewItem.setTag(holder);
        } else {
            holder = (ViewHolder) listViewItem.getTag();
            if (holder.ratingListenerRegistration != null) {
                holder.ratingListenerRegistration.remove();
                holder.ratingListenerRegistration = null;
            }
        }

        final ApprovedEvent currentApprovedEvent = getItem(position);

        if (currentApprovedEvent == null || currentApprovedEvent.getEventDetails() == null ||
                currentApprovedEvent.getEventId() == null || currentApprovedEvent.getEventId().isEmpty()) {
            Log.w(TAG, "ApprovedEvent or its core details are null/empty at position: " + position);
            holder.textViewEventName.setText(strEventDataNotAvailable);
            holder.textViewEventDateTime.setText("");
            holder.textViewEventLocation.setText("");
            if (holder.ratingBarAvg != null) {
                holder.ratingBarAvg.setVisibility(View.GONE);
            }
            if (listViewItem != null) listViewItem.setOnClickListener(null);
            return listViewItem; // Return early if essential data is missing
        }

        final com.example.volunteersApp.models.EventModel eventDetails = currentApprovedEvent.getEventDetails();
        final String eventId = currentApprovedEvent.getEventId();

        holder.textViewEventName.setText(eventDetails.getTitle() != null ? eventDetails.getTitle() : strEventNameNotAvailable);

        // --- DATE AND TIME DISPLAY (using EventModel's getEventDateTime()) ---
        if (eventDetails.getEventDateTime() != null) {
            try {
                Timestamp eventFirebaseTimestamp = eventDetails.getEventDateTime();
                Date eventJavaDate = eventFirebaseTimestamp.toDate();
                holder.textViewEventDateTime.setText(displayDateTimeFormat.format(eventJavaDate));
            } catch (Exception ex) {
                Log.e(TAG, "Error formatting event date for eventId: " + eventId, ex);
                holder.textViewEventDateTime.setText(strDateInvalid);
            }
        } else {
            holder.textViewEventDateTime.setText(strDateNotAvailable);
        }

        // --- LOCATION DISPLAY ---
        holder.textViewEventLocation.setText(eventDetails.getLocationName() != null ? eventDetails.getLocationName() : strLocationNotAvailable);

        // --- RATING BAR LOGIC ---
        if (holder.ratingBarAvg != null) {
            holder.ratingBarAvg.setRating(0f); // Default state
            holder.ratingBarAvg.setVisibility(View.INVISIBLE); // Hide until data is loaded or confirmed absent

            holder.currentEventIdForRating = eventId;

            // Path to your event document in Firestore.
            DocumentReference eventDocRef = db.collection(strEventsCollectionName).document(eventId);

            // Detach any existing listener (already done if view is recycled)
            // if (holder.ratingListenerRegistration != null) {
            // holder.ratingListenerRegistration.remove();
            // }

            holder.ratingListenerRegistration = eventDocRef.addSnapshotListener((snapshot, e) -> {
                if (!eventId.equals(holder.currentEventIdForRating)) {
                    Log.d(TAG, "Snapshot listener callback for a recycled view. Current: " + holder.currentEventIdForRating + ", Callback for: " + eventId);
                    return; // Stale update, ignore
                }

                // Ensure ratingBarAvg is still valid
                if (holder.ratingBarAvg == null) {
                    Log.w(TAG, "RatingBar (ratingBarAvg) became null for eventId: " + eventId + " during listener callback.");
                    return;
                }

                if (e != null) {
                    Log.w(TAG, "Listen error for event rating: " + eventId, e);
                    holder.ratingBarAvg.setVisibility(View.GONE);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Double ratingValue = snapshot.getDouble("avgRating"); // Firestore stores numbers as Double by default
                    if (ratingValue != null) {
                        holder.ratingBarAvg.setRating(ratingValue.floatValue());
                        holder.ratingBarAvg.setVisibility(View.VISIBLE);
                    } else {
                        Log.w(TAG, "avgRating is null or not a number for event: " + eventId);
                        holder.ratingBarAvg.setRating(0f);
                        holder.ratingBarAvg.setVisibility(View.GONE);
                    }
                } else {
                    Log.w(TAG, "Event document or avgRating field does not exist for event: " + eventId);
                    holder.ratingBarAvg.setRating(0f);
                    holder.ratingBarAvg.setVisibility(View.GONE);
                }
            });
        }
        // --- END RATING BAR LOGIC ---

        // --- CLICK LISTENER ---
        if (listViewItem != null) {
            listViewItem.setOnClickListener(v -> {
                // Re-fetch from currentApprovedEvent to be safe for eventId
                final ApprovedEvent clickedApprovedEvent = getItem(position);
                if (clickedApprovedEvent == null || clickedApprovedEvent.getEventId() == null || clickedApprovedEvent.getEventId().isEmpty()) {
                    Log.e(TAG, "Cannot start EventDescription4, EventId is missing for clicked item at position " + position);
                    // Consider showing a Toast message here if appropriate
                    return;
                }
                final String clickedEventId = clickedApprovedEvent.getEventId();

                Intent intent = new Intent(context, EventDescription4.class);
                intent.putExtra("EventId", clickedEventId);

                // If organizerId is still relevant and available in ApprovedEvent or its EventModel:
                // EventModel detailsForIntent = clickedApprovedEvent.getEventDetails();
                // if (detailsForIntent != null && detailsForIntent.getOrganizerId() != null) {
                // intent.putExtra("OrganizerId", detailsForIntent.getOrganizerId());
                // }

                if (context instanceof Activity) {
                    ((Activity) context).startActivity(intent);
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            });
        }
        return listViewItem;
    }

    /**
     * Call this method from your Activity/Fragment's onDestroy or onStop
     * if the adapter instance might live longer, to ensure all listeners are cleaned up.
     * For ListView adapters using a ViewHolder pattern like this,
     * the cleanup in getView's 'else' block (when a view is recycled) is crucial.
     * Explicitly iterating through all potential views to remove listeners is complex
     * and generally not standard for ArrayAdapter.
     */
    public void cleanupListeners() {
        // The primary cleanup is handled by removing the listener from the ViewHolder
        // when a view is recycled (in the 'else' block of `if (listViewItem == null)`).
        // For an ArrayAdapter, you don't typically have direct access to all active ViewHolders
        // to iterate and clean them up from outside getView().
        Log.d(TAG, "CleanupListeners called. Individual listeners are managed within getView for recycled views.");
        // If this adapter is expected to be held onto by an Activity/Fragment that outlives the ListView's
        // typical lifecycle, more complex listener management might be needed, or ideally,
        // the adapter's lifecycle should be tied to the view it serves.
    }
}

