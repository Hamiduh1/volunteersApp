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
import com.example.volunteersApp.databinding.ItemApplicationSummaryBinding // Ensure this layout is suitable for JobApplication display
import com.example.volunteersApp.models.JobApplication // UPDATED
import com.example.volunteersApp.models.JobApplicationStatus // UPDATED
import java.text.SimpleDateFormat
import java.util.Locale

class EmployerApplicationsAdapter(
    private val context: Context,
    private val onApplicationClicked: (JobApplication) -> Unit // UPDATED
) : ListAdapter<JobApplication, EmployerApplicationsAdapter.ApplicationViewHolder>(JobApplicationDiffCallback()) { // UPDATED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        val binding = ItemApplicationSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ApplicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val jobApplication = getItem(position) // Variable name updated for clarity
        holder.bind(jobApplication, context)
        holder.itemView.setOnClickListener {
            onApplicationClicked(jobApplication)
        }
    }

    class ApplicationViewHolder(private val binding: ItemApplicationSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(application: JobApplication, context: Context) { // UPDATED parameter type
            binding.textViewItemVolunteerName.text = application.volunteerName?.ifEmpty { context.getString(R.string.n_a) } ?: context.getString(R.string.n_a)

            // Use 'applicationTimestamp' from JobApplication
            application.applicationTimestamp?.toDate()?.let { date ->
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                // Ensure R.string.applied_on_date exists and takes one string argument (the date)
                binding.textViewItemApplicationDate.text = context.getString(R.string.applied_on_date, sdf.format(date))
            } ?: run {
                binding.textViewItemApplicationDate.text = context.getString(R.string.applied_on_date, context.getString(R.string.n_a))
            }

            // Use 'status' from JobApplication
            val statusText = application.status ?: JobApplicationStatus.PENDING.name // Default if null
            if (statusText.isNotEmpty()) {
                binding.textViewItemApplicationStatus.text = statusText.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            } else {
                binding.textViewItemApplicationStatus.text = context.getString(R.string.n_a)
            }

            // Set status background using 'status' from JobApplication and JobApplicationStatus enum
            val statusDrawableRes = when (statusText.uppercase(Locale.getDefault())) {
                JobApplicationStatus.PENDING.name -> R.drawable.status_background_pending
                JobApplicationStatus.VIEWED.name -> R.drawable.status_background_viewed
                JobApplicationStatus.SHORTLISTED.name -> R.drawable.status_background_shortlisted
                JobApplicationStatus.INTERVIEWING.name -> R.drawable.status_background_interviewing
                JobApplicationStatus.OFFER_EXTENDED.name -> R.drawable.status_background_offer_extended
                JobApplicationStatus.ACCEPTED.name -> R.drawable.status_background_accepted
                JobApplicationStatus.REJECTED_BY_EMPLOYER.name -> R.drawable.status_background_rejected
                JobApplicationStatus.WITHDRAWN.name -> R.drawable.status_background_withdrawn
                // Add JobApplicationStatus.REJECTED_BY_VOLUNTEER if relevant for this list
                else -> R.drawable.status_background_default
            }
            binding.textViewItemApplicationStatus.background = ContextCompat.getDrawable(context, statusDrawableRes)

            // Set text color based on status
            val statusTextColorRes = when (statusText.uppercase(Locale.getDefault())) {
                JobApplicationStatus.PENDING.name,
                JobApplicationStatus.VIEWED.name,
                JobApplicationStatus.SHORTLISTED.name,
                JobApplicationStatus.INTERVIEWING.name,
                JobApplicationStatus.OFFER_EXTENDED.name,
                JobApplicationStatus.ACCEPTED.name,
                JobApplicationStatus.REJECTED_BY_EMPLOYER.name,
                JobApplicationStatus.WITHDRAWN.name -> android.R.color.white
                else -> android.R.color.black
            }
            binding.textViewItemApplicationStatus.setTextColor(ContextCompat.getColor(context, statusTextColorRes))


            Glide.with(context)
                .load(application.volunteerProfileImageUrl) // Assumes JobApplication has this field
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.imageViewItemVolunteerProfile)

            // Optional: Display Job Title if item_application_summary.xml has a TextView for it
            // binding.textViewItemJobTitle.text = application.jobTitle ?: "Job Title N/A"
        }
    }

    // UPDATED DiffCallback to use JobApplication
    class JobApplicationDiffCallback : DiffUtil.ItemCallback<JobApplication>() {
        override fun areItemsTheSame(oldItem: JobApplication, newItem: JobApplication): Boolean {
            // Use the unique ID of the JobApplication document
            return oldItem.applicationId == newItem.applicationId
        }

        override fun areContentsTheSame(oldItem: JobApplication, newItem: JobApplication): Boolean {
            // Relies on JobApplication being a data class (which it is in Kotlin)
            // or having a well-defined equals() method.
            return oldItem == newItem
        }
    }
}

