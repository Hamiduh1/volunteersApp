package com.example.volunteersApp.organizer

import android.content.Context
import android.util.Log // <<< CHANGED IMPORT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
//import androidx.glance.background
//import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.volunteersApp.R
import com.example.volunteersApp.models.ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.databinding.ItemApplicationOrgBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ApplicationAdapter(
    private val context: Context,
    private val onApplicationClicked: (application: ApplicationModel) -> Unit,
    private val onApproveClicked: ((ApplicationModel) -> Unit)? = null,
    private val onRejectClicked: ((ApplicationModel) -> Unit)? = null
) : ListAdapter<ApplicationModel, ApplicationAdapter.ApplicationViewHolder>(ApplicationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        val binding = ItemApplicationOrgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ApplicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val application = getItem(position)
        holder.bind(application)
    }

    inner class ApplicationViewHolder(private val binding: ItemApplicationOrgBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // ... (your init block remains the same) ...
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onApplicationClicked(getItem(position))
                }
            }

            if (onApproveClicked != null && binding.buttonApproveOrg != null) {
                binding.buttonApproveOrg.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onApproveClicked.invoke(getItem(position))
                    }
                }
            }

            if (onRejectClicked != null && binding.buttonRejectOrg != null) {
                binding.buttonRejectOrg.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onRejectClicked.invoke(getItem(position))
                    }
                }
            }
        }


        fun bind(application: ApplicationModel) {
            // ... (binding volunteerName, eventTitle, image, appliedAt remains the same) ...
            binding.textViewVolunteerNameOrg.text = application.volunteerName
            binding.textViewEventTitleOrg.text = application.eventTitle

            Glide.with(context)
                .load(application.volunteerProfileImageUrl)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)
                .circleCrop()
                .into(binding.imageViewVolunteerProfileOrg)

            application.appliedAt?.toDate()?.let { date ->
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.textViewAppliedDateOrg.text = context.getString(R.string.applied_on_date, dateFormat.format(date))
            } ?: run {
                binding.textViewAppliedDateOrg.text = context.getString(R.string.applied_on_unavailable)
            }


            val statusString = application.applicationStatus.uppercase(Locale.ROOT)
            binding.textViewApplicationStatusOrg.text = statusString

            val statusEnum: ApplicationStatus? = try {
                ApplicationStatus.valueOf(statusString)
            } catch (e: IllegalArgumentException) {
                // Now uses android.util.Log, no opt-in needed
                Log.w("ApplicationAdapter", "Invalid application status string: $statusString", e)
                null
            }

            when (statusEnum) {
                ApplicationStatus.PENDING -> {
                    binding.textViewApplicationStatusOrg.background =
                        ContextCompat.getDrawable(context, R.drawable.status_background_pending)
                }
                ApplicationStatus.APPROVED -> {
                    binding.textViewApplicationStatusOrg.background =
                        ContextCompat.getDrawable(context, R.drawable.status_background_approved)
                }
                ApplicationStatus.REJECTED -> {
                    binding.textViewApplicationStatusOrg.background =
                        ContextCompat.getDrawable(context, R.drawable.status_background_rejected)
                }
                ApplicationStatus.WITHDRAWN -> {
                    binding.textViewApplicationStatusOrg.background =
                        ContextCompat.getDrawable(context, R.drawable.status_background_withdrawn)
                }
                ApplicationStatus.WAITLISTED -> {
                    binding.textViewApplicationStatusOrg.background =
                        ContextCompat.getDrawable(context, R.drawable.status_background_waitlisted)
                }
                ApplicationStatus.ACCEPTED -> {
                    binding.textViewApplicationStatusOrg.background =
                        ContextCompat.getDrawable(context, R.drawable.status_background_accepted)
                }
                null, -> { // This syntax should now be fine
                // Uses android.util.Log
                Log.w("ApplicationAdapter", "Unhandled or unknown application status for UI: $statusString")
                binding.textViewApplicationStatusOrg.background =
                    ContextCompat.getDrawable(context, R.drawable.status_background_default)
            }
            }

            // ... (Manage Visibility of Action Buttons remains the same) ...
            if (binding.buttonApproveOrg != null && binding.buttonRejectOrg != null) {
                if (statusEnum == ApplicationStatus.PENDING && onApproveClicked != null && onRejectClicked != null) {
                    binding.buttonApproveOrg.visibility = View.VISIBLE
                    binding.buttonRejectOrg.visibility = View.VISIBLE
                } else {
                    binding.buttonApproveOrg.visibility = View.GONE
                    binding.buttonRejectOrg.visibility = View.GONE
                }
            }
        }
    }

    // ... (ApplicationDiffCallback remains the same) ...
    class ApplicationDiffCallback : DiffUtil.ItemCallback<ApplicationModel>() {
        override fun areItemsTheSame(oldItem: ApplicationModel, newItem: ApplicationModel): Boolean {
            return oldItem.applicationId == newItem.applicationId
        }

        override fun areContentsTheSame(oldItem: ApplicationModel, newItem: ApplicationModel): Boolean {
            return oldItem == newItem
        }
    }
}

