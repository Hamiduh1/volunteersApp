package com.example.volunteersApp.employer.ui.applications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.graphics.vector.path
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
//import androidx.glance.background
//import androidx.glance.visibility
import androidx.navigation.NavController
import androidx.navigation.Navigation
//import androidx.preference.isNotEmpty
import com.bumptech.glide.Glide
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentEmployerApplicationDetailBinding // Ensure this matches your XML file name
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.User
import com.example.volunteersApp.models.JobApplicationStatus // Make sure this enum is defined


import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class EmployerApplicationDetailFragment : Fragment() {

    private var _binding: FragmentEmployerApplicationDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var navController: NavController
    private lateinit var db: FirebaseFirestore

    private var applicationDocId: String? = null
    private var jobId: String? = null
    private var volunteerId: String? = null

    companion object {
        private const val TAG = "EmpAppDetailFrag"
        const val ARG_APPLICATION_DOC_ID = "application_doc_id"
        const val ARG_JOB_ID = "job_id"
        const val ARG_VOLUNTEER_ID = "volunteer_id"

        private const val JOBS_COLLECTION = "jobs" // Collection name for job postings
        private const val APPLICATIONS_SUBCOLLECTION = "applications" // Subcollection for applications under each job
        // OR private const val JOB_APPLICATIONS_COLLECTION = "job_applications" // If using a top-level collection
        private const val USERS_COLLECTION = "users"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()
        arguments?.let {
            applicationDocId = it.getString(ARG_APPLICATION_DOC_ID)
            jobId = it.getString(ARG_JOB_ID)
            volunteerId = it.getString(ARG_VOLUNTEER_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployerApplicationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_job_application_detail)

        if (applicationDocId == null || jobId == null || volunteerId == null) {
            Toast.makeText(requireContext(), getString(R.string.error_missing_job_application_data), Toast.LENGTH_LONG).show()
            Log.e(TAG, "Missing applicationDocId, jobId, or volunteerId in arguments.")
            navController.popBackStack()
            return
        }

        setupButtonListeners()
        loadJobApplicationDetails()
    }

    private fun setupButtonListeners() {
        binding.buttonAcceptApplication.setOnClickListener {
            // Consider adding a confirmation dialog before updating status
            updateApplicationStatus(JobApplicationStatus.ACCEPTED.name)
        }
        binding.buttonRejectApplication.setOnClickListener {
            // Consider adding a confirmation dialog and a way to input rejection reason
            updateApplicationStatus(JobApplicationStatus.REJECTED_BY_EMPLOYER.name)
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (!isAdded || _binding == null) return // Check fragment is added and binding is not null
        binding.progressBarApplicationDetail.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.contentContainerApplicationDetail.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun loadJobApplicationDetails() {
        setLoadingState(true)

        val applicationRef = db.collection(JOBS_COLLECTION)
            .document(jobId!!)
            .collection(APPLICATIONS_SUBCOLLECTION)
            .document(applicationDocId!!)

        // If using a top-level "job_applications" collection:
        // val applicationRef = db.collection(JOB_APPLICATIONS_COLLECTION).document(applicationDocId!!)

        applicationRef.get().addOnCompleteListener { task ->
            if (!isAdded || _binding == null) return@addOnCompleteListener

            if (task.isSuccessful) {
                val appDocument = task.result
                if (appDocument != null && appDocument.exists()) {
                    val jobApplication = appDocument.toObject(JobApplication::class.java)
                    jobApplication?.let { app ->
                        populateApplicationData(app)
                        if (app.volunteerName.isNullOrEmpty() || app.volunteerProfileImageUrl.isNullOrEmpty() ||
                            (binding.textViewVolunteerPhoneDetail.text.toString() == "N/A" && userNeedsPhoneLookup(app))) { // Condition to check if phone lookup is needed
                            app.volunteerUid?.let { uid -> loadVolunteerUserDetails(uid) }
                                ?: run {
                                    Log.w(TAG, "Volunteer UID is null in JobApplication, cannot fetch user details.")
                                    setLoadingState(false) // Stop loading if UID is missing
                                }
                        } else {
                            // If basic data is already in JobApplication and phone is not "N/A" or not needed
                            binding.textViewVolunteerNameDetail.text = app.volunteerName
                            Glide.with(this@EmployerApplicationDetailFragment)
                                .load(app.volunteerProfileImageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(binding.imageViewVolunteerProfileDetail)
                            setLoadingState(false)
                        }
                    } ?: run {
                        handleLoadError("Job Application data could not be parsed from Firestore.")
                    }
                } else {
                    Log.w(TAG, "Job Application document not found: ${applicationRef.path}")
                    handleLoadError(getString(R.string.job_application_details_not_found))
                }
            } else {
                Log.e(TAG, "Error fetching job application details: ", task.exception)
                handleLoadError(getString(R.string.failed_to_load_job_application_details))
            }
        }
    }

    // Helper to decide if we need to hit the User collection for phone
    private fun userNeedsPhoneLookup(application: JobApplication): Boolean {
        // Customize this logic. Example: if your JobApplication model *never* stores phone.
        return true // Or check a specific condition, e.g., !application.hasDenormalizedPhoneNumber
    }


    private fun populateApplicationData(application: JobApplication) {
        if (!isAdded || _binding == null) return

        // Assumes your layout has textViewJobTitleLabel and textViewJobTitle
        binding.textViewJobTitleLabel.text = getString(R.string.label_job_title)
        binding.textViewJobTitle.text = application.jobTitle ?: getString(R.string.n_a)

        binding.textViewVolunteerNameDetail.text = application.volunteerName?.ifEmpty { getString(R.string.volunteer_details_not_available) } ?: getString(R.string.volunteer_details_not_available)
        binding.textViewVolunteerEmailDetail.text = application.volunteerEmail?.ifEmpty { getString(R.string.n_a) } ?: getString(R.string.n_a)

        // Set phone to N/A initially; will be updated by loadVolunteerUserDetails if available in User model
        // Or, if JobApplication *can* have a phone number, use it here:
        // binding.textViewVolunteerPhoneDetail.text = application.volunteerPhoneNumber?.ifEmpty { getString(R.string.n_a) } ?: getString(R.string.n_a)
        binding.textViewVolunteerPhoneDetail.text = getString(R.string.n_a)


        application.applicationTimestamp?.toDate()?.let { date ->
            val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            binding.textViewApplicationDateDetail.text = sdf.format(date)
        } ?: run {
            binding.textViewApplicationDateDetail.text = getString(R.string.n_a)
        }

        val statusString = application.status ?: JobApplicationStatus.PENDING.name
        binding.textViewApplicationStatusDetail.text = capitalize(statusString)

        val statusBgRes = when (statusString.uppercase(Locale.ROOT)) {
            JobApplicationStatus.PENDING.name -> R.drawable.status_background_pending
            JobApplicationStatus.VIEWED.name -> R.drawable.status_background_viewed // Add this drawable
            JobApplicationStatus.SHORTLISTED.name -> R.drawable.status_background_shortlisted
            JobApplicationStatus.INTERVIEWING.name -> R.drawable.status_background_interviewing
            JobApplicationStatus.OFFER_EXTENDED.name -> R.drawable.status_background_offer_extended // Add this
            JobApplicationStatus.ACCEPTED.name -> R.drawable.status_background_accepted
            JobApplicationStatus.REJECTED_BY_EMPLOYER.name -> R.drawable.status_background_rejected
            JobApplicationStatus.WITHDRAWN.name -> R.drawable.status_background_withdrawn // Add this
            // JobApplicationStatus.REJECTED_BY_VOLUNTEER is usually a state set by volunteer action
            else -> R.drawable.status_background_default
        }
        binding.textViewApplicationStatusDetail.background = ContextCompat.getDrawable(requireContext(), statusBgRes)
        binding.textViewApplicationStatusDetail.setTextColor(
            when (statusString.uppercase(Locale.ROOT)) {
                JobApplicationStatus.PENDING.name,
                JobApplicationStatus.VIEWED.name,
                JobApplicationStatus.SHORTLISTED.name,
                JobApplicationStatus.INTERVIEWING.name,
                JobApplicationStatus.OFFER_EXTENDED.name,
                JobApplicationStatus.ACCEPTED.name,
                JobApplicationStatus.REJECTED_BY_EMPLOYER.name,
                JobApplicationStatus.WITHDRAWN.name -> ContextCompat.getColor(requireContext(), android.R.color.white)
                else -> ContextCompat.getColor(requireContext(), android.R.color.black)
            }
        )

        binding.textViewCoverLetterDetail.text = application.notesFromVolunteer?.ifEmpty { getString(R.string.no_message_provided) } ?: getString(R.string.no_message_provided)
        if (application.notesFromVolunteer.isNullOrEmpty()) {
            binding.textViewLabelCoverLetter.visibility = View.GONE
            binding.textViewCoverLetterDetail.visibility = View.GONE
        } else {
            binding.textViewLabelCoverLetter.visibility = View.VISIBLE
            binding.textViewCoverLetterDetail.visibility = View.VISIBLE
        }

        // Handle Resume and Cover Letter Links (Example)
        binding.buttonViewResume.visibility = if (application.resumeLink.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.buttonViewResume.setOnClickListener {
            application.resumeLink?.let { openLink(it) }
        }
        binding.buttonViewCoverLetter.visibility = if (application.coverLetterLink.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.buttonViewCoverLetter.setOnClickListener {
            application.coverLetterLink?.let { openLink(it) }
        }


        updateActionButtonsVisibility(statusString)
    }

    private fun updateActionButtonsVisibility(status: String) {
        val showActions = when (status.uppercase(Locale.ROOT)) {
            JobApplicationStatus.PENDING.name,
            JobApplicationStatus.VIEWED.name,
            JobApplicationStatus.SHORTLISTED.name,
            JobApplicationStatus.INTERVIEWING.name -> true
            else -> false
        }
        binding.layoutActionButtons.visibility = if (showActions) View.VISIBLE else View.GONE
        binding.buttonAcceptApplication.isEnabled = showActions
        binding.buttonRejectApplication.isEnabled = showActions
    }


    private fun loadVolunteerUserDetails(currentVolunteerId: String) {
        val userRef = db.collection(USERS_COLLECTION).document(currentVolunteerId)
        userRef.get().addOnCompleteListener { task ->
            if (!isAdded || _binding == null) {
                if (!task.isSuccessful) setLoadingState(false) // Ensure loading stops if early exit on failure
                return@addOnCompleteListener
            }

            if (task.isSuccessful) {
                val userDocument = task.result
                if (userDocument != null && userDocument.exists()) {
                    val user = userDocument.toObject(User::class.java)
                    user?.let { volunteer ->
                        if (binding.textViewVolunteerNameDetail.text.toString() == getString(R.string.volunteer_details_not_available) ||
                            binding.textViewVolunteerNameDetail.text.isEmpty()) {
                            binding.textViewVolunteerNameDetail.text = volunteer.name ?: getString(R.string.volunteer_details_not_available)
                        }

                        if (binding.textViewVolunteerEmailDetail.text.toString() == getString(R.string.n_a) && !volunteer.email.isNullOrEmpty()) {
                            binding.textViewVolunteerEmailDetail.text = volunteer.email
                        }

                        // Check User model for phoneNumber and update if it was "N/A"
                        if (binding.textViewVolunteerPhoneDetail.text.toString() == getString(R.string.n_a) && volunteer.phoneNumber != null && volunteer.phoneNumber.isNotEmpty()) {
                            binding.textViewVolunteerPhoneDetail.text = volunteer.phoneNumber
                        }

                        Glide.with(this@EmployerApplicationDetailFragment)
                            .load(volunteer.profileImageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(binding.imageViewVolunteerProfileDetail)
                    }
                } else {
                    Log.w(TAG, "Volunteer user document not found: ${userRef.path}")
                    // Keep existing placeholder if name was already set from JobApplication
                }
            } else {
                Log.e(TAG, "Error fetching volunteer user details: ", task.exception)
                Toast.makeText(context, getString(R.string.could_not_load_full_volunteer_details), Toast.LENGTH_SHORT).show()
            }
            setLoadingState(false) // All data loading attempts are complete
        }
    }

    private fun updateApplicationStatus(newStatus: String) {
        if (jobId.isNullOrEmpty() || applicationDocId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot update status: jobId or applicationDocId is null")
            Toast.makeText(requireContext(), "Error: Missing data to update status.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true) // Show loading for the update operation itself
        binding.buttonAcceptApplication.isEnabled = false
        binding.buttonRejectApplication.isEnabled = false

        val applicationRef = db.collection(JOBS_COLLECTION)
            .document(jobId!!)
            .collection(APPLICATIONS_SUBCOLLECTION)
            .document(applicationDocId!!)

        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "lastUpdatedAt" to FieldValue.serverTimestamp()
        )
        // Example: Add rejection reason if implementing that feature
        // if (newStatus == JobApplicationStatus.REJECTED_BY_EMPLOYER.name) {
        //    updates["rejectionReason"] = "Your reason here..." // Get this from a dialog
        // }

        applicationRef.update(updates)
            .addOnSuccessListener {
                if (!isAdded || _binding == null) return@addOnSuccessListener
                setLoadingState(false)
                Toast.makeText(context, getString(R.string.job_application_updated_successfully, capitalize(newStatus)), Toast.LENGTH_SHORT).show()
                binding.textViewApplicationStatusDetail.text = capitalize(newStatus)

                val statusBgRes = when (newStatus.uppercase(Locale.ROOT)) {
                    JobApplicationStatus.PENDING.name -> R.drawable.status_background_pending
                    JobApplicationStatus.VIEWED.name -> R.drawable.status_background_viewed
                    JobApplicationStatus.SHORTLISTED.name -> R.drawable.status_background_shortlisted
                    JobApplicationStatus.INTERVIEWING.name -> R.drawable.status_background_interviewing
                    JobApplicationStatus.OFFER_EXTENDED.name -> R.drawable.status_background_offer_extended
                    JobApplicationStatus.ACCEPTED.name -> R.drawable.status_background_accepted
                    JobApplicationStatus.REJECTED_BY_EMPLOYER.name -> R.drawable.status_background_rejected
                    JobApplicationStatus.WITHDRAWN.name -> R.drawable.status_background_withdrawn
                    else -> R.drawable.status_background_default
                }
                binding.textViewApplicationStatusDetail.background = ContextCompat.getDrawable(requireContext(), statusBgRes)
                binding.textViewApplicationStatusDetail.setTextColor(
                    when (newStatus.uppercase(Locale.ROOT)) {
                        JobApplicationStatus.PENDING.name,
                        JobApplicationStatus.VIEWED.name,
                        JobApplicationStatus.SHORTLISTED.name,
                        JobApplicationStatus.INTERVIEWING.name,
                        JobApplicationStatus.OFFER_EXTENDED.name,
                        JobApplicationStatus.ACCEPTED.name,
                        JobApplicationStatus.REJECTED_BY_EMPLOYER.name,
                        JobApplicationStatus.WITHDRAWN.name -> ContextCompat.getColor(requireContext(), android.R.color.white)
                        else -> ContextCompat.getColor(requireContext(), android.R.color.black)
                    }
                )
                updateActionButtonsVisibility(newStatus)
                // TODO: Optionally send a notification to the volunteer about the status change
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                setLoadingState(false)
                // Re-enable buttons based on the status *before* the failed update attempt
                val originalStatus = binding.textViewApplicationStatusDetail.text.toString() // Or store it before calling update
                updateActionButtonsVisibility(capitalize(originalStatus)) // Use the existing status to reset button state

                Log.e(TAG, "Error updating job application status", e)
                Toast.makeText(context, getString(R.string.failed_to_update_job_application_status), Toast.LENGTH_SHORT).show()
            }
    }

    private fun capitalize(str: String?): String {
        return str?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        } ?: getString(R.string.n_a)
    }

    private fun handleLoadError(message: String) {
        if (!isAdded || _binding == null) return
        setLoadingState(false)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        // Consider not popping back immediately for all load errors,
        // maybe show an error message in the UI instead.
        if (navController.currentDestination?.id == R.id.nav_employer_application_detail) { // Check if still on this screen
            navController.popBackStack()
        }
    }

    private fun openLink(url: String) {
        // TODO: Implement logic to open a URL (e.g., for resume/cover letter)
        // Example:
        // try {
        //     val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        //     startActivity(intent)
        // } catch (e: ActivityNotFoundException) {
        //     Toast.makeText(context, "Cannot open link: No app found to handle it.", Toast.LENGTH_SHORT).show()
        // } catch (e: Exception) {
        //     Toast.makeText(context, "Cannot open link: Invalid URL.", Toast.LENGTH_SHORT).show()
        // }
        Toast.makeText(context, "TODO: Open link: $url", Toast.LENGTH_SHORT).show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Ensure JobApplicationStatus enum is defined in your models package like this or similar:
/*
package com.example.volunteersApp.models

enum class JobApplicationStatus {
    PENDING,
    VIEWED,
    SHORTLISTED,
    INTERVIEWING,
    OFFER_EXTENDED,
    ACCEPTED, // Volunteer accepted employer's offer
    REJECTED_BY_EMPLOYER,
    // These might be initiated by the volunteer, so less likely to be set by employer actions:
    // DECLINED_BY_VOLUNTEER, // Volunteer declined offer
    // WITHDRAWN // Volunteer withdrew application
}
*/




