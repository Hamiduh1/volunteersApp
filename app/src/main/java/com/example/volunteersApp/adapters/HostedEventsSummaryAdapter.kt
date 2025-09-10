package com.example.volunteersApp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.databinding.ItemHostedEventSummaryBinding // Assuming ViewBinding
import com.example.volunteersApp.models.EventModel // Your EventModel
import java.text.SimpleDateFormat
import java.util.Locale

class HostedEventsSummaryAdapter(
    private val onEventClicked: (EventModel) -> Unit
) : ListAdapter<EventModel, HostedEventsSummaryAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemHostedEventSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding, onEventClicked)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        if (event != null) {
            holder.bind(event)
        }
    }

    class EventViewHolder(
        private val binding: ItemHostedEventSummaryBinding,
        private val onEventClicked: (EventModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: EventModel) {
            binding.textViewEventTitleSummary.text = event.title ?: "N/A"

            if (event.eventDateTime != null) {
                try {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    binding.textViewEventDateSummary.text = sdf.format(event.eventDateTime.toDate())
                } catch (e: Exception) {
                    binding.textViewEventDateSummary.text = "Date N/A"
                }
            } else {
                binding.textViewEventDateSummary.text = "Date N/A"
            }

            binding.hostedEventSummaryItemContainer.setOnClickListener {
                onEventClicked(event)
            }
        }
    }

    private class EventDiffCallback : DiffUtil.ItemCallback<EventModel>() {
        override fun areItemsTheSame(oldItem: EventModel, newItem: EventModel): Boolean {
            return oldItem.eventId == newItem.eventId
        }

        override fun areContentsTheSame(oldItem: EventModel, newItem: EventModel): Boolean {
            // Compare relevant fields that might change the visual representation
            return oldItem.title == newItem.title &&
                    oldItem.eventDateTime == newItem.eventDateTime // Add more if needed
        }
    }
}
