package com.example.volunteersApp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.databinding.ItemEventWithVolunteersSummaryBinding // Assuming ViewBinding
import com.example.volunteersApp.models.EventWithVolunteerCount

class EventsWithVolunteersSummaryAdapter(
    private val onItemClicked: (EventWithVolunteerCount) -> Unit
) : ListAdapter<EventWithVolunteerCount, EventsWithVolunteersSummaryAdapter.ViewHolder>(EventWithCountDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventWithVolunteersSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bind(item)
        }
    }

    class ViewHolder(
        private val binding: ItemEventWithVolunteersSummaryBinding,
        private val onItemClicked: (EventWithVolunteerCount) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EventWithVolunteerCount) {
            binding.textViewEventTitleWithCount.text =
                "${item.event.title ?: "N/A"} (${item.confirmedVolunteersCount} confirmed)"

            binding.textViewEventLocationWithCount.text = item.event.locationName ?: "Location N/A"

            binding.eventWithVolunteersSummaryItemContainer.setOnClickListener {
                onItemClicked(item)
            }
        }
    }

    private class EventWithCountDiffCallback : DiffUtil.ItemCallback<EventWithVolunteerCount>() {
        override fun areItemsTheSame(oldItem: EventWithVolunteerCount, newItem: EventWithVolunteerCount): Boolean {
            // Assuming eventId is the unique identifier for the underlying event
            return oldItem.event.eventId == newItem.event.eventId
        }

        override fun areContentsTheSame(oldItem: EventWithVolunteerCount, newItem: EventWithVolunteerCount): Boolean {
            return oldItem == newItem // Relies on EventWithVolunteerCount being a data class
        }
    }
}
