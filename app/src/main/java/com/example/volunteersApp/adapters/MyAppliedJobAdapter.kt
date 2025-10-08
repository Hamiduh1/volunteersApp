package com.example.volunteersApp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
//import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemJobApplicationStatusBinding // You'll create this layout
import com.example.volunteersApp.models.JobApplication // Your JobApplication model
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.text.isLowerCase
import kotlin.text.lowercase
import kotlin.text.replaceFirstChar
import kotlin.text.titlecase

class MyAppliedJobAdapter(
    private val listener: OnAppliedJobClickListener
) : ListAdapter<JobApplication, MyAppliedJobAdapter.JobApplicationViewHolder>(DiffCallback()) {

    interface OnAppliedJobClickListener {
        fun onAppliedJobClicked(jobApplication: JobApplication)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobApplicationViewHolder {
        val binding = ItemJobApplicationStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return JobApplicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JobApplicationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class JobApplicationViewHolder(private val binding: ItemJobApplicationStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onAppliedJobClicked(getItem(position))
                }
            }
        }

        fun bind(jobApplication: JobApplication) {
            binding.textViewJobTitleApplication.text = jobApplication.jobTitle ?: "N/A"
          //  binding.textViewEmployerNameApplication.text = jobApplication.employerName ?: "N/A"
            binding.textViewEmployerNameApplication.text = jobApplication.companyName ?: "N/A"
            binding.textViewApplicationStatus.text =
                itemView.context.getString(R.string.status_prefix, jobApplication.status?.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                } ?: "Unknown")


            // Format timestamp if available
            jobApplication.applicationTimestamp?.toDate()?.let { date ->
                val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                // sdf.timeZone = TimeZone.getDefault() // Or specify a fixed timezone if needed for consistency
                binding.textViewApplicationDate.text = itemView.context.getString(R.string.applied_on_prefix, sdf.format(date))
                binding.textViewApplicationDate.visibility = View.VISIBLE
            } ?: run {
                binding.textViewApplicationDate.visibility = View.GONE
            }

            // Set status color (example)
            val statusColor = when (jobApplication.status?.lowercase(Locale.getDefault())) {
                "accepted" -> R.color.status_accepted_green
                "pending" -> R.color.status_pending_orange
                "rejected", "cancelled", "withdrawn" -> R.color.status_rejected_red
                else -> R.color.status_unknown_grey
            }
            binding.textViewApplicationStatus.setTextColor(
                ContextCompat.getColor(itemView.context, statusColor)
            )
            // You might want a small status indicator dot as well
            binding.statusIndicatorDot.setColorFilter(ContextCompat.getColor(itemView.context, statusColor))

        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<JobApplication>() {
        override fun areItemsTheSame(oldItem: JobApplication, newItem: JobApplication): Boolean {
            return oldItem.applicationId == newItem.applicationId // Assuming applicationId is unique and stable
        }

        override fun areContentsTheSame(oldItem: JobApplication, newItem: JobApplication): Boolean {
            return oldItem == newItem // Relies on JobApplication being a data class or having good equals()
        }
    }
}
