package com.example.volunteersApp.organizer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.savedstate.SavedStateRegistryOwner
import com.example.volunteersApp.R
// Using the ApplicationAdapter from the 'organizer' package
import com.example.volunteersApp.organizer.ApplicationAdapter
import com.example.volunteersApp.databinding.FragmentOrganizerApplicationsBinding
import com.example.volunteersApp.models.ApplicationModel // Assuming your model is ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.repository.ApplicationRepository
// ApplicationViewModel is also in the 'organizer' package
import com.example.volunteersApp.organizer.ApplicationViewModel
// Assuming ApplicationViewModelFactory is in the 'viewmodels' package as per previous context
import com.example.volunteersApp.viewmodels.ApplicationViewModelFactory


class OrganizerApplicationsFragment : Fragment() {

    private var _binding: FragmentOrganizerApplicationsBinding? = null
    private val binding get() = _binding!!

    // ViewModel from the 'organizer' package
    private lateinit var viewModel: ApplicationViewModel
    // Adapter from the 'organizer' package
    private lateinit var applicationAdapter: ApplicationAdapter

    private var currentEventId: String? = null
    private var currentOrganizerId: String? = null

    companion object {
        private const val TAG = "OrganizerAppsFrag"
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_ORGANIZER_ID = "organizer_id"

        @JvmStatic
        fun newInstance(eventId: String? = null, organizerId: String? = null): OrganizerApplicationsFragment {
            return OrganizerApplicationsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EVENT_ID, eventId)
                    putString(ARG_ORGANIZER_ID, organizerId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        arguments?.let {
            currentEventId = it.getString(ARG_EVENT_ID)
            currentOrganizerId = it.getString(ARG_ORGANIZER_ID)
            Log.d(TAG, "Arguments received: eventId='$currentEventId', organizerId='$currentOrganizerId'")
        }

        val applicationRepository = ApplicationRepository()

        // Ensure your ApplicationViewModelFactory's constructor and the ApplicationViewModel's constructor
        // (the one in the 'organizer' package) are compatible with these parameters.
        val factory = ApplicationViewModelFactory(
            owner = this as SavedStateRegistryOwner,
            applicationRepository = applicationRepository,
            defaultArgs = arguments
        )

        // Initialize ApplicationViewModel from the 'organizer' package
        viewModel = ViewModelProvider(this, factory)[ApplicationViewModel::class.java]
        Log.d(TAG, "ApplicationViewModel (organizer) initialized.")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentOrganizerApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        setupRecyclerView()
        observeViewModel()
        loadApplications()
    }

    private fun loadApplications() {
        Log.d(TAG, "loadApplications: eventId='$currentEventId', organizerId='$currentOrganizerId'")
        if (_binding == null) {
            Log.w(TAG, "Binding is null in loadApplications. View likely destroyed.")
            return
        }

        if (!::viewModel.isInitialized) {
            Log.e(TAG, "ViewModel not initialized in loadApplications.")
            binding.textViewEmptyState.text = getString(R.string.error_generic_message)
            binding.textViewEmptyState.visibility = View.VISIBLE
            binding.progressBarApplications.visibility = View.GONE
            return
        }

        if (currentEventId != null) {
            viewModel.fetchApplicationsForEvent(currentEventId!!)
        } else if (currentOrganizerId != null) {
            // Check if your ApplicationViewModel (organizer) has fetchAllManagedApplications
            viewModel.fetchAllManagedApplications(currentOrganizerId!!) // Assuming this method exists
            // Log.w(TAG, "Event ID is null. Fetching all managed applications for organizer $currentOrganizerId.")
        } else {
            Log.e(TAG, "Neither eventId nor organizerId is available to fetch applications.")
            binding.textViewEmptyState.text = getString(R.string.cannot_load_applications_no_ids)
            binding.textViewEmptyState.visibility = View.VISIBLE
            binding.progressBarApplications.visibility = View.GONE
            if (::applicationAdapter.isInitialized) applicationAdapter.submitList(emptyList())
        }
    }

    private fun setupRecyclerView() {
        if (_binding == null) {
            Log.e(TAG, "Binding is null in setupRecyclerView.")
            return
        }
        Log.d(TAG, "setupRecyclerView called")

        // Use the ApplicationAdapter from the 'organizer' package
        applicationAdapter = ApplicationAdapter( // com.example.volunteersApp.organizer.ApplicationAdapter
            context = requireContext(), // Pass context as the first argument as per its constructor
            onApplicationClicked = { application ->
                Log.d(TAG, "Clicked on application: ${application.applicationId} by ${application.volunteerName}")
                // Navigate to detail screen if needed
                // val action = OrganizerApplicationsFragmentDirections.actionOrganizerApplicationsFragmentToApplicationDetailFragment(application.applicationId)
                // findNavController().navigate(action)
                Toast.makeText(context, getString(R.string.clicked_volunteer, application.volunteerName ?: "N/A"), Toast.LENGTH_SHORT).show()
            },
            onApproveClicked = { application ->
                if (!::viewModel.isInitialized) { Log.e(TAG, "Approve: VM not ready"); return@ApplicationAdapter }
                Log.d(TAG, "Approve clicked for: ${application.applicationId}")
                viewModel.updateApplicationStatus(application.applicationId, ApplicationStatus.APPROVED)
            },
            onRejectClicked = { application ->
                if (!::viewModel.isInitialized) { Log.e(TAG, "Reject: VM not ready"); return@ApplicationAdapter }
                Log.d(TAG, "Reject clicked for: ${application.applicationId}")
                val reason: String? = getString(R.string.rejection_reason_placeholder) // Consider a dialog for reason
                viewModel.updateApplicationStatus(application.applicationId, ApplicationStatus.REJECTED, reason)
            }
        )

        binding.recyclerViewApplications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = applicationAdapter
        }
    }

    private fun observeViewModel() {
        if (!isAdded || _binding == null) {
            Log.w(TAG, "Fragment not added or binding is null. Cannot observe ViewModel.")
            return
        }
        if (!::viewModel.isInitialized) {
            Log.e(TAG, "ViewModel not initialized in observeViewModel.")
            return
        }
        Log.d(TAG, "observeViewModel called")

        viewModel.applications.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) {
                Log.w(TAG, "Applications LiveData: Binding null or fragment not added.")
                return@observe
            }
            when (resource) {
                is Resource.Loading -> {
                    Log.d(TAG, "Observing applications: Loading...")
                    binding.progressBarApplications.visibility = View.VISIBLE
                    binding.textViewEmptyState.visibility = View.GONE
                }
                is Resource.Success -> {
                    Log.d(TAG, "Observing applications: Success. Data: ${resource.data?.size ?: 0} items.")
                    binding.progressBarApplications.visibility = View.GONE
                    val applications: List<ApplicationModel>? = resource.data
                    if (applications.isNullOrEmpty()) {
                        binding.textViewEmptyState.text = getString(R.string.no_applications_found)
                        binding.textViewEmptyState.visibility = View.VISIBLE
                        if (::applicationAdapter.isInitialized) applicationAdapter.submitList(emptyList())
                    } else {
                        binding.textViewEmptyState.visibility = View.GONE
                        // ListAdapter's submitList expects List<ApplicationModel>?
                        // Ensure ApplicationModel properties are well-defined for nullability
                        if (::applicationAdapter.isInitialized) applicationAdapter.submitList(applications)
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Observing applications: Error - ${resource.message}")
                    binding.progressBarApplications.visibility = View.GONE
                    binding.textViewEmptyState.text = getString(R.string.error_loading_applications, resource.message ?: "Unknown error")
                    binding.textViewEmptyState.visibility = View.VISIBLE
                    if (::applicationAdapter.isInitialized) applicationAdapter.submitList(emptyList())
                    Toast.makeText(context, getString(R.string.error_loading_applications, resource.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.applicationUpdateStatus.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) {
                Log.w(TAG, "UpdateStatus LiveData: Binding null or fragment not added.")
                return@observe
            }
            when (resource) {
                is Resource.Loading -> {
                    Log.d(TAG, "Observing update status: Loading...")
                    binding.progressBarApplications.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    Log.d(TAG, "Observing update status: Success.")
                    binding.progressBarApplications.visibility = View.GONE
                    Toast.makeText(context, R.string.application_status_updated, Toast.LENGTH_SHORT).show()
                    // Re-fetch or rely on snapshot listener if data doesn't auto-update
                    // if (currentEventId != null) viewModel.fetchApplicationsForEvent(currentEventId!!)
                }
                is Resource.Error -> {
                    Log.e(TAG, "Observing update status: Error - ${resource.message}")
                    binding.progressBarApplications.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.update_failed, resource.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called, setting _binding to null")
        if (::applicationAdapter.isInitialized && binding.recyclerViewApplications.adapter != null) {
            // binding.recyclerViewApplications.adapter = null // To help with potential leaks
        }
        _binding = null
    }
}
