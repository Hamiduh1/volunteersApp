package com.example.volunteersApp;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import java.util.Objects;

// Make this a top-level public class
public class VolunteerEventItemCallback extends DiffUtil.ItemCallback<VolunteerEventItem> {
    @Override
    public boolean areItemsTheSame(@NonNull VolunteerEventItem oldItem, @NonNull VolunteerEventItem newItem) {
        return Objects.equals(oldItem.getEventId(), newItem.getEventId());
    }

    @Override
    public boolean areContentsTheSame(@NonNull VolunteerEventItem oldItem, @NonNull VolunteerEventItem newItem) {
        return oldItem.equals(newItem); // Relies on VolunteerEventItem.equals()
    }
}