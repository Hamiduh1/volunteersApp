package com.example.volunteersApp.organizer

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.savedstate.SavedStateRegistryOwner // Required for the factory
import com.example.volunteersApp.databinding.FragmentApplicationDetailBinding
import com.example.volunteersApp.models.ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
// Ensure ApplicationViewModel is imported from the correct package (organizer)
import com.example.volunteersApp.organizer.ApplicationViewModel
// Ensure ApplicationViewModelFactory is imported from its correct package
import com.example.volunteersApp.viewmodels.ApplicationViewModelFactory
import com.example.volunteersApp.repository.ApplicationRepository

class ApplicationDetailFragment : Fragment(), RejectApplicationDialogFragment.RejectionReasonListener {

    private var _binding: FragmentApplicationDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ApplicationViewModel
    private val args: ApplicationDetailFragmentArgs by navArgs()
    private var currentApplicationIdFromArgs: String? = null
    private var currentDisplayedApplication: ApplicationModel? = null

    private var applicationToUpdateIdForDialog: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AppDetailFrag", "onCreate called")

        val applicationRepository = ApplicationRepository()

        // Correctly instantiate ApplicationViewModelFactory
        val viewModelFactory = ApplicationViewModelFactory(
            owner = this as SavedStateRegistryOwner, // The Fragment itself is a SavedStateRegistryOwner
            applicationRepository = applicationRepository,
            defaultArgs = arguments // Pass the fragment's arguments to be available in SavedStateHandle
        )

        // Initialize ApplicationViewModel using the factory, scoped to this Fragment
        viewModel = ViewModelProvider(this, viewModelFactory)[ApplicationViewModel::class.java]
        Log.d("AppDetailFrag", "ViewModel initialized")

        currentApplicationIdFromArgs = args.applicationId
        if (currentApplicationIdFromArgs == null) {
            Log.e("AppDetailFrag", "Application ID from navArgs is null. Cannot load details.")
            Toast.makeText(context, "Error: Application ID missing.", Toast.LENGTH_LONG).show()
            if (isAdded) { // Prevent crash if fragment is not added
                findNavController().popBackStack()
            }
        } else {
            Log.d("AppDetailFrag", "Received application ID from navArgs: $currentApplicationIdFromArgs")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("AppDetailFrag", "onCreateView called")
        _binding = FragmentApplicationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("AppDetailFrag", "onViewCreated called")

        observeViewModel()

        currentApplicationIdFromArgs?.let {
            Log.d("AppDetailFrag", "Fetching application details for ID: $it")
            viewModel.fetchApplicationById(it)
        } ?: Log.w("AppDetailFrag", "currentApplicationIdFromArgs is null in onViewCreated, cannot fetch details.")

        setupActionButtons()
    }

    private fun setupActionButtons() {
        binding.buttonApproveApplication.setOnClickListener {
            currentDisplayedApplication?.let { app ->
                if (app.applicationStatus != ApplicationStatus.APPROVED.name) {
                    Log.d("AppDetailFrag", "Approving application: ${app.applicationId}")
                    viewModel.updateApplicationStatus(app.applicationId, ApplicationStatus.APPROVED)
                } else {
                    Toast.makeText(context, "Application is already approved.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.buttonRejectApplication.setOnClickListener {
            currentDisplayedApplication?.let { app ->
                if (app.applicationStatus != ApplicationStatus.REJECTED.name) {
                    applicationToUpdateIdForDialog = app.applicationId
                    val dialog = RejectApplicationDialogFragment.newInstance()
                    dialog.setRejectionReasonListener(this@ApplicationDetailFragment)
                    // Use childFragmentManager for dialogs shown from a Fragment for better lifecycle management
                    dialog.show(childFragmentManager, RejectApplicationDialogFragment.TAG)
                    Log.d("AppDetailFrag", "Showing rejection dialog for application: ${app.applicationId}")
                } else {
                    Toast.makeText(context, "Application is already rejected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        Log.d("AppDetailFrag", "Setting up ViewModel observers")
        viewModel.selectedApplication.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) { // Ensure view is available
                Log.w("AppDetailFrag", "selectedApplication observer: Binding is null or fragment not added.")
                return@observe
            }
            Log.d("AppDetailFrag", "selectedApplication LiveData changed: $resource")
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarDetail.visibility = View.VISIBLE
                    binding.textViewDetailError.visibility = View.GONE
                    setFieldsAndActionsVisibility(View.GONE)
                }
                is Resource.Success -> {
                    binding.progressBarDetail.visibility = View.GONE
                    resource.data?.let { application ->
                        currentDisplayedApplication = application
                        populateUi(application)
                        setFieldsAndActionsVisibility(View.VISIBLE)
                        Log.d("AppDetailFrag", "Successfully loaded application: ${application.applicationId}")
                    } ?: run {
                        binding.textViewDetailError.text = "Application details not found."
                        binding.textViewDetailError.visibility = View.VISIBLE
                        setFieldsAndActionsVisibility(View.GONE)
                        Log.w("AppDetailFrag", "Application data is null for ID: $currentApplicationIdFromArgs")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarDetail.visibility = View.GONE
                    binding.textViewDetailError.text = "Error: ${resource.message ?: "Unknown error"}"
                    binding.textViewDetailError.visibility = View.VISIBLE
                    setFieldsAndActionsVisibility(View.GONE)
                    Log.e("AppDetailFrag", "Error loading app details: ${resource.message}")
                }
            }
        }

        viewModel.applicationUpdateStatus.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) {  // Ensure view is available
                Log.w("AppDetailFrag", "applicationUpdateStatus observer: Binding is null or fragment not added.")
                return@observe
            }
            Log.d("AppDetailFrag", "applicationUpdateStatus LiveData changed: $resource")
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarDetail.visibility = View.VISIBLE // Or a more subtle loader for updates
                }
                is Resource.Success -> {
                    binding.progressBarDetail.visibility = View.GONE
                    Toast.makeText(context, "Application status updated!", Toast.LENGTH_SHORT).show()
                    Log.d("AppDetailFrag", "Application status updated successfully.")
                    // If selectedApplication is not automatically updated by Firestore snapshot,
                    // you might need to re-fetch it here:
                    // currentApplicationIdFromArgs?.let { viewModel.fetchApplicationById(it) }
                }
                is Resource.Error -> {
                    binding.progressBarDetail.visibility = View.GONE
                    Toast.makeText(context, "Update failed: ${resource.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    Log.e("AppDetailFrag", "Error updating status: ${resource.message}")
                }
            }
        }
    }

    private fun setFieldsAndActionsVisibility(visibility: Int) {
        if (_binding == null) return // Safety check

        val viewsToToggle = listOf(
            binding.textViewDetailApplicantNameLabel, binding.textViewDetailApplicantName,
            binding.textViewDetailEventTitleLabel, binding.textViewDetailEventTitle,
            binding.textViewDetailApplicantEmailLabel, binding.textViewDetailApplicantEmail,
            binding.textViewDetailApplicationStatusLabel, binding.textViewDetailApplicationStatus,
            binding.textViewDetailAppliedDateLabel, binding.textViewDetailAppliedDate,
            binding.textViewDetailApplicantNotesLabel, binding.textViewDetailApplicantNotes
        )
        viewsToToggle.forEach { it.visibility = visibility }
        binding.linearLayoutActions.visibility = visibility
        // Rejection reason fields are handled in populateUi based on data
    }

    private fun populateUi(application: ApplicationModel) {
        if (_binding == null) return // Safety check

        binding.textViewDetailApplicantName.text = application.volunteerName.takeIf { it.isNotEmpty() } ?: "N/A"
        binding.textViewDetailEventTitle.text = application.eventTitle.takeIf { it.isNotEmpty() } ?: "N/A"
        binding.textViewDetailApplicantEmail.text = application.volunteerEmail.takeIf { it.isNotEmpty() } ?: "N/A"
        binding.textViewDetailApplicationStatus.text = application.applicationStatus
        binding.textViewDetailAppliedDate.text = application.appliedAt?.toDate()?.let { date ->
            context?.let { DateFormat.getDateFormat(it).format(date) }
        } ?: "N/A"

        if (!application.notes.isNullOrEmpty()) {
            binding.textViewDetailApplicantNotesLabel.visibility = View.VISIBLE
            binding.textViewDetailApplicantNotes.visibility = View.VISIBLE
            binding.textViewDetailApplicantNotes.text = application.notes
        } else {
            binding.textViewDetailApplicantNotesLabel.visibility = View.GONE
            binding.textViewDetailApplicantNotes.visibility = View.GONE
        }

        if (application.applicationStatus == ApplicationStatus.REJECTED.name && !application.reasonForRejection.isNullOrEmpty()) {
            binding.textViewDetailRejectionReasonLabel.visibility = View.VISIBLE
            binding.textViewDetailRejectionReason.visibility = View.VISIBLE
            binding.textViewDetailRejectionReason.text = application.reasonForRejection
        } else {
            binding.textViewDetailRejectionReasonLabel.visibility = View.GONE
            binding.textViewDetailRejectionReason.visibility = View.GONE
        }

        updateButtonStates(application.applicationStatus)
    }

    private fun updateButtonStates(status: String) {
        if (_binding == null) return // Safety check
        when (status) {
            ApplicationStatus.APPROVED.name -> {
                binding.buttonApproveApplication.isEnabled = false
                binding.buttonRejectApplication.isEnabled = true
            }
            ApplicationStatus.REJECTED.name -> {
                binding.buttonApproveApplication.isEnabled = true
                binding.buttonRejectApplication.isEnabled = false
            }
            else -> { // PENDING, WAITLISTED, etc.
                binding.buttonApproveApplication.isEnabled = true
                binding.buttonRejectApplication.isEnabled = true
            }
        }
    }

    override fun onRejectionReasonEntered(reason: String) {
        applicationToUpdateIdForDialog?.let { appId ->
            Log.d("AppDetailFrag", "Rejection reason entered for $appId: '$reason'")
            viewModel.updateApplicationStatus(appId, ApplicationStatus.REJECTED, reason.ifEmpty { null })
        }
        applicationToUpdateIdForDialog = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("AppDetailFrag", "onDestroyView called.")
        currentDisplayedApplication = null // Clear reference
        _binding = null
    }
}

