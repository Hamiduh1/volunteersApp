package com.example.volunteersApp.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
//import androidx.compose.foundation.background
//import androidx.compose.ui.semantics.error
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
//import androidx.wear.compose.material.placeholder
import com.bumptech.glide.Glide // For image loading - add Glide dependency if not already present
import com.example.volunteersApp.R
import com.example.volunteersApp.models.Event // Ensure this import points to your Event model
import java.text.SimpleDateFormat
import java.util.Locale

class HostedEventsAdapter(
    private val context: Context,
    private var eventList: List<Event>,
    private val onItemClicked: (event: Event) -> Unit // Lambda for click events
) : RecyclerView.Adapter<HostedEventsAdapter.EventViewHolder>() {

    // SimpleDateFormat for formatting the event date and time
    // Define format patterns as needed
    private val dateTimeFormatter = SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    // private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    // private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hosted_event, parent, false) // Use your item layout
        return EventViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val currentEvent = eventList[position]
        holder.bind(currentEvent)
    }

    override fun getItemCount() = eventList.size

    // Function to update the list of events in the adapter
    fun updateEvents(newEvents: List<Event>) {
        eventList = newEvents
        notifyDataSetChanged() // Consider using DiffUtil for better performance with large lists
    }

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventNameTextView: TextView = itemView.findViewById(R.id.textView_event_item_name)
        private val eventDateTextView: TextView = itemView.findViewById(R.id.textView_event_item_date)
        private val eventLocationTextView: TextView = itemView.findViewById(R.id.textView_event_item_location)
        private val eventStatusTextView: TextView = itemView.findViewById(R.id.textView_event_item_status)
        private val eventImageView: ImageView = itemView.findViewById(R.id.imageView_event_item_image)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) { // Check if item position is valid
                    onItemClicked(eventList[position])
                }
            }
        }

        fun bind(event: Event) {
            eventNameTextView.text = event.title ?: "N/A"
            eventLocationTextView.text = event.locationName ?: "Location not specified"

            // Format and display the event timestamp
            event.eventTimestamp?.toDate()?.let { date ->
                eventDateTextView.text = dateTimeFormatter.format(date)
            } ?: run {
                eventDateTextView.text = "Date not set"
            }

            eventStatusTextView.text = event.status?.uppercase(Locale.ROOT) ?: "UNKNOWN"

            // Set status background and text color based on event status
            // This is a basic example; you might want more sophisticated logic or data binding
            when (event.status?.lowercase(Locale.ROOT)) {
                "upcoming" -> {
                    eventStatusTextView.background = ContextCompat.getDrawable(context, R.drawable.status_background_upcoming)
                    // eventStatusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_text_upcoming))
                }
                "active" -> {
                    eventStatusTextView.background = ContextCompat.getDrawable(context, R.drawable.status_background_active) // Create this drawable
                    // eventStatusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_text_active))
                }
                "completed" -> {
                    eventStatusTextView.background = ContextCompat.getDrawable(context, R.drawable.status_background_completed) // Create this drawable
                    // eventStatusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_text_completed))
                }
                "cancelled" -> {
                    eventStatusTextView.background = ContextCompat.getDrawable(context, R.drawable.status_background_cancelled) // Create this drawable
                    // eventStatusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_text_cancelled))
                }
                else -> {
                    eventStatusTextView.background = ContextCompat.getDrawable(context, R.drawable.status_background_default) // Create a default drawable
                    // eventStatusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_text_default))
                }
            }
            // Ensure text color contrasts with background, often white for solid backgrounds.
            eventStatusTextView.setTextColor(ContextCompat.getColor(context, android.R.color.white))


            // Load event image using Glide (or Picasso, or your preferred image loading library)
            if (!event.imageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(event.imageUrl)
                    .placeholder(R.drawable.ic_placeholder_event) // Placeholder while loading
                    .error(R.drawable.ic_placeholder_event) // Error placeholder if image fails to load
                    .centerCrop()
                    .into(eventImageView)
            } else {
                // Set a default image or placeholder if no imageUrl is provided
                Glide.with(itemView.context)
                    .load(R.drawable.ic_placeholder_event)
                    .centerCrop()
                    .into(eventImageView)
            }
        }
    }
}
