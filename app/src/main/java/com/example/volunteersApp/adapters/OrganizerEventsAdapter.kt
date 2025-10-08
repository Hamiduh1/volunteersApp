package com.example.volunteersApp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemEventBinding
import com.example.volunteersApp.models.Event
import java.text.SimpleDateFormat
import java.util.*

class OrganizerEventsAdapter(
    private val onItemClicked: (event: Event, view: View) -> Unit
) : ListAdapter<Event, OrganizerEventsAdapter.EventViewHolder>(EventDiffCallback()) {

    inner class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            // --- Bind data from the Event model to the views ---
            binding.textViewEventTitle.text = event.title
            binding.textViewEventLocation.text = event.location

            // Format the date for display
            binding.textViewEventDate.text = event.date?.let {
                SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(it)
            } ?: "Date not set"

            // --- Control Visibility and Data for Organizer-specific views ---

            // Show organizer name if available
            if (!event.organizerName.isNullOrEmpty()) {
                binding.textViewEventOrganizer.visibility = View.VISIBLE
                binding.textViewEventOrganizer.text = itemView.context.getString(R.string.organized_by, event.organizerName)
            } else {
                binding.textViewEventOrganizer.visibility = View.GONE
            }

            // Show category if available
            if (!event.category.isNullOrEmpty()) {
                binding.textViewEventCategory.visibility = View.VISIBLE
                binding.textViewEventCategory.text = event.category
            } else {
                binding.textViewEventCategory.visibility = View.GONE
            }

            // Show applicant info and View Applicants button for organizers
            binding.layoutActions.visibility = View.VISIBLE
            binding.buttonApplyForEvent.visibility = View.GONE // Hide volunteer button
            binding.textViewApplicantsInfo.visibility = View.VISIBLE
            binding.buttonViewApplicants.visibility = View.VISIBLE

            // Example of showing applicant count
            val applicantCount = event.applicants?.size ?: 0
            binding.textViewApplicantsInfo.text = itemView.context.resources.getQuantityString(
                R.plurals.number_of_applicants, applicantCount, applicantCount
            )

            // --- Set Click Listeners ---

            // General click on the whole item
            binding.root.setOnClickListener {
                onItemClicked(event, it)
            }

            // Specific click on the "View Applicants" button
            binding.buttonViewApplicants.setOnClickListener {
                onItemClicked(event, it)
            }

            // Click listener for the options menu icon
            binding.imageViewEventOptions.setOnClickListener {
                onItemClicked(event, it)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
    }
}

class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
    override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
        return oldItem.eventId == newItem.eventId
    }

    override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
        return oldItem == newItem
    }
}
