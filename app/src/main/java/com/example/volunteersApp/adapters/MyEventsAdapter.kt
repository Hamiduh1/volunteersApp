/** This adapter will be a ListAdapter, which is highly efficient for RecyclerViews
 * that are updated by LiveData from a ViewModel. It will also use View Binding
 * and include an interface to handle clicks, such as tapping the "Withdraw" button. **/

// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/adapters/MyEventsAdapter.kt

package com.example.volunteersApp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
//import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemMyEventBinding // This binding will be generated from your item layout XML
import com.example.volunteersApp.models.EventModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyEventsAdapter(
    private val listener: OnMyEventListener
) : ListAdapter<EventModel, MyEventsAdapter.MyEventViewHolder>(MyEventDiffCallback()) {

    // Interface for handling clicks within the fragment
    interface OnMyEventListener {
        fun onEventClick(event: EventModel)
        fun onWithdrawClick(event: EventModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyEventViewHolder {
        val binding = ItemMyEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MyEventViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: MyEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ViewHolder class to hold and bind the views for each item
    inner class MyEventViewHolder(
        private val binding: ItemMyEventBinding,
        private val listener: OnMyEventListener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Set a click listener for the entire item view
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onEventClick(getItem(adapterPosition))
                }
            }
            // Set a click listener for the withdraw button
            binding.buttonWithdraw.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onWithdrawClick(getItem(adapterPosition))
                }
            }
        }

        fun bind(event: EventModel) {
            binding.textViewEventTitle.text = event.title
            binding.textViewEventOrganizer.text = event.organizerName

            // Format and display the date
            event.eventDateTime?.toDate()?.let { date ->
                val sdf = SimpleDateFormat("EEE, MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                binding.textViewEventDate.text = sdf.format(date)
            } ?: run {
                binding.textViewEventDate.text = "Date not specified"
            }

            // Update UI based on the event's user-specific status
            val now = Date()
            val isUpcoming = event.eventDateTime?.toDate()?.after(now) ?: false
            val userStatus = event.userStatus // This field is set in the ViewModel

            when (userStatus?.lowercase()) {
                "approved" -> {
                    if (isUpcoming) {
                        // Approved & Upcoming
                        binding.textViewStatus.text = "Approved"
                        binding.textViewStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green500))
                        binding.buttonWithdraw.visibility = View.VISIBLE
                        binding.buttonWithdraw.text = "Withdraw"
                        binding.buttonWithdraw.isEnabled = true
                    } else {
                        // Past & Attended
                        binding.textViewStatus.text = "Attended"
                        binding.textViewStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.grey_600))
                        binding.buttonWithdraw.visibility = View.GONE // Can't withdraw from a past event
                    }
                }
                "pending" -> {
                    // Applied & Pending
                    binding.textViewStatus.text = "Pending"
                    binding.textViewStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange_500)) // Use an orange color
                    binding.buttonWithdraw.visibility = View.VISIBLE
                    binding.buttonWithdraw.text = "Withdraw"
                    binding.buttonWithdraw.isEnabled = true
                }
                else -> {
                    // Default or unknown state
                    binding.textViewStatus.text = userStatus ?: "Unknown"
                    binding.textViewStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.grey_600))
                    binding.buttonWithdraw.visibility = View.GONE
                }
            }
        }
    }
}

// DiffUtil class to calculate the difference between two lists for efficient RecyclerView updates
class MyEventDiffCallback : DiffUtil.ItemCallback<EventModel>() {
    override fun areItemsTheSame(oldItem: EventModel, newItem: EventModel): Boolean {
        // IDs are the best way to check if two items represent the same object
        return oldItem.eventId == newItem.eventId
    }

    override fun areContentsTheSame(oldItem: EventModel, newItem: EventModel): Boolean {
        // Check if the content of the items has changed
        return oldItem == newItem
    }
}
