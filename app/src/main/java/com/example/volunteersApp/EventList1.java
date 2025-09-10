package com.example.volunteersApp;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.volunteersApp.models.EventModel;
import com.google.firebase.Timestamp; // For eventDateTime

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventList1 extends ArrayAdapter<EventModel> {

    private static final String TAG = "EventList1Adapter"; // Standardized TAG
    private final Activity context;

    // String resources for placeholders (ensure these exist in strings.xml)
    private final String strEventDataNotAvailable;
    private final String strDateNotAvailable;
    private final String strLocationNotAvailable;
    private final String strNameNotAvailable;


    public EventList1(@NonNull Activity context, @NonNull List<EventModel> eventList) {
        super(context, 0, eventList);
        this.context = context;

        // Initialize string resources once
        this.strEventDataNotAvailable = context.getString(R.string.event_data_not_available_placeholder);
        this.strDateNotAvailable = context.getString(R.string.date_not_available); // Example string
        this.strLocationNotAvailable = context.getString(R.string.location_not_available); // Example string
        this.strNameNotAvailable = context.getString(R.string.name_not_available); // Example string
    }

    private static class ViewHolder {
        TextView textViewEventName; // Renamed for clarity to match common naming
        TextView textViewEventDateTime; // Renamed to reflect combined date and time
        TextView textViewEventLocation;
        RatingBar ratingBarEvent; // Renamed for clarity
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        View listViewItem = convertView;

        if (listViewItem == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            // Ensure R.layout.customlayout is the correct layout file for your list items
            // and that it contains views with IDs:
            // textView_eventName, textView_eventDateTime, textView_eventLocation, avg_rating
            listViewItem = inflater.inflate(R.layout.customlayout, parent, false);

            holder = new ViewHolder();
            holder.textViewEventName = listViewItem.findViewById(R.id.textView_eventName);
            holder.textViewEventDateTime = listViewItem.findViewById(R.id.textView_eventDate); // Assuming ID is textView_eventDate
            holder.textViewEventLocation = listViewItem.findViewById(R.id.textView_eventLocation);
            holder.ratingBarEvent = listViewItem.findViewById(R.id.avg_rating); // Assuming ID is avg_rating
            listViewItem.setTag(holder);
        } else {
            holder = (ViewHolder) listViewItem.getTag();
        }

        final EventModel currentEvent = getItem(position);

        if (currentEvent != null) {
            // --- EVENT NAME ---
            holder.textViewEventName.setText(currentEvent.getTitle() != null ? currentEvent.getTitle() : strNameNotAvailable);

            // --- DATE AND TIME DISPLAY (using EventModel's getEventDateTime()) ---
            if (currentEvent.getEventDateTime() != null) {
                try {
                    Timestamp eventTimestamp = currentEvent.getEventDateTime();
                    Date eventDate = eventTimestamp.toDate(); // Convert Firebase Timestamp to java.util.Date
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                    holder.textViewEventDateTime.setText(sdf.format(eventDate));
                } catch (Exception e) {
                    Log.e(TAG, "Error formatting event date and time for event: " + currentEvent.getTitle(), e);
                    holder.textViewEventDateTime.setText(strDateNotAvailable);
                }
            } else {
                holder.textViewEventDateTime.setText(strDateNotAvailable);
            }

            // --- LOCATION ---
            holder.textViewEventLocation.setText(currentEvent.getLocationName() != null ? currentEvent.getLocationName() : strLocationNotAvailable);


            // --- RATING DISPLAY ---
            // EventModel currently does NOT have an avgRating field or getAvgRating() method.
            // If you add it to EventModel (e.g., private Double avgRating; with getter/setter),
            // then you can uncomment and use the following:
            /*
            Double avgRating = currentEvent.getAvgRating(); // Assuming getAvgRating() exists
            if (avgRating != null) {
                holder.ratingBarEvent.setRating(avgRating.floatValue());
                holder.ratingBarEvent.setVisibility(View.VISIBLE);
            } else {
                holder.ratingBarEvent.setRating(0f);
                holder.ratingBarEvent.setVisibility(View.GONE); // Or View.INVISIBLE
            }
            */
            // For now, since it's missing in EventModel, hide the rating bar:
            if (holder.ratingBarEvent != null) { // Check if the view exists
                holder.ratingBarEvent.setVisibility(View.GONE);
                holder.ratingBarEvent.setRating(0f);
            }


            // --- CLICK LISTENER ---
            final String eventId = currentEvent.getEventId();
            if (eventId != null && !eventId.isEmpty()) {
                listViewItem.setOnClickListener(v -> {
                    Log.d(TAG, "Item clicked: " + currentEvent.getTitle() + " with ID: " + eventId);
                    // TODO: Verify HostEventDetailsActivity_Firestore.class is the correct destination
                    // And that it expects "EventId" as the extra key.
                    Intent intent = new Intent(context, HostEventDetailsActivity_Firestore.class);
                    intent.putExtra("EventId", eventId); // Consider using a public static final String for intent extras
                    context.startActivity(intent);
                });
            } else {
                Log.e(TAG, "EventId is null or empty for event: " +
                        (currentEvent.getTitle() != null ? currentEvent.getTitle() : "Unknown Event") +
                        " at position " + position + ". Disabling click listener.");
                listViewItem.setOnClickListener(null); // Remove listener if no valid ID
            }
        } else {
            // Handle case where currentEvent (getItem(position)) is null
            Log.e(TAG, "EventModel object is null at position: " + position);
            holder.textViewEventName.setText(strEventDataNotAvailable);
            holder.textViewEventDateTime.setText("");
            holder.textViewEventLocation.setText("");
            if (holder.ratingBarEvent != null) {
                holder.ratingBarEvent.setRating(0f);
                holder.ratingBarEvent.setVisibility(View.GONE);
            }
            listViewItem.setOnClickListener(null);
        }

        return listViewItem;
    }
}

