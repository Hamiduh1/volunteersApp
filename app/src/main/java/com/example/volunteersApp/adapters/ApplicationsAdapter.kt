package com.example.volunteersApp.adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.ItemApplicationOrgBinding
import com.example.volunteersApp.models.EventApplication
import java.text.SimpleDateFormat
import com.example.volunteersApp.organizer.ApplicationDiffCallback
import java.util.Locale // FIXED: Import the correct, standard Java/Kotlin Locale

// IMPORTANT: ApplicationDiffCallback should also be in this file or imported from its own file.
// Let's include it here to make this file self-contained.
import androidx.recyclerview.widget.DiffUtil

// --- Adapter for the RecyclerView ---

// --- Adapter for the RecyclerView ---
class ApplicationsAdapter(
    private val onItemClicked: (EventApplication) -> Unit
) : ListAdapter<EventApplication, ApplicationsAdapter.ApplicationViewHolder>(ApplicationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        val binding = ItemApplicationOrgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ApplicationViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ApplicationViewHolder(
        private val binding: ItemApplicationOrgBinding,
        private val onItemClicked: (EventApplication) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(application: EventApplication) {
            val naString = itemView.context.getString(R.string.n_a)

            // FIXED: Use the correct IDs from your item_application_org.xml
            binding.textViewVolunteerNameOrg.text = application.volunteerName ?: naString
            binding.textViewEventTitleOrg.text = application.eventTitle ?: naString
            binding.textViewApplicationStatusOrg.text = application.status?.replace("_", " ")?.capitalizeWords() ?: "N/A"

            binding.textViewAppliedDateOrg.text = application.applicationTimestamp?.toDate()?.let { date ->
                itemView.context.getString(R.string.applied_on_date, DateFormat.getMediumDateFormat(itemView.context).format(date))
            } ?: itemView.context.getString(R.string.date_na)

            // TODO: You can also add logic here to load the profile image with a library like Glide or Coil
            // For example: Glide.with(itemView.context).load(application.volunteerImageUrl).into(binding.imageViewVolunteerProfileOrg)

            // TODO: Add logic to change the status background color based on application.status
            // val statusColor = when(application.status) { ... }
            // binding.textViewApplicationStatusOrg.setBackgroundColor(statusColor)

            binding.root.setOnClickListener {
                onItemClicked(application)
            }
        }
    }

    // Using Locale for better practice
    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.lowercase(Locale.ROOT).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}

// --- DiffUtil Callback for the Adapter ---
// Moved this here so the file is complete. You can keep it separate if you prefer.
//class ApplicationDiffCallback : DiffUtil.ItemCallback<EventApplication>() {
  //  override fun areItemsTheSame(oldItem: EventApplication, newItem: EventApplication): Boolean {
  //      return oldItem.applicationId == newItem.applicationId
   // }

   // override fun areContentsTheSame(oldItem: EventApplication, newItem: EventApplication): Boolean {
     //   return oldItem == newItem
   // }
//}
