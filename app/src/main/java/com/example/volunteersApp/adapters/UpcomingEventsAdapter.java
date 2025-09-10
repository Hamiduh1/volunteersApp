package com.example.volunteersApp.adapters; // Your package

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.ItemEventCardSmallBinding; // ViewBinding for the item
import com.example.volunteersApp.models.EventDetails;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class UpcomingEventsAdapter extends RecyclerView.Adapter<UpcomingEventsAdapter.EventViewHolder> {

    private List<EventDetails> eventList;
    private OnEventClickListener listener;

    public interface OnEventClickListener {
        void onEventClick(EventDetails event);
    }

    public UpcomingEventsAdapter(List<EventDetails> eventList, OnEventClickListener listener) {
        this.eventList = eventList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the item layout
        ItemEventCardSmallBinding binding = ItemEventCardSmallBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EventViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventDetails event = eventList.get(position);
        holder.bind(event, listener);
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        // Use the binding class for views
        ItemEventCardSmallBinding binding;

        EventViewHolder(ItemEventCardSmallBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final EventDetails event, final OnEventClickListener listener) {
            binding.textViewEventNameSmall.setText(event.getTitle());

            if (event.getEventTimestamp() != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
                binding.textViewEventDateSmall.setText(dateFormat.format(event.getEventTimestamp().toDate()));
            } else {
                binding.textViewEventDateSmall.setText(R.string.date_time_not_available);
            }

            if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(event.getImageUrl())
                        .placeholder(R.drawable.ic_image_placeholder) // your placeholder
                        .error(R.drawable.ic_image_error) // your error placeholder
                        .centerCrop()
                        .into(binding.imageViewEventSmall);
            } else {
                // Set a default image or hide if no image URL
                binding.imageViewEventSmall.setImageResource(R.drawable.ic_image_placeholder);
            }

            itemView.setOnClickListener(v -> listener.onEventClick(event));
        }
    }
}

