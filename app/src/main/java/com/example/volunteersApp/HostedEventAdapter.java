package com.example.volunteersApp;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteersApp.models.EventModel;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date; // Needed for eventTimestamp.toDate()
import java.util.Locale;

public class HostedEventAdapter extends ListAdapter<EventModel, HostedEventAdapter.EventViewHolder> {

    private static final String TAG = "HostedEventAdapter";
    private final OnEventListener onEventListener;
    private final SimpleDateFormat displayDateTimeFormat =
            new SimpleDateFormat("EEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault());
    private final Context context; // Now used for getString

    // String resources for placeholders
    private final String strErrorEventDataMissing;
    private final String strEventNameNotAvailable;
    private final String strDateInvalid;
    private final String strDateNotAvailable;
    private final String strLocationNotAvailable;
    private final String strVolunteerInfoNotSpecified;


    public HostedEventAdapter(@NonNull Context context, @NonNull OnEventListener onEventListener) {
        super(DIFF_CALLBACK);
        this.context = context; // Store context for accessing resources
        this.onEventListener = onEventListener;

        // Initialize string resources once
        this.strErrorEventDataMissing = context.getString(R.string.error_event_data_missing);
        this.strEventNameNotAvailable = context.getString(R.string.event_name_not_available);
        this.strDateInvalid = context.getString(R.string.date_invalid);
        this.strDateNotAvailable = context.getString(R.string.date_not_available);
        this.strLocationNotAvailable = context.getString(R.string.location_not_available);
        this.strVolunteerInfoNotSpecified = context.getString(R.string.volunteer_info_not_specified);
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hosted_event, parent, false);
        return new EventViewHolder(view, onEventListener);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventModel event = getItem(position);

        if (event == null) {
            Log.w(TAG, "Event object is null at position: " + position);
            holder.eventName.setText(strErrorEventDataMissing);
            holder.eventDateTime.setVisibility(View.GONE);
            holder.eventLocation.setVisibility(View.GONE);
            holder.locationIcon.setVisibility(View.GONE);
            holder.volunteersInfo.setVisibility(View.GONE);
            return;
        }


        // Event Name
        holder.eventName.setText(event.getTitle() != null ? event.getTitle() : strEventNameNotAvailable);

        // Date and Time
        Timestamp eventFirebaseTimestamp = event.getEventDateTime(); // Corrected based on EventModel
        if (eventFirebaseTimestamp != null) {
            try {
                Date eventJavaDate = eventFirebaseTimestamp.toDate();
                holder.eventDateTime.setText(displayDateTimeFormat.format(eventJavaDate));
                holder.eventDateTime.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                Log.e(TAG, "Error formatting timestamp: " + eventFirebaseTimestamp.toString(), e);
                holder.eventDateTime.setText(strDateInvalid);
                holder.eventDateTime.setVisibility(View.VISIBLE);
            }
        } else {
            holder.eventDateTime.setText(strDateNotAvailable);
            holder.eventDateTime.setVisibility(View.VISIBLE);
        }

        // Location
        if (event.getLocationName() != null && !event.getLocationName().isEmpty()) {
            holder.eventLocation.setText(event.getLocationName());
            holder.eventLocation.setVisibility(View.VISIBLE);
            holder.locationIcon.setVisibility(View.VISIBLE);
        } else {
            holder.eventLocation.setText(strLocationNotAvailable);
            holder.locationIcon.setVisibility(View.GONE);
            holder.eventLocation.setVisibility(View.GONE);
        }

        // Volunteers Info
        // --- MODIFIED HERE ---
        Integer registeredCount = event.getParticipantsCount(); // Use the correct getter from EventModel
        Integer limit = event.getVolunteerLimit();          // Assuming this getter exists in EventModel

        if (registeredCount != null && limit != null && limit > 0) {
            String volunteersText = String.format(Locale.getDefault(), "%d/%d Volunteers", registeredCount, limit);
            holder.volunteersInfo.setText(volunteersText);
            holder.volunteersInfo.setVisibility(View.VISIBLE);
        } else if (registeredCount != null) {
            String volunteersText = String.format(Locale.getDefault(), "%d Volunteers Registered", registeredCount);
            holder.volunteersInfo.setText(volunteersText);
            holder.volunteersInfo.setVisibility(View.VISIBLE);
        } else {
            holder.volunteersInfo.setText(strVolunteerInfoNotSpecified);
            holder.volunteersInfo.setVisibility(View.VISIBLE);
        }
    }
    public class EventViewHolder extends RecyclerView.ViewHolder {
        TextView eventName, eventDateTime, eventLocation, volunteersInfo;
        ImageView locationIcon, optionsMenu;

        EventViewHolder(@NonNull View itemView, OnEventListener listener) {
            super(itemView);
            eventName = itemView.findViewById(R.id.textView_item_event_name);
            eventDateTime = itemView.findViewById(R.id.textView_item_event_date_time);
            locationIcon = itemView.findViewById(R.id.imageView_location_icon);
            eventLocation = itemView.findViewById(R.id.textView_item_event_location);
            optionsMenu = itemView.findViewById(R.id.imageView_event_options);
            volunteersInfo = itemView.findViewById(R.id.textView_item_volunteers_info);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                // Check if position is valid and listener is not null
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    EventModel clickedEvent = getItem(position);
                    if (clickedEvent != null) { // Ensure event is not null
                        listener.onEventClick(clickedEvent);
                    } else {
                        Log.w(TAG, "Clicked item at position " + position + " but event data was null.");
                    }
                }
            });

            if (optionsMenu != null) {
                optionsMenu.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (listener != null && position != RecyclerView.NO_POSITION) {
                        EventModel clickedEvent = getItem(position);
                        if (clickedEvent != null) { // Ensure event is not null
                            listener.onOptionMenuClick(clickedEvent, v);
                        } else {
                            Log.w(TAG, "Options menu clicked for item at position " + position + " but event data was null.");
                        }
                    }
                });
            } else {
                Log.w(TAG, "optionsMenu (R.id.imageView_event_options) not found in item_hosted_event.xml. Options will not work.");
            }
        }
    }

    public interface OnEventListener {
        void onEventClick(@NonNull EventModel event); // Added @NonNull for clarity
        void onOptionMenuClick(@NonNull EventModel event, @NonNull View anchorView); // Added @NonNull
    }

    private static final DiffUtil.ItemCallback<EventModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<EventModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull EventModel oldItem, @NonNull EventModel newItem) {
                    // eventId should be non-null for persisted events.
                    // If either is null, they can't be the "same" item unless they are the same instance.
                    if (oldItem.getEventId() != null && newItem.getEventId() != null) {
                        return oldItem.getEventId().equals(newItem.getEventId());
                    }
                    // Fallback for items that might not have IDs yet (e.g., newly created, not yet saved)
                    // or if one ID is null. Consider if this object identity check is what you want.
                    return oldItem == newItem;
                }

                @Override
                public boolean areContentsTheSame(@NonNull EventModel oldItem, @NonNull EventModel newItem) {
                    // Relies on EventModel having a well-defined equals() method that compares relevant content.
                    return oldItem.equals(newItem);
                }
            };
}

