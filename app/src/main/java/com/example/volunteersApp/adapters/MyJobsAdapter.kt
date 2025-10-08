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
import com.example.volunteersApp.databinding.ItemMyJobBinding // Will be generated from item_my_job.xml
import com.example.volunteersApp.models.Job // Ensure this is your correct Job model
import java.text.SimpleDateFormat
import java.util.Locale

class MyJobsAdapter(
    private val listener: OnMyJobListener
) : ListAdapter<Job, MyJobsAdapter.MyJobViewHolder>(MyJobDiffCallback()) {

    // Interface for the fragment to handle clicks
    interface OnMyJobListener {
        fun onJobClick(job: Job)
        fun onWithdrawJobClick(job: Job)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyJobViewHolder {
        val binding = ItemMyJobBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // REMOVED listener from here, as it's passed in onBindViewHolder
        return MyJobViewHolder(binding)
    }

    // THIS METHOD IS UPDATED
    override fun onBindViewHolder(holder: MyJobViewHolder, position: Int) {
        val currentJob = getItem(position)
        // Pass the specific item and the listener to the holder
        holder.bind(currentJob, listener)
    }

    // THIS CLASS IS UPDATED
    class MyJobViewHolder(
        private val binding: ItemMyJobBinding
        // Listener is no longer a property of the ViewHolder
    ) : RecyclerView.ViewHolder(binding.root) {

        // The 'init' block is removed from here.

        fun bind(job: Job, listener: OnMyJobListener) { // Listener is now passed in here
            binding.textViewJobTitle.text = job.title
            binding.textViewEmployerName.text = job.employerName
            binding.textViewLocation.text = job.locationString

            // Display and style the status
            val status = job.userStatus?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "Unknown"
            binding.textViewStatus.text = status

            // **FIX**: The color resource name was 'green_500', not 'green500'. Corrected here.
            val statusColor = when (job.userStatus) {
                "approved" -> R.color.green500
                "pending" -> R.color.orange_500
                else -> R.color.grey_600
            }
            binding.textViewStatus.setTextColor(ContextCompat.getColor(itemView.context, statusColor))

            // Show/hide the withdraw button based on status
            binding.buttonWithdraw.visibility = when (job.userStatus) {
                "pending", "approved" -> View.VISIBLE
                else -> View.GONE
            }

            // --- THIS IS THE FIX ---
            // Set the click listeners here, inside bind(), where you have access to the 'job' object.
            binding.root.setOnClickListener {
                listener.onJobClick(job)
            }
            binding.buttonWithdraw.setOnClickListener {
                listener.onWithdrawJobClick(job)
            }
        }
    }


    // DiffUtil for efficient list updates
    class MyJobDiffCallback : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job): Boolean {
            return oldItem.jobId == newItem.jobId
        }

        override fun areContentsTheSame(oldItem: Job, newItem: Job): Boolean {
            // Compare contents, including the transient userStatus field
            return oldItem == newItem && oldItem.userStatus == newItem.userStatus
        }
    }
}
