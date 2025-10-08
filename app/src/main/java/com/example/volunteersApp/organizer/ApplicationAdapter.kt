package com.example.volunteersApp.organizer

import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
//import androidx.compose.ui.semantics.dismiss
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
//import androidx.glance.background
//import androidx.glance.visibility
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.widget.EditText;
import com.bumptech.glide.Glide
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemApplicationOrgBinding
import com.example.volunteersApp.models.EventApplication // <<< CHANGED IMPORT
import com.example.volunteersApp.models.ApplicationStatus // Assuming this enum can map EventApplication statuses
import com.example.volunteersApp.models.EventApplicationStatus // Or import your new specific enum
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.text.ifEmpty
import kotlin.text.trim

class ApplicationAdapter(
    private val context: Context,
    private val onApplicationClicked: (application: EventApplication) -> Unit, // <<< CHANGED Type
    private val onApproveClicked: ((EventApplication) -> Unit)? = null,    // <<< CHANGED Type
  //  private val onRejectClicked: ((EventApplication) -> Unit)? = null     // <<< CHANGED Type
   private val onRejectClicked: ((application: EventApplication, reason: String?) -> Unit)? = null, // <<<< CORRECTED

) : ListAdapter<EventApplication, ApplicationAdapter.ApplicationViewHolder>(EventApplicationDiffCallback()) { // <<< CHANGED Type

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
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition // Use bindingAdapterPosition for safety
                if (position != RecyclerView.NO_POSITION) {
                    onApplicationClicked(getItem(position))
                }
            }

            // Check if buttons exist in the binding to avoid NullPointerExceptions
            // if the layout variation doesn't include them.
            binding.buttonApproveOrg?.let { button ->
                if (onApproveClicked != null) {
                    button.setOnClickListener {
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            onApproveClicked.invoke(getItem(position))
                        }
                    }
                }
            }
            binding.buttonRejectOrg?.let { button ->
                if (onRejectClicked != null) {
                    button.setOnClickListener {
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val application = getItem(position)
                            showRejectionReasonDialog(getItem(position))
                           // onRejectClicked.invoke(getItem(position))
                        }
                    }
                }
            }
        }

        // --- ADD THIS HELPER METHOD ---
        private fun showRejectionReasonDialog(application: EventApplication) {
            val builder = AlertDialog.Builder(context) // Use the context passed to the adapter
            builder.setTitle(context.getString(R.string.reject_application_title)) // Add to strings.xml: "Reject Application"

            val input = EditText(context)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            input.hint = context.getString(R.string.rejection_reason_hint) // Add to strings.xml: "Enter reason (optional)"
            builder.setView(input)

            builder.setPositiveButton(context.getString(R.string.submit_button_text)) { dialog, _ -> // Add to strings.xml: "Submit"
                val reason = input.text.toString().trim()
                // Invoke the callback passed from the Fragment, now with the reason
                onRejectClicked?.invoke(application, reason.ifEmpty { null })
                dialog.dismiss()
            }
            builder.setNegativeButton(context.getString(R.string.cancel_button_text)) { dialog, _ -> // Add to strings.xml: "Cancel"
                // Optionally, if you want to invoke with null reason on cancel:
                // onRejectClicked?.invoke(application, null)
                // Or just dismiss if cancel means "don't reject yet"
                dialog.dismiss()
            }
            builder.show()
        }





        fun bind(application: EventApplication) { // <<< CHANGED Type
            binding.textViewVolunteerNameOrg.text = application.volunteerName ?: context.getString(R.string.n_a)
            binding.textViewEventTitleOrg.text = application.eventTitle ?: context.getString(R.string.n_a)

            Glide.with(context)
                .load(application.volunteerProfileImageUrl)
                .placeholder(R.drawable.ic_default_profile) // Ensure ic_default_profile exists
                .error(R.drawable.ic_default_profile)
                .circleCrop()
                .into(binding.imageViewVolunteerProfileOrg)

            // Use the helper method from EventApplication or format here
            // application.getFormattedApplicationDate() might be cleaner if you keep it.
            application.applicationTimestamp?.toDate()?.let { date ->
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.textViewAppliedDateOrg.text = context.getString(R.string.applied_on_date, dateFormat.format(date))
            } ?: run {
                binding.textViewAppliedDateOrg.text = context.getString(R.string.applied_on_unavailable)
            }

            // Status handling
            val statusStringFromModel = application.status?.uppercase(Locale.ROOT) ?: "UNKNOWN"
            binding.textViewApplicationStatusOrg.text = statusStringFromModel.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }


            // Attempt to map string status to your ApplicationStatus enum
            // If you create EventApplicationStatus enum, use that directly.
            val statusEnum: ApplicationStatus? = try {
                // This assumes your ApplicationStatus enum names match the uppercase strings from EventApplication.status
                ApplicationStatus.valueOf(statusStringFromModel)
            } catch (e: IllegalArgumentException) {
                Log.w("ApplicationAdapter", "Invalid or unmapped application status string: $statusStringFromModel", e)
                null
            }

            // Determine background based on the enum
            val statusBackgroundRes = when (statusEnum) {
                ApplicationStatus.PENDING -> R.drawable.status_background_pending
                ApplicationStatus.APPROVED -> R.drawable.status_background_approved
                ApplicationStatus.REJECTED -> R.drawable.status_background_rejected
                ApplicationStatus.WITHDRAWN -> R.drawable.status_background_withdrawn
                ApplicationStatus.WAITLISTED -> R.drawable.status_background_waitlisted
                ApplicationStatus.ACCEPTED -> R.drawable.status_background_accepted
                // Add any other statuses from your ApplicationStatus enum
                null -> { // Handles unmapped or null statusEnum
                    Log.w("ApplicationAdapter", "Unhandled or unknown application status for UI: $statusStringFromModel")
                    R.drawable.status_background_default
                }
            }
            binding.textViewApplicationStatusOrg.background = ContextCompat.getDrawable(context, statusBackgroundRes)

            // Set text color (optional, often white for colored backgrounds)
            // You might want to make this dynamic based on the background too.
            val statusTextColor = when (statusEnum) {
                null -> ContextCompat.getColor(context, android.R.color.black) // Default for unknown
                else -> ContextCompat.getColor(context, android.R.color.white) // Default for known statuses
            }
            binding.textViewApplicationStatusOrg.setTextColor(statusTextColor)


            // Manage Visibility of Action Buttons
            // Ensure buttons exist in the layout before trying to set visibility
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

    // DiffCallback updated for EventApplication
    class EventApplicationDiffCallback : DiffUtil.ItemCallback<EventApplication>() { // <<< CHANGED Type
        override fun areItemsTheSame(oldItem: EventApplication, newItem: EventApplication): Boolean {
            // Use applicationId which should be unique for each application document
            return oldItem.applicationId == newItem.applicationId
        }

        override fun areContentsTheSame(oldItem: EventApplication, newItem: EventApplication): Boolean {
            // Relies on EventApplication having a well-defined equals() method.
            // Your EventApplication.java already has a decent equals() method.
            return oldItem == newItem
        }
    }
}

