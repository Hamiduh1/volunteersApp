package com.example.volunteersApp.employer.ui.applications

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
//import androidx.compose.foundation.background
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemApplicationSummaryBinding
import com.example.volunteersApp.models.ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus // <<< IMPORT YOUR ENUM
import java.text.SimpleDateFormat
import java.util.Locale

class EmployerApplicationsAdapter(
    private val context: Context,
    private val onApplicationClicked: (ApplicationModel) -> Unit
) : ListAdapter<ApplicationModel, EmployerApplicationsAdapter.ApplicationViewHolder>(ApplicationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        val binding = ItemApplicationSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ApplicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val application = getItem(position)
        holder.bind(application, context)
        holder.itemView.setOnClickListener {
            onApplicationClicked(application)
        }
    }

    class ApplicationViewHolder(private val binding: ItemApplicationSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(application: ApplicationModel, context: Context) {
            binding.textViewItemVolunteerName.text = application.volunteerName.ifEmpty { "N/A" }

            // Use 'appliedAt' from your ApplicationModel
            application.appliedAt?.toDate()?.let { date ->
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.textViewItemApplicationDate.text = context.getString(R.string.applied_on_date, sdf.format(date))
            } ?: run {
                binding.textViewItemApplicationDate.text = context.getString(R.string.applied_on_date, "N/A")
            }

            // Use 'applicationStatus' from your ApplicationModel
            // Ensure applicationStatus is not null or empty before replaceFirstChar if it can be
            val statusText = application.applicationStatus
            if (statusText.isNotEmpty()) {
                binding.textViewItemApplicationStatus.text = statusText.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            } else {
                binding.textViewItemApplicationStatus.text = "N/A"
            }


            // Set status background using 'applicationStatus' and your ApplicationStatus enum
            when (application.applicationStatus.uppercase(Locale.getDefault())) { // Compare with uppercase enum names
                ApplicationStatus.PENDING.name -> {
                    binding.textViewItemApplicationStatus.background = ContextCompat.getDrawable(context, R.drawable.status_background_pending)
                    binding.textViewItemApplicationStatus.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
                ApplicationStatus.ACCEPTED.name -> {
                    binding.textViewItemApplicationStatus.background = ContextCompat.getDrawable(context, R.drawable.status_background_accepted)
                    binding.textViewItemApplicationStatus.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
                ApplicationStatus.REJECTED.name -> {
                    binding.textViewItemApplicationStatus.background = ContextCompat.getDrawable(context, R.drawable.status_background_rejected)
                    binding.textViewItemApplicationStatus.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
                // Add other statuses from your ApplicationStatus enum if needed
                else -> {
                    binding.textViewItemApplicationStatus.background = ContextCompat.getDrawable(context, R.drawable.status_background_default)
                    binding.textViewItemApplicationStatus.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                }
            }

            Glide.with(context)
                .load(application.volunteerProfileImageUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.imageViewItemVolunteerProfile)
        }
    }

    class ApplicationDiffCallback : DiffUtil.ItemCallback<ApplicationModel>() {
        override fun areItemsTheSame(oldItem: ApplicationModel, newItem: ApplicationModel): Boolean {
            // Use the unique ID of the Application document itself for DiffUtil
            return oldItem.applicationId == newItem.applicationId
        }

        override fun areContentsTheSame(oldItem: ApplicationModel, newItem: ApplicationModel): Boolean {
            return oldItem == newItem // Relies on ApplicationModel being a data class
        }
    }
}

