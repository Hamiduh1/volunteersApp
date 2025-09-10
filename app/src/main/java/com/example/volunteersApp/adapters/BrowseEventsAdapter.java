package com.example.volunteersApp.adapters;

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

import com.bumptech.glide.Glide;
import com.example.volunteersApp.R;
import com.example.volunteersApp.models.Event; // Using the Event model

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class BrowseEventsAdapter extends ListAdapter<Event, BrowseEventsAdapter.EventViewHolder> {

    private static final String TAG = "BrowseEventsAdapter";
    private final OnItemClickListener onItemClickListener;
    private final Context context;

    public interface OnItemClickListener {
        void onItemClick(Event event);
    }

    public BrowseEventsAdapter(Context context, @NonNull OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = listener;
    }

    private static final DiffUtil.ItemCallback<Event> DIFF_CALLBACK = new DiffUtil.ItemCallback<Event>() {
        @Override
        public boolean areItemsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
            // Assumes getEventId() is the unique identifier from your Event model
            return oldItem.getEventId() != null && oldItem.getEventId().equals(newItem.getEventId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
            // For robust comparison, ensure Event.java has a well-implemented equals() method.
            // If not, you'll need to compare individual fields here.
            // Example of manual comparison (if Event.equals() is not overridden or sufficient):
            /*
            return Objects.equals(oldItem.getTitle(), newItem.getTitle()) &&
                   Objects.equals(oldItem.getEventTimestamp(), newItem.getEventTimestamp()) &&
                   Objects.equals(oldItem.getLocationName(), newItem.getLocationName()) &&
                   Objects.equals(oldItem.getOrganizerName(), newItem.getOrganizerName()) &&
                   Objects.equals(oldItem.getCategory(), newItem.getCategory()) &&
                   oldItem.getVolunteersNeeded() == newItem.getVolunteersNeeded() &&
                   oldItem.getVolunteersRegistered() == newItem.getVolunteersRegistered() &&
                   Objects.equals(oldItem.getStatus(), newItem.getStatus()) &&
                   Objects.equals(oldItem.getImageUrl(), newItem.getImageUrl());
            */
            return oldItem.equals(newItem); // Relies on Event.java having a proper equals() method
        }
    };

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure this layout resource ID (item_event_card) is correct for displaying an event.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_card, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event currentEvent = getItem(position);

        if (currentEvent == null) {
            Log.e(TAG, "currentEvent is null at position: " + position);
            // Handle this case gracefully in the UI, e.g., show placeholder or hide item
            // For now, just logging and returning, but you might want to clear views in holder.
            // Example:
            // holder.eventName.setText("Data unavailable");
            // holder.eventImage.setVisibility(View.GONE); ... etc.
            return;
        }

        holder.bind(currentEvent, onItemClickListener, context);
    }

    public static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView eventImage;
        TextView eventName;
        TextView eventDateTime;
        TextView eventLocation;
        TextView eventOrganizer;
        TextView eventCategory;
        TextView eventSlots;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ensure these view IDs match the IDs in your R.layout.item_event_card
            eventImage = itemView.findViewById(R.id.imageViewEventCardImage);
            eventName = itemView.findViewById(R.id.textViewEventCardName);
            eventDateTime = itemView.findViewById(R.id.textViewEventCardDateTime);
            eventLocation = itemView.findViewById(R.id.textViewEventCardLocation);
            eventOrganizer = itemView.findViewById(R.id.textViewEventCardOrganizer);
            eventCategory = itemView.findViewById(R.id.textViewEventCardCategory);
            eventSlots = itemView.findViewById(R.id.textViewEventCardSlots);
        }

        void bind(final Event event, final OnItemClickListener clickListener, Context context) {
            eventName.setText(event.getTitle());
            eventLocation.setText(event.getLocationName());

            if (event.getEventTimestamp() != null) {
                Date eventJavaDate = event.getEventTimestamp().toDate(); // Convert Firestore Timestamp
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
                eventDateTime.setText(sdf.format(eventJavaDate));
            } else {
                eventDateTime.setText("Date/Time not specified");
            }

            if (event.getOrganizerName() != null && !event.getOrganizerName().isEmpty()) {
                eventOrganizer.setText("Organized by: " + event.getOrganizerName());
                eventOrganizer.setVisibility(View.VISIBLE);
            } else {
                eventOrganizer.setVisibility(View.GONE);
            }

            if (event.getCategory() != null && !event.getCategory().isEmpty()) {
                eventCategory.setText(event.getCategory());
                eventCategory.setVisibility(View.VISIBLE);
            } else {
                eventCategory.setVisibility(View.GONE);
            }

            // Displaying volunteer slots information
            int registered = event.getVolunteersRegistered();
            int needed = event.getVolunteersNeeded();
            String slotsText;
            if (needed > 0) {
                slotsText = registered + "/" + needed + " Slots";
            } else {
                // If 0 needed, could mean unlimited or just tracking registered
                slotsText = "Registered: " + registered;
                // Alternatively, if 0 means unlimited: slotsText = "Unlimited Slots";
            }
            eventSlots.setText(slotsText);


            if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
                eventImage.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(event.getImageUrl())
                        .placeholder(R.drawable.ic_image_placeholder) // Make sure this placeholder exists
                        .error(R.drawable.ic_image_broken_placeholder)   // Make sure this error placeholder exists
                        .into(eventImage);
            } else {
                // eventImage.setImageResource(R.drawable.ic_event_default_placeholder); // Optional: set a default image
                eventImage.setVisibility(View.GONE); // Or set a default placeholder
            }

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(event);
                }
            });
        }
    }
}

