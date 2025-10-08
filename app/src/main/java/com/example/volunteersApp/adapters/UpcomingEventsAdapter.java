package com.example.volunteersApp.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.ItemEventCardSmallBinding;
import com.example.volunteersApp.models.EventModel; // CORRECTED: Use EventModel consistently

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class UpcomingEventsAdapter extends RecyclerView.Adapter<UpcomingEventsAdapter.EventViewHolder> {

    // CORRECTED: The list now holds EventModel objects
    private List<EventModel> eventList;
    private final OnEventClickListener listener;

    public interface OnEventClickListener {
        // CORRECTED: The listener callback now provides an EventModel
        void onEventClick(EventModel event);
    }

    // CORRECTED: The constructor now accepts a List<EventModel>
    public UpcomingEventsAdapter(List<EventModel> eventList, OnEventClickListener listener) {
        this.eventList = eventList;
        this.listener = listener;
    }

    // *** THIS METHOD IS NOW CORRECT ***
    public void updateEvents(List<EventModel> newEvents) {
        this.eventList.clear(); // Clear the old list
        if (newEvents != null) {
            this.eventList.addAll(newEvents); // Add all the new events
        }
        notifyDataSetChanged(); // Tell the adapter to refresh the view
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEventCardSmallBinding binding = ItemEventCardSmallBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EventViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        // CORRECTED: Get an EventModel object from the list
        EventModel event = eventList.get(position);
        holder.bind(event, listener);
    }

    @Override
    public int getItemCount() {
        // This remains correct
        return eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        private final ItemEventCardSmallBinding binding;

        EventViewHolder(ItemEventCardSmallBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        // CORRECTED: The bind method now accepts an EventModel
        void bind(final EventModel event, final OnEventClickListener listener) {
            binding.textViewEventTitleSmall.setText(event.getTitle());

            // CORRECTED: Use the 'eventDateTime' field from EventModel
            if (event.getEventDateTime() != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
                binding.textViewEventDateSmall.setText(dateFormat.format(event.getEventDateTime().toDate()));
            } else {
                binding.textViewEventDateSmall.setText(R.string.date_time_not_available);
            }

            // CORRECTED: Use the 'imageUrl' field from EventModel
            if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(event.getImageUrl())
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .centerCrop()
                        .into(binding.imageViewEventSmall);
            } else {
                binding.imageViewEventSmall.setImageResource(R.drawable.ic_image_placeholder);
            }

            itemView.setOnClickListener(v -> listener.onEventClick(event));
        }
    }
}
