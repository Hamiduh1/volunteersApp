package com.example.volunteersApp.organizer

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter // For Spinner
import android.widget.Spinner // For Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.savedstate.SavedStateRegistryOwner
import com.example.volunteersApp.R // Ensure R is imported
import com.example.volunteersApp.databinding.FragmentApplicationDetailBinding
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.EventApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.repository.ApplicationRepository
import com.example.volunteersApp.viewmodels.ApplicationViewModel // CORRECTED IMPORT
import com.example.volunteersApp.viewmodels.ApplicationViewModelFactory
import java.util.Locale


class ApplicationDetailFragment : Fragment(), RejectApplicationDialogFragment.RejectionReasonListener {

    private var _binding: FragmentApplicationDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ApplicationViewModel
    private val args: ApplicationDetailFragmentArgs by navArgs() // Using Safe Args

    private var currentDisplayedApplication: EventApplication? = null
    // Event ID will now primarily come from the loaded application or nav args if directly passed for context
    private var contextualEventId: String? = null

    private var applicationToUpdateIdForDialog: String? = null // For rejection dialog

    // Define status options for the spinner
    private val statusOptions by lazy {
        EventApplicationStatus.values().map { it.name.replace("_", " ").capitalizeWords() }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AppDetailFrag", "onCreate called")

        // The repository is now correctly referenced from the 'repository' package
        val applicationRepository = ApplicationRepository()
        val viewModelFactory = ApplicationViewModelFactory(
            owner = this, // 'as SavedStateRegistryOwner' is redundant
            applicationRepository = applicationRepository,
            defaultArgs = arguments // Safe Args passes arguments bundle here
        )
        viewModel = ViewModelProvider(this, viewModelFactory)[ApplicationViewModel::class.java]
        Log.d("AppDetailFrag", "ViewModel initialized")
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

        if (args.applicationId.isEmpty()) { // Check Safe Arg directly
            Log.e("AppDetailFrag", "Application ID from navArgs is empty. Cannot load details.")
            // FIXED: Using string resource
            Toast.makeText(context, R.string.error_application_id_missing, Toast.LENGTH_LONG).show()
            if (isAdded) findNavController().popBackStack()
        } else {
            Log.d("AppDetailFrag", "Received application ID from navArgs: ${args.applicationId}")
            // FIXED: 'args.eventId' is now a valid reference from Safe Args
            viewModel.fetchApplicationById(args.applicationId, args.eventId)
        }
    }

    private fun setupStatusSpinner(currentStatus: String?) {
        val spinner: Spinner = binding.spinnerApplicationStatus
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            statusOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter

        currentStatus?.let {
            val currentStatusFormatted = it.replace("_", " ").capitalizeWords()
            val currentPosition = statusOptions.indexOf(currentStatusFormatted)
            if (currentPosition >= 0) {
                spinner.setSelection(currentPosition)
            }
        }

        binding.buttonUpdateStatus.setOnClickListener {
            currentDisplayedApplication?.let { app ->
                // FIXED: Use uppercase() instead of the deprecated toUpperCase()
                val selectedStatusName = spinner.selectedItem.toString().replace(" ", "_").uppercase(Locale.ROOT)
                try {
                    val newStatusEnum = EventApplicationStatus.valueOf(selectedStatusName)
                    if (newStatusEnum.name != app.status) {
                        if (newStatusEnum == EventApplicationStatus.REJECTED) {
                            applicationToUpdateIdForDialog = app.applicationId
                            val dialog = RejectApplicationDialogFragment.newInstance()
                            dialog.setRejectionReasonListener(this@ApplicationDetailFragment)
                            dialog.show(childFragmentManager, RejectApplicationDialogFragment.TAG)
                        } else {
                            viewModel.updateApplicationStatus(
                                applicationId = app.applicationId,
                                // FIXED: Ensure eventId is available from multiple sources
                                eventId = app.eventId ?: contextualEventId ?: args.eventId,
                                newStatus = newStatusEnum
                            )
                        }
                    } else {
                        Toast.makeText(context, R.string.status_already_set, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("AppDetailFrag", "Invalid status selected: $selectedStatusName", e)
                    // FIXED: Using string resource
                    Toast.makeText(context, R.string.invalid_status_selected, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.buttonApproveApplication.visibility = View.GONE
        binding.buttonRejectApplication.visibility = View.GONE
    }


    private fun observeViewModel() {
        Log.d("AppDetailFrag", "Setting up ViewModel observers")
        viewModel.selectedApplication.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) {
                Log.w("AppDetailFrag", "selectedApplication observer: Binding is null or fragment not added.")
                return@observe
            }
            Log.d("AppDetailFrag", "selectedApplication LiveData changed: $resource")
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarDetail.visibility = View.VISIBLE
                    binding.textViewDetailError.visibility = View.GONE
                    setFieldsVisibility(View.GONE)
                    binding.spinnerApplicationStatus.visibility = View.GONE
                    binding.buttonUpdateStatus.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBarDetail.visibility = View.GONE
                    resource.data?.let { application ->
                        currentDisplayedApplication = application
                        contextualEventId = application.eventId // Store eventId from loaded application
                        populateUi(application)
                        setFieldsVisibility(View.VISIBLE)
                        setupStatusSpinner(application.status)
                        binding.spinnerApplicationStatus.visibility = View.VISIBLE
                        binding.buttonUpdateStatus.visibility = View.VISIBLE

                        Log.d("AppDetailFrag", "Successfully loaded application: ${application.applicationId} for event ${application.eventId}")
                    } ?: run {
                        binding.textViewDetailError.text = getString(R.string.application_details_not_found)
                        binding.textViewDetailError.visibility = View.VISIBLE
                        setFieldsVisibility(View.GONE)
                        binding.spinnerApplicationStatus.visibility = View.GONE
                        binding.buttonUpdateStatus.visibility = View.GONE
                        Log.w("AppDetailFrag", "Application data is null for ID: ${args.applicationId}")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarDetail.visibility = View.GONE
                    val errorMessage = resource.message ?: getString(R.string.unknown_error)
                    binding.textViewDetailError.text = getString(R.string.error_loading_details_param, errorMessage)
                    binding.textViewDetailError.visibility = View.VISIBLE
                    setFieldsVisibility(View.GONE)
                    binding.spinnerApplicationStatus.visibility = View.GONE
                    binding.buttonUpdateStatus.visibility = View.GONE
                    Log.e("AppDetailFrag", "Error loading app details: ${resource.message}")
                }
            }
        }

        viewModel.applicationUpdateStatus.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) {
                Log.w("AppDetailFrag", "applicationUpdateStatus observer: Binding is null or fragment not added.")
                return@observe
            }
            Log.d("AppDetailFrag", "applicationUpdateStatus LiveData changed: $resource")
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarDetail.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBarDetail.visibility = View.GONE
                    Toast.makeText(context, R.string.application_status_updated_successfully, Toast.LENGTH_SHORT).show()
                    Log.d("AppDetailFrag", "Application status updated successfully.")
                    // Re-fetch to ensure UI reflects the latest status including reason for rejection
                    viewModel.fetchApplicationById(args.applicationId, args.eventId)
                }
                is Resource.Error -> {
                    binding.progressBarDetail.visibility = View.GONE
                    val errorMessage = resource.message ?: getString(R.string.unknown_error)
                    Toast.makeText(context, getString(R.string.update_failed_param, errorMessage), Toast.LENGTH_LONG).show()
                    Log.e("AppDetailFrag", "Error updating status: ${resource.message}")
                }
            }
        }
    }

    private fun setFieldsVisibility(visibility: Int) {
        if (_binding == null) return
        val viewsToToggle = listOf(
            binding.textViewDetailApplicantNameLabel, binding.textViewDetailApplicantName,
            binding.textViewDetailEventTitleLabel, binding.textViewDetailEventTitle,
            binding.textViewDetailApplicantEmailLabel, binding.textViewDetailApplicantEmail,
            binding.textViewDetailApplicationStatusLabel, binding.textViewDetailApplicationStatusText,
            binding.textViewDetailAppliedDateLabel, binding.textViewDetailAppliedDate,
            binding.textViewDetailApplicantNotesLabel, binding.textViewDetailApplicantNotes,
            binding.labelSpinnerStatus
        )
        viewsToToggle.forEach { it.visibility = visibility }
    }

    private fun populateUi(application: EventApplication) {
        if (_binding == null) return

        val naString = getString(R.string.n_a)
        binding.textViewDetailApplicantName.text = application.volunteerName.takeIf { !it.isNullOrEmpty() } ?: naString
        binding.textViewDetailEventTitle.text = application.eventTitle.takeIf { !it.isNullOrEmpty() } ?: naString
        binding.textViewDetailApplicantEmail.text = application.volunteerEmail.takeIf { !it.isNullOrEmpty() } ?: naString
        binding.textViewDetailApplicationStatusText.text = application.status?.replace("_", " ")?.capitalizeWords() ?: naString

        binding.textViewDetailAppliedDate.text = application.applicationTimestamp?.toDate()?.let { date ->
            context?.let { DateFormat.getDateFormat(it).format(date) }
        } ?: naString

        if (!application.notes.isNullOrEmpty()) {
            binding.textViewDetailApplicantNotesLabel.visibility = View.VISIBLE
            binding.textViewDetailApplicantNotes.visibility = View.VISIBLE
            binding.textViewDetailApplicantNotes.text = application.notes
        } else {
            binding.textViewDetailApplicantNotesLabel.visibility = View.GONE
            binding.textViewDetailApplicantNotes.visibility = View.GONE
        }

        if (application.status == EventApplicationStatus.REJECTED.name && !application.reasonForRejection.isNullOrEmpty()) {
            binding.textViewDetailRejectionReasonLabel.visibility = View.VISIBLE
            binding.textViewDetailRejectionReason.visibility = View.VISIBLE
            binding.textViewDetailRejectionReason.text = application.reasonForRejection
        } else {
            binding.textViewDetailRejectionReasonLabel.visibility = View.GONE
            binding.textViewDetailRejectionReason.visibility = View.GONE
        }
    }

    override fun onRejectionReasonEntered(reason: String) {
        applicationToUpdateIdForDialog?.let { appId ->
            currentDisplayedApplication?.let { app ->
                Log.d("AppDetailFrag", "Rejection reason entered for $appId for event ${app.eventId}: '$reason'")
                viewModel.updateApplicationStatus(
                    applicationId = appId,
                    // FIXED: Ensure eventId is available
                    eventId = app.eventId ?: contextualEventId ?: args.eventId,
                    newStatus = EventApplicationStatus.REJECTED,
                    reason = reason.ifEmpty { null }
                )
            }
        }
        applicationToUpdateIdForDialog = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("AppDetailFrag", "onDestroyView called.")
        currentDisplayedApplication = null
        _binding = null
    }

    // Helper to capitalize words in status for display
    // FIXED: Use lowercase() and replaceFirstChar() for better capitalization logic
    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
