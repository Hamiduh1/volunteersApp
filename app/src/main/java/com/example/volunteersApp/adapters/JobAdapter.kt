package com.example.volunteersApp.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
//import androidx.glance.visibility
//import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemJobBinding
import com.example.volunteersApp.models.Job
// import com.squareup.picasso.Picasso // Or Glide
import java.text.SimpleDateFormat
import java.util.Locale

class JobAdapter(
    private val context: Context,
    private val listener: OnJobInteractionListener,
    initialAppliedJobIds: Set<String> // Renamed constructor param to avoid confusion if needed,
    // or ensure the class property is correctly initialized.
) : ListAdapter<Job, JobAdapter.JobViewHolder>(JobDiffCallback()) {

    // THIS IS THE CLASS PROPERTY that holds the state.
    // It should be initialized by the constructor parameter.
    private var appliedJobIds: Set<String> = initialAppliedJobIds // Initialize with constructor param

    interface OnJobInteractionListener {
        fun onJobClick(job: Job)
        fun onApplyJobClick(job: Job, position: Int)
    }

    // This function updates the class property 'appliedJobIds'
    fun setAppliedJobIds(newAppliedJobIds: Set<String>) {
        // No need for 'val oldAppliedJobIds = appliedJobIds' unless you need to compare
        this.appliedJobIds = newAppliedJobIds // Correctly assigns Set<String> to Set<String>
        // When this changes, you might need to notify the adapter about changes
        // if the button state of many visible items might change.
        // For ListAdapter, if this affects sorting or filtering, you'd resubmit the list.
        // If it only affects the "Apply" button state, you might consider diffing
        // or selectively notifying items, but that can get complex.
        // A common approach is to have the Fragment/ViewModel manage the list
        // and resubmit it when data like this changes the view.
        // For now, we'll just update the set. The next bind will use the new set.
        // If you want immediate updates for visible items:
        // currentList.forEachIndexed { index, job ->
        //    if (job.id in oldAppliedJobIds && job.id !in newAppliedJobIds ||
        //        job.id !in oldAppliedJobIds && job.id in newAppliedJobIds) {
        //        notifyItemChanged(index)
        //    }
        // }
        // Or simply, if the list is managed externally and resubmitted, that's cleaner.
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val binding = ItemJobBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return JobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = getItem(position)
        holder.bind(job)
    }

    inner class JobViewHolder(private val binding: ItemJobBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onJobClick(getItem(position))
                }
            }

            binding.buttonApplyJob.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onApplyJobClick(getItem(position), position)
                }
            }
        }

        fun bind(job: Job) {
            binding.textViewJobTitle.text = job.title
            binding.textViewJobEmployer.text = job.employerName
            binding.textViewJobLocation.text = job.locationString
            binding.textViewJobCategory.text = job.category


            // In JobAdapter.kt -> JobViewHolder.bind()

            // ... other properties of Job are set ...
            //binding.textViewJobCategory.text = job.category

            val localPostedDate = job.postedDate // Assign to a local val
            if (localPostedDate != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.textViewJobDate.text = context.getString(R.string.job_posted_on, sdf.format(localPostedDate.toDate()))
                binding.textViewJobDate.visibility = android.view.View.VISIBLE
            } else {
                binding.textViewJobDate.visibility = android.view.View.GONE
            }


            binding.imageViewJobIcon.setImageResource(R.drawable.ic_default_work_outline)
            // ... rest of the bind method ...
/**
            if (job.postedDate != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.textViewJobDate.text = context.getString(R.string.job_posted_on, sdf.format(job.postedDate.toDate()))
                binding.textViewJobDate.visibility = android.view.View.VISIBLE
            } else {
                binding.textViewJobDate.visibility = android.view.View.GONE
            }

            binding.imageViewJobIcon.setImageResource(R.drawable.ic_default_work_outline)
**/
            // Update Apply button based on whether the user has applied
            // Now 'appliedJobIds' correctly refers to the Set<String> class property
            if (appliedJobIds.contains(job.jobId)) {
                binding.buttonApplyJob.text = context.getString(R.string.job_applied_button_text)
                binding.buttonApplyJob.isEnabled = false
                binding.buttonApplyJob.backgroundTintList = ContextCompat.getColorStateList(context, R.color.grey_400)
            } else {
                binding.buttonApplyJob.text = context.getString(R.string.job_apply_button_text)
                binding.buttonApplyJob.isEnabled = true
                binding.buttonApplyJob.backgroundTintList = ContextCompat.getColorStateList(context, R.color.buttonColor)
            }
        }
    }

    class JobDiffCallback : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job): Boolean {
            return oldItem.jobId == newItem.jobId
        }

        override fun areContentsTheSame(oldItem: Job, newItem: Job): Boolean {
            return oldItem == newItem
        }
    }
}

