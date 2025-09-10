// File: WaitlistedEventAdapterListView.java
package com.example.volunteersApp;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast; // For user feedback

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.volunteersApp.models.EventModel;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WaitlistedEventAdapterListView extends ArrayAdapter<WaitlistedEvent> {

    private static final String TAG = "WaitlistedEventAdapter"; // Added TAG for logging
    private final Context context;
    // The list is managed by the parent ArrayAdapter class.

    // ViewHolder class for performance optimization
    private static class ViewHolder {
        TextView eventNameTextView;
        TextView eventDateTextView;
        TextView eventLocationTextView;
        // Add other views from your list item layout here if needed
    }

    public WaitlistedEventAdapterListView(@NonNull Context context, @NonNull List<WaitlistedEvent> events) {
        // Ensure you have a layout file that will be used for each item in the list.
        // If R.layout.simple_list_item_event is not your layout, change it.
        // For example, if you created 'custom_event_list_item.xml', use R.layout.custom_event_list_item
        super(context, R.layout.simple_list_item_event, events); // IMPORTANT: Verify this layout ID
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        View listItemView = convertView;

        if (listItemView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            // IMPORTANT: Ensure R.layout.simple_list_item_event is the correct layout file.
            // This layout file should contain the TextViews with the IDs used below.
            listItemView = inflater.inflate(R.layout.simple_list_item_event, parent, false);

            holder = new ViewHolder();
            // IMPORTANT: Make sure these IDs (e.g., R.id.event_name_textview)
            // exist in your 'simple_list_item_event.xml' or your custom item layout file.
            holder.eventNameTextView = listItemView.findViewById(R.id.event_name_textview);
            holder.eventDateTextView = listItemView.findViewById(R.id.event_date_textview);
            holder.eventLocationTextView = listItemView.findViewById(R.id.event_location_textview);
            listItemView.setTag(holder);
        } else {
            holder = (ViewHolder) listItemView.getTag();
        }

        final WaitlistedEvent currentEvent = getItem(position); // Get item from ArrayAdapter

        if (currentEvent != null && currentEvent.getEventDetails() != null) {
            EventModel eventDetails = currentEvent.getEventDetails();

            if (holder.eventNameTextView != null) {
                holder.eventNameTextView.setText(eventDetails.getTitle());
            } else {
                Log.w(TAG, "eventNameTextView is null in ViewHolder. Check layout file and ID.");
            }

            if (holder.eventDateTextView != null) {
                String displayDateTime = "";

                if (eventDetails != null && eventDetails.getEventDateTime() != null) {
                    try {
                        com.google.firebase.Timestamp eventFirebaseTimestamp = eventDetails.getEventDateTime();
                        java.util.Date eventJavaDate = eventFirebaseTimestamp.toDate();
                        java.text.SimpleDateFormat dateTimeFormat =
                                new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                        displayDateTime = dateTimeFormat.format(eventJavaDate);
                    } catch (Exception e) {
                        Log.e(TAG, "Error formatting event date/time for event: " +
                                (eventDetails.getTitle() != null ? eventDetails.getTitle() : "Unknown"), e);
                        // --- CORRECTED ---
                        displayDateTime = context.getString(R.string.date_not_available);
                    }
                } else {
                    Log.w(TAG, "Event date/time (Timestamp) is null for event: " +
                            (eventDetails != null && eventDetails.getTitle() != null ? eventDetails.getTitle() : "Unknown"));
                    // --- CORRECTED ---
                    displayDateTime = context.getString(R.string.date_not_available);
                }
                holder.eventDateTextView.setText(displayDateTime.trim());
            }

            else {
                Log.w(TAG, "eventDateTextView is null in ViewHolder. Check layout file and ID.");
            }

            if (holder.eventLocationTextView != null) {
                holder.eventLocationTextView.setText(eventDetails.getLocationName());
            } else {
                Log.w(TAG, "eventLocationTextView is null in ViewHolder. Check layout file and ID.");
            }

            // --- THIS IS THE KEY UPDATE ---
            // Set OnClickListener for the entire list item view
            listItemView.setOnClickListener(v -> {
                // Double-check currentEvent is still valid, though generally it should be for this position
                if (currentEvent != null) {
                    String eventId = currentEvent.getEventId();
                    String organizerId = currentEvent.getOrganizerId(); // Get organizerId from the WaitlistedEvent object

                    if (eventId != null && !eventId.isEmpty() && organizerId != null && !organizerId.isEmpty()) {
                        Intent intent = new Intent(context, EventDescription3.class); // Target EventDescription3
                        intent.putExtra("EventId", eventId);
                        intent.putExtra("organizerId", organizerId);      // Pass organizerId directly
                        Log.d(TAG, "Starting EventDescription3 for EventID: " + eventId + ", organizerId: " + organizerId);
                        context.startActivity(intent);
                    } else {
                        Log.e(TAG, "EventId or organizerId is missing for event: " + (eventDetails != null ? eventDetails.getTitle() : "Unknown Event (details missing)"));
                        Toast.makeText(context, "Cannot open event details. Required information is missing.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "currentEvent is null inside OnClickListener for position: " + position);
                    Toast.makeText(context, "Error opening event details.", Toast.LENGTH_SHORT).show();
                }
            });
            // --- END OF KEY UPDATE ---

        } else {
            // Handle the case where currentEvent or its details are null
            if (holder.eventNameTextView != null) {
                // Ensure you have a string resource like: <string name="event_data_not_available_placeholder">Event data not available</string>
                holder.eventNameTextView.setText(R.string.event_data_not_available_placeholder);
            }
            if (holder.eventDateTextView != null) {
                holder.eventDateTextView.setText("");
            }
            if (holder.eventLocationTextView != null) {
                holder.eventLocationTextView.setText("");
            }
            listItemView.setOnClickListener(null); // Remove click listener if data is invalid or incomplete
        }

        return listItemView;
    }
}