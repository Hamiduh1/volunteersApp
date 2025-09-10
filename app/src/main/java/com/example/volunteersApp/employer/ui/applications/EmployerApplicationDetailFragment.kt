package com.example.volunteersApp.employer.ui.applications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.graphics.vector.path
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.graphics.vector.path
//import androidx.compose.ui.semantics.error
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
//import androidx.glance.background
//import androidx.glance.visibility
import androidx.navigation.NavController
import androidx.navigation.Navigation
//import androidx.wear.compose.material.placeholder
import com.bumptech.glide.Glide
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentEmployerApplicationDetailBinding
import com.example.volunteersApp.models.ApplicationModel // Your existing ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus // Your ApplicationStatus enum
import com.example.volunteersApp.models.User // Assuming you have a UserModel for volunteer details
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class EmployerApplicationDetailFragment : Fragment() {

    private var _binding: FragmentEmployerApplicationDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var navController: NavController
    private lateinit var db: FirebaseFirestore

    private var applicationDocId: String? = null
    private var eventId: String? = null
    private var volunteerId: String? = null

    companion object {
        private const val TAG = "EmpAppDetailFrag"
        const val ARG_APPLICATION_DOC_ID = "application_doc_id"
        const val ARG_EVENT_ID = "event_id"
        const val ARG_VOLUNTEER_ID = "volunteer_id"

        // Firestore paths - Adjust if your structure is different
        private const val JOB_POSTINGS_COLLECTION = "events" // Or "job_postings"
        private const val APPLICATIONS_SUBCOLLECTION = "applications"
        private const val USERS_COLLECTION = "users"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()
        arguments?.let {
            applicationDocId = it.getString(ARG_APPLICATION_DOC_ID)
            eventId = it.getString(ARG_EVENT_ID)
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

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_application_detail)

        if (applicationDocId == null || eventId == null || volunteerId == null) {
            Toast.makeText(context, "Error: Missing required application data.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Missing applicationDocId, eventId, or volunteerId in arguments.")
            navController.popBackStack()
            return
        }

        setupButtonListeners()
        loadApplicationDetails()
    }

    private fun setupButtonListeners() {
        binding.buttonAcceptApplication.setOnClickListener { updateApplicationStatus(ApplicationStatus.ACCEPTED.name) }
        binding.buttonRejectApplication.setOnClickListener { updateApplicationStatus(ApplicationStatus.REJECTED.name) }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (!isAdded || _binding == null) return
        binding.progressBarApplicationDetail.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.contentContainerApplicationDetail.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun loadApplicationDetails() {
        setLoadingState(true)

        // Path to the specific application document using applicationDocId
        val applicationRef = db.collection(JOB_POSTINGS_COLLECTION)
            .document(eventId!!) // eventId is checked not null
            .collection(APPLICATIONS_SUBCOLLECTION)
            .document(applicationDocId!!) // applicationDocId is checked not null

        applicationRef.get().addOnCompleteListener { task ->
            if (!isAdded || _binding == null) return@addOnCompleteListener

            if (task.isSuccessful) {
                val appDocument = task.result
                if (appDocument != null && appDocument.exists()) {
                    val application = appDocument.toObject(ApplicationModel::class.java)
                    application?.let {
                        populateApplicationData(it)
                        // Load general volunteer details (name, profile image) using volunteerId
                        // if they are not sufficiently denormalized in ApplicationModel
                        if (it.volunteerName.isEmpty() || it.volunteerProfileImageUrl.isNullOrEmpty()) {
                            loadVolunteerUserDetails(it.volunteerId)
                        } else {
                            // If data is already in ApplicationModel, no need to fetch user separately unless for more fields
                            binding.textViewVolunteerNameDetail.text = it.volunteerName
                            Glide.with(this@EmployerApplicationDetailFragment)
                                .load(it.volunteerProfileImageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(binding.imageViewVolunteerProfileDetail)
                            setLoadingState(false) // All necessary data loaded
                        }
                    } ?: run {
                        handleLoadError("Application data could not be parsed.")
                    }
                } else {
                    Log.w(TAG, "Application document not found: ${applicationRef.path}")
                    handleLoadError(getString(R.string.application_details_not_found))
                }
            } else {
                Log.e(TAG, "Error fetching application details: ", task.exception)
                handleLoadError(getString(R.string.failed_to_load_application_details))
            }
        }
    }

    private fun populateApplicationData(application: ApplicationModel) {
        if (!isAdded || _binding == null) return

        // Application-specific data (already present in ApplicationModel)
        binding.textViewVolunteerNameDetail.text = application.volunteerName.ifEmpty { getString(R.string.volunteer_details_not_available) }
        binding.textViewVolunteerEmailDetail.text = application.volunteerEmail.ifEmpty { "N/A" }
        //binding.textViewVolunteerPhoneDetail.text = application.volunteerPhoneNumber.ifEmpty { "N/A" }
        binding.textViewVolunteerPhoneDetail.text = application.volunteerPhoneNumber?.ifEmpty { "N/A" } ?: "N/A"


        application.appliedAt?.toDate()?.let { date ->
            val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            binding.textViewApplicationDateDetail.text = sdf.format(date)
        } ?: run {
            binding.textViewApplicationDateDetail.text = "N/A"
        }

        val statusString = application.applicationStatus
        binding.textViewApplicationStatusDetail.text = capitalize(statusString)

        // Set status background
        val statusBgRes = when (statusString.uppercase(Locale.ROOT)) {
            ApplicationStatus.PENDING.name -> R.drawable.status_background_pending
            ApplicationStatus.ACCEPTED.name -> R.drawable.status_background_accepted
            ApplicationStatus.REJECTED.name -> R.drawable.status_background_rejected
            else -> R.drawable.status_background_default
        }
        binding.textViewApplicationStatusDetail.background = ContextCompat.getDrawable(requireContext(), statusBgRes)
        binding.textViewApplicationStatusDetail.setTextColor(
            if (statusString.uppercase(Locale.ROOT) == ApplicationStatus.PENDING.name ||
                statusString.uppercase(Locale.ROOT) == ApplicationStatus.ACCEPTED.name ||
                statusString.uppercase(Locale.ROOT) == ApplicationStatus.REJECTED.name )
                ContextCompat.getColor(requireContext(), android.R.color.white)
            else
                ContextCompat.getColor(requireContext(), android.R.color.black)
        )

        binding.textViewVolunteerPhoneDetail.text = application.volunteerPhoneNumber?.ifEmpty { "N/A" } ?: "N/A"

        binding.textViewCoverLetterDetail.text = application.notes?.ifEmpty { "No message provided." } ?: "No message provided."
        if (application.notes.isNullOrEmpty()) {
            binding.textViewLabelCoverLetter.visibility = View.GONE
            binding.textViewCoverLetterDetail.visibility = View.GONE
        } else {
            binding.textViewLabelCoverLetter.visibility = View.VISIBLE
            binding.textViewCoverLetterDetail.visibility = View.VISIBLE
        }


        // Control visibility of action buttons based on status
        if (ApplicationStatus.PENDING.name.equals(statusString, ignoreCase = true)) {
            binding.layoutActionButtons.visibility = View.VISIBLE
        } else {
            binding.layoutActionButtons.visibility = View.GONE
        }
    }

    // Inside EmployerApplicationDetailFragment.kt

    // ... (other methods and imports, including 'import com.example.volunteersApp.models.User;') ...

    private fun loadVolunteerUserDetails(currentVolunteerId: String) {
        // This method is called if volunteerName or profileImageUrl are missing from ApplicationModel
        // Or if you need more user details not denormalized into the application.
        val userRef = db.collection(USERS_COLLECTION).document(currentVolunteerId)
        userRef.get().addOnCompleteListener { task ->
            if (!isAdded || _binding == null) return@addOnCompleteListener
            setLoadingState(false) // All data loading attempts are now complete for the screen

            if (task.isSuccessful) {
                val userDocument = task.result
                if (userDocument != null && userDocument.exists()) {
                    // Use your existing User.java class for deserialization
                    val user = userDocument.toObject(User::class.java) // <<< CHANGED HERE

                    user?.let { volunteer -> // Use the instance of your User.java class
                        // Only update if these were empty or not set from ApplicationModel
                        if (binding.textViewVolunteerNameDetail.text.toString() == getString(R.string.volunteer_details_not_available) ||
                            binding.textViewVolunteerNameDetail.text.isEmpty()) {
                            // Use the getter from User.java
                            binding.textViewVolunteerNameDetail.text = volunteer.name ?: getString(R.string.volunteer_details_not_available)
                        }

                        // Update email if it was missing in ApplicationModel and present in User model
                        if (binding.textViewVolunteerEmailDetail.text.toString() == "N/A" && !volunteer.email.isNullOrEmpty()){
                            binding.textViewVolunteerEmailDetail.text = volunteer.email
                        }

                        // Your User.java model does not have a 'phone' field.
                        // So, textViewVolunteerPhoneDetail will remain as it was set from ApplicationModel,
                        // or "N/A" if ApplicationModel didn't have it.
                        // If phone number is crucial, you'd need to consider adding it to User.java
                        // (but you said not to change it) or store it elsewhere.
                        // For now, we cannot populate it from this User object.
                        // binding.textViewVolunteerPhoneDetail.text = volunteer.getPhone() // This line would cause an error

                        // Use the getter from User.java
                        Glide.with(this@EmployerApplicationDetailFragment)
                            .load(volunteer.profileImageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(binding.imageViewVolunteerProfileDetail)
                    }
                } else {
                    Log.w(TAG, "Volunteer user document not found: ${userRef.path}")
                    if (binding.textViewVolunteerNameDetail.text.isEmpty() || binding.textViewVolunteerNameDetail.text.toString() == getString(R.string.volunteer_details_not_available)) {
                        binding.textViewVolunteerNameDetail.text = getString(R.string.volunteer_details_not_available)
                    }
                }
            } else {
                Log.e(TAG, "Error fetching volunteer user details: ", task.exception)
                Toast.makeText(context, getString(R.string.could_not_load_full_volunteer_details), Toast.LENGTH_SHORT).show()
            }
        }
    }

// ... (rest of EmployerApplicationDetailFragment.kt) ...




    private fun updateApplicationStatus(newStatus: String) {
        setLoadingState(true)
        binding.buttonAcceptApplication.isEnabled = false
        binding.buttonRejectApplication.isEnabled = false

        val applicationRef = db.collection(JOB_POSTINGS_COLLECTION)
            .document(eventId!!)
            .collection(APPLICATIONS_SUBCOLLECTION)
            .document(applicationDocId!!)

        val updates = hashMapOf<String, Any>(
            "applicationStatus" to newStatus,
            "lastUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp() // Update timestamp
        )

        applicationRef.update(updates)
            .addOnSuccessListener {
                if (!isAdded || _binding == null) return@addOnSuccessListener
                setLoadingState(false)
                Toast.makeText(context, getString(R.string.application_updated_successfully, capitalize(newStatus)), Toast.LENGTH_SHORT).show()
                binding.textViewApplicationStatusDetail.text = capitalize(newStatus)

                val statusBgRes = when (newStatus.uppercase(Locale.ROOT)) {
                    ApplicationStatus.PENDING.name -> R.drawable.status_background_pending
                    ApplicationStatus.ACCEPTED.name -> R.drawable.status_background_accepted
                    ApplicationStatus.REJECTED.name -> R.drawable.status_background_rejected
                    else -> R.drawable.status_background_default
                }
                binding.textViewApplicationStatusDetail.background = ContextCompat.getDrawable(requireContext(), statusBgRes)
                binding.textViewApplicationStatusDetail.setTextColor(
                    if (newStatus.uppercase(Locale.ROOT) == ApplicationStatus.PENDING.name ||
                        newStatus.uppercase(Locale.ROOT) == ApplicationStatus.ACCEPTED.name ||
                        newStatus.uppercase(Locale.ROOT) == ApplicationStatus.REJECTED.name )
                        ContextCompat.getColor(requireContext(), android.R.color.white)
                    else
                        ContextCompat.getColor(requireContext(), android.R.color.black)
                )


                if (ApplicationStatus.PENDING.name.equals(newStatus, ignoreCase = true)) {
                    binding.layoutActionButtons.visibility = View.VISIBLE
                    binding.buttonAcceptApplication.isEnabled = true
                    binding.buttonRejectApplication.isEnabled = true
                } else {
                    binding.layoutActionButtons.visibility = View.GONE
                }
                // TODO: Optionally send a notification to the volunteer
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                setLoadingState(false)
                if (ApplicationStatus.PENDING.name.equals(binding.textViewApplicationStatusDetail.text.toString(), ignoreCase = true)) {
                    binding.buttonAcceptApplication.isEnabled = true
                    binding.buttonRejectApplication.isEnabled = true
                }
                Log.e(TAG, "Error updating application status", e)
                Toast.makeText(context, getString(R.string.failed_to_update_status), Toast.LENGTH_SHORT).show()
            }
    }

    private fun capitalize(str: String?): String {
        if (str.isNullOrEmpty()) {
            return str ?: "N/A"
        }
        return str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun handleLoadError(message: String) {
        if (!isAdded || _binding == null) return
        setLoadingState(false)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        // Optionally, display a permanent error message in the UI
        // binding.textViewVolunteerNameDetail.text = message
        // binding.contentContainerApplicationDetail.visibility = View.VISIBLE // Ensure some part of UI is visible to show error
        navController.popBackStack() // Or go back if it's a fatal error
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
