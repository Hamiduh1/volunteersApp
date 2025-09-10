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

// Firestore imports
import com.google.firebase.Timestamp; // For EventModel's date/time
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.volunteersApp.models.EventModel;
// Assuming VolunteerEventItem holds an EventModel instance and an eventId (Firestore Document ID)

import java.text.SimpleDateFormat;
import java.util.Date; // For converting Timestamp
import java.util.List;
import java.util.Locale;

public class EventList2 extends ArrayAdapter<VolunteerEventItem> {

    private static final String TAG = "EventList2Adapter"; // Standardized TAG
    private final Context context;
    private final FirebaseFirestore db;
    private final SimpleDateFormat displayDateTimeFormat =
            new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

    // String resources for placeholders
    private final String strEventDataMissing;
    private final String strDateInvalid;
    private final String strDateNotAvailable;
    private final String strLocationNotAvailable;
    private final String strEventNameNotAvailable;
    private final String strErrorCannotOpenEventDetails;
    private final String strEventsCollectionName; // For Firestore collection


    public EventList2(@NonNull Context context, @NonNull List<VolunteerEventItem> eventItemList) {
        super(context, R.layout.customlayout, eventItemList); // Ensure customlayout.xml is correct
        this.context = context;
        this.db = FirebaseFirestore.getInstance();

        // Initialize string resources
        this.strEventDataMissing = context.getString(R.string.event_data_missing);
        this.strDateInvalid = context.getString(R.string.date_invalid);
        this.strDateNotAvailable = context.getString(R.string.date_not_available);
        this.strLocationNotAvailable = context.getString(R.string.location_not_available);
        this.strEventNameNotAvailable = context.getString(R.string.event_name_not_available);
        this.strErrorCannotOpenEventDetails = context.getString(R.string.error_cannot_open_event_details);
        this.strEventsCollectionName = context.getString(R.string.firestore_collection_events); // e.g., "events"
    }

    private static class ViewHolder {
        TextView textViewEventName;
        TextView textViewEventDateTime; // Renamed for clarity
        TextView textViewEventLocation;
        RatingBar ratingBarEvent; // Renamed for clarity

        String currentEventIdForRating;
        ListenerRegistration ratingListenerRegistration;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        View listItemView = convertView;

        if (listItemView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            listItemView = inflater.inflate(R.layout.customlayout, parent, false);
            holder = new ViewHolder();
            holder.textViewEventName = listItemView.findViewById(R.id.textView_eventName);
            holder.textViewEventDateTime = listItemView.findViewById(R.id.textView_eventDate); // Ensure ID matches
            holder.textViewEventLocation = listItemView.findViewById(R.id.textView_eventLocation);
            holder.ratingBarEvent = listItemView.findViewById(R.id.avg_rating); // Ensure ID matches
            listItemView.setTag(holder);
        } else {
            holder = (ViewHolder) listItemView.getTag();
            if (holder.ratingListenerRegistration != null) {
                holder.ratingListenerRegistration.remove();
                holder.ratingListenerRegistration = null;
            }
        }

        final VolunteerEventItem currentItem = getItem(position);

        if (currentItem == null || currentItem.getEventDetails() == null || currentItem.getEventId() == null || currentItem.getEventId().isEmpty()) {
            Log.w(TAG, "VolunteerEventItem or its core details are null/empty at position: " + position);
            holder.textViewEventName.setText(strEventDataMissing);
            holder.textViewEventDateTime.setText("");
            holder.textViewEventLocation.setText("");
            if (holder.ratingBarEvent != null) {
                holder.ratingBarEvent.setVisibility(View.GONE);
            }
            if (listItemView != null) listItemView.setOnClickListener(null);
            return listItemView; // Return early if essential data is missing
        }

        final EventModel eventDetails = currentItem.getEventDetails();
        final String eventId = currentItem.getEventId(); // Firestore Document ID

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

        // --- RATING SETUP AND FETCH ---
        if (holder.ratingBarEvent != null) {
            holder.ratingBarEvent.setRating(0f);
            holder.ratingBarEvent.setVisibility(View.INVISIBLE); // Hide until fetched or if error

            holder.currentEventIdForRating = eventId;

            // Path to your event document in Firestore.
            DocumentReference eventDocRef = db.collection(strEventsCollectionName).document(eventId);

            // Detach any existing listener (already done if view is recycled, but good for clarity)
            // if (holder.ratingListenerRegistration != null) {
            // holder.ratingListenerRegistration.remove();
            // }

            holder.ratingListenerRegistration = eventDocRef.addSnapshotListener((snapshot, e) -> {
                // Critical check: Ensure this callback is for the item currently in this ViewHolder
                if (!eventId.equals(holder.currentEventIdForRating)) {
                    Log.d(TAG, "Rating data received for a recycled view. Current in holder: " +
                            holder.currentEventIdForRating + ", Data for: " + eventId);
                    return; // Stale update, ignore.
                }

                // Ensure ratingBarEvent is still valid (View might have been further recycled)
                if (holder.ratingBarEvent == null) {
                    Log.w(TAG, "RatingBar became null for eventId: " + eventId + " during listener callback.");
                    return;
                }

                if (e != null) {
                    Log.w(TAG, "Listen error for event rating: " + eventId, e);
                    holder.ratingBarEvent.setVisibility(View.GONE); // Hide on error
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Double ratingValue = snapshot.getDouble("avgRating"); // Assuming "avgRating" is the field name
                    if (ratingValue != null) {
                        holder.ratingBarEvent.setRating(ratingValue.floatValue());
                        holder.ratingBarEvent.setVisibility(View.VISIBLE);
                    } else {
                        Log.w(TAG, "avgRating field is null or not a number for event: " + eventId);
                        holder.ratingBarEvent.setRating(0f);
                        holder.ratingBarEvent.setVisibility(View.GONE); // Hide if rating is not valid
                    }
                } else {
                    Log.w(TAG, "Event document or avgRating field does not exist for event: " + eventId);
                    holder.ratingBarEvent.setRating(0f);
                    holder.ratingBarEvent.setVisibility(View.GONE); // Hide if no data
                }
            });
        }
        // --- END RATING FETCH ---

        // --- CLICK LISTENER ---
        if (listItemView != null) {
            listItemView.setOnClickListener(v -> {
                // Re-fetch eventId from currentItem just to be absolutely safe,
                // though holder.currentEventIdForRating should also be reliable here.
                final VolunteerEventItem clickedVolunteerItem = getItem(position); // Get current item again
                if (clickedVolunteerItem == null || clickedVolunteerItem.getEventId() == null || clickedVolunteerItem.getEventId().isEmpty()) {
                    Log.e(TAG, "Cannot start EventDescription2, EventId is missing for clicked item at position " + position);
                    // Toast.makeText(context, strErrorCannotOpenEventDetails, Toast.LENGTH_SHORT).show();
                    return;
                }
                final String clickedEventId = clickedVolunteerItem.getEventId();

                Intent intent = new Intent(context, EventDescription2.class);
                intent.putExtra("EventId", clickedEventId); // Pass Firestore Document ID

                // If organizerId is needed and is part of EventModel:
                // EventModel detailsForIntent = clickedVolunteerItem.getEventDetails();
                // if (detailsForIntent != null && detailsForIntent.getOrganizerId() != null) {
                //    intent.putExtra("OrganizerId", detailsForIntent.getOrganizerId());
                // }

                if (context instanceof Activity) {
                    ((Activity) context).startActivity(intent);
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            });
        }

        return listItemView;
    }
}

