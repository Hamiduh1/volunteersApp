package com.example.volunteersApp.organizer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.adapters.ApplicationsAdapter
import com.example.volunteersApp.databinding.FragmentOrganizerApplicationsBinding
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.repository.ApplicationRepository
import com.example.volunteersApp.viewmodels.ApplicationViewModel
import com.example.volunteersApp.viewmodels.ApplicationViewModelFactory

class OrganizerApplicationsFragment : Fragment() {

    private var _binding: FragmentOrganizerApplicationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ApplicationViewModel
    private lateinit var applicationsAdapter: ApplicationsAdapter
    private val args: OrganizerApplicationsFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrganizerApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("OrganizerAppsFrag", "onViewCreated called")

        setupViewModel()
        setupRecyclerView()
        setupSwipeToRefresh()
        observeViewModel()

        // Initial data fetch
        fetchData()
    }

    private fun setupViewModel() {
        // ViewModelFactory doesn't need the 'arguments' bundle passed manually.
        // The AbstractSavedStateViewModelFactory handles it automatically.
        val applicationRepository = ApplicationRepository()
        val viewModelFactory = ApplicationViewModelFactory(this, applicationRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[ApplicationViewModel::class.java]
    }

    private fun fetchData() {
        val eventIdToFilterBy = args.eventId
        viewModel.fetchApplicationsForOrganizer(eventIdToFilterBy)
    }

    private fun setupRecyclerView() {
        applicationsAdapter = ApplicationsAdapter { selectedApplication ->
            Log.d("OrganizerAppsFrag", "Clicked on application: ${selectedApplication.applicationId}")
            val action = OrganizerApplicationsFragmentDirections
                .actionOrganizerApplicationsFragmentToApplicationDetailFragment(
                    selectedApplication.applicationId,
                    // Pass the eventId from the application model to the detail view
                    selectedApplication.eventId
                )
            findNavController().navigate(action)
        }
        binding.recyclerViewApplications.apply {
            adapter = applicationsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    // ADDED: Setup for SwipeRefreshLayout
    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("OrganizerAppsFrag", "onRefresh called")
            fetchData() // Re-fetch data when user swipes
        }
    }

    private fun observeViewModel() {
        viewModel.applicationsList.observe(viewLifecycleOwner) { resource ->
            // Hide the refresh indicator once data is received
            binding.swipeRefreshLayout.isRefreshing = false

            when (resource) {
                is Resource.Loading -> {
                    // Only show the central progress bar if the list is empty
                    if (applicationsAdapter.currentList.isEmpty()) {
                        showLoading()
                    }
                }
                is Resource.Success -> {
                    val apps = resource.data
                    if (!apps.isNullOrEmpty()) {
                        applicationsAdapter.submitList(apps)
                        showContent()
                    } else {
                        showEmptyState(getString(R.string.no_applications_found))
                    }
                }
                is Resource.Error -> {
                    showError(resource.message ?: getString(R.string.unknown_error))
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBarApplications.visibility = View.VISIBLE
        binding.textViewApplicationsStatus.visibility = View.VISIBLE
        binding.textViewApplicationsStatus.text = getString(R.string.loading_applications)
        binding.recyclerViewApplications.visibility = View.GONE
    }

    private fun showContent() {
        binding.progressBarApplications.visibility = View.GONE
        binding.textViewApplicationsStatus.visibility = View.GONE
        binding.recyclerViewApplications.visibility = View.VISIBLE
    }

    private fun showEmptyState(message: String) {
        binding.progressBarApplications.visibility = View.GONE
        binding.recyclerViewApplications.visibility = View.GONE
        binding.textViewApplicationsStatus.visibility = View.VISIBLE
        binding.textViewApplicationsStatus.text = message
    }

    private fun showError(errorMessage: String) {
        binding.progressBarApplications.visibility = View.GONE
        binding.recyclerViewApplications.visibility = View.GONE
        binding.textViewApplicationsStatus.visibility = View.VISIBLE
        binding.textViewApplicationsStatus.text = getString(R.string.error_loading_applications, errorMessage)
        Log.e("OrganizerAppsFrag", "Error: $errorMessage")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to avoid memory leaks
    }
}
