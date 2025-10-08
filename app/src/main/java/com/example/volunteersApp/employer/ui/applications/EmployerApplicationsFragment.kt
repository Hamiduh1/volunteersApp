package com.example.volunteersApp.employer.ui.applications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
//import androidx.glance.visibility
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
// import androidx.navigation.fragment.navArgs // Uncomment if using Safe Args
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R // For R.id.action_... and R.string...
import com.example.volunteersApp.databinding.FragmentEmployerApplicationsBinding
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.Job // << CORRECTED: Using your Job.kt model
import com.example.volunteersApp.models.Resource // << CORRECTED: Assuming Resource is in 'utils'
import com.google.firebase.firestore.FirebaseFirestore

class EmployerApplicationsFragment : Fragment() {

    private var _binding: FragmentEmployerApplicationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var navController: NavController
    private val viewModel: EmployerApplicationsViewModel by viewModels()

    private var jobId: String? = null
    private var jobTitle: String? = null // To store the fetched or passed job title

    private lateinit var applicationsAdapter: EmployerApplicationsAdapter

    // TODO: This direct DB access for fetchJobTitle should be moved to a Repository and accessed via ViewModel.
    // For now, keeping it here for simplicity of the current request.
    private lateinit var db: FirebaseFirestore

    companion object {
        private const val TAG = "EmpApplicationsFrag"
        const val ARG_JOB_ID = "job_id"
        const val ARG_JOB_TITLE = "job_title"
        // ARG_APPLICATION_ID, ARG_JOB_ID, ARG_VOLUNTEER_ID are used for navigating TO EmployerApplicationDetailFragment
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance() // For the temporary fetchJobTitle

        // Argument retrieval
        // Consider using Safe Args for type safety and cleaner argument passing.
        // val safeArgs: EmployerApplicationsFragmentArgs by navArgs()
        // jobId = safeArgs.jobId
        // jobTitle = safeArgs.jobTitle

        arguments?.let {
            jobId = it.getString(ARG_JOB_ID)
            jobTitle = it.getString(ARG_JOB_TITLE) // Can be null if not passed
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployerApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = findNavController()

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_job_applications)

        if (jobId == null) {
            Log.e(TAG, "Job ID is null. Cannot proceed.")
            Toast.makeText(requireContext(), getString(R.string.error_job_id_missing), Toast.LENGTH_LONG).show()
            binding.textViewJobTitleForApplications.text = getString(R.string.select_job_to_see_applications)
            binding.textViewNoApplications.text = getString(R.string.cannot_load_applications_no_ids)
            binding.textViewNoApplications.visibility = View.VISIBLE
            binding.progressBarApplications.visibility = View.GONE
            binding.recyclerViewApplications.visibility = View.GONE
            // Optional: navController.popBackStack()
            return
        }

        setupRecyclerView()
        setupObservers()

        // Set or fetch the job title for display
        if (!jobTitle.isNullOrEmpty()) {
            binding.textViewJobTitleForApplications.text = getString(R.string.applications_for_dynamic_job_title, jobTitle)
        } else {
            fetchJobTitle(jobId!!) // Fetch if not passed (jobId is confirmed not null here)
        }

        // Trigger data fetching from ViewModel
        viewModel.fetchApplicationsForJob(jobId!!) // jobId is confirmed not null
    }

    private fun setupRecyclerView() {
        applicationsAdapter = EmployerApplicationsAdapter(requireContext()) { jobApplication ->
            Log.d(TAG, "Clicked application: ${jobApplication.applicationId} for job ${jobApplication.jobTitle ?: "N/A"}")

            // Prepare arguments for detail fragment
            val bundle = Bundle().apply {
                putString(EmployerApplicationDetailFragment.ARG_APPLICATION_DOC_ID, jobApplication.applicationId)
                putString(EmployerApplicationDetailFragment.ARG_JOB_ID, jobApplication.jobId) // Should be same as this fragment's jobId
                putString(EmployerApplicationDetailFragment.ARG_VOLUNTEER_ID, jobApplication.volunteerUid)
            }
            // Navigate using action defined in your nav_graph.xml
            // Ensure R.id.action_employerApplicationsFragment_to_employerApplicationDetailFragment is correct
            navController.navigate(R.id.action_employerApplicationsFragment_to_employerApplicationDetailFragment, bundle)
        }
        binding.recyclerViewApplications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = applicationsAdapter
        }
    }

    private fun setupObservers() {
        viewModel.jobApplications.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    // This is now primarily handled by the isLoading LiveData for the initial screen load.
                    // If you have pull-to-refresh, you might handle its loading state here or in isLoading.
                    // If resource.data is not null here, it means you're showing stale data while loading new.
                    if (resource.data.isNullOrEmpty()) { // Only show full progress if list is not yet loaded or is empty
                        binding.progressBarApplications.visibility = View.VISIBLE
                        binding.recyclerViewApplications.visibility = View.GONE
                        binding.textViewNoApplications.visibility = View.GONE
                    }
                }
                is Resource.Success -> {
                    binding.progressBarApplications.visibility = View.GONE
                    val applications = resource.data
                    if (applications.isNullOrEmpty()) {
                        binding.textViewNoApplications.text = getString(R.string.no_applications_found_for_this_job)
                        binding.textViewNoApplications.visibility = View.VISIBLE
                        binding.recyclerViewApplications.visibility = View.GONE
                        applicationsAdapter.submitList(emptyList()) // Ensure adapter is cleared
                    } else {
                        applicationsAdapter.submitList(applications)
                        binding.recyclerViewApplications.visibility = View.VISIBLE
                        binding.textViewNoApplications.visibility = View.GONE
                    }
                }
                is Resource.Error -> {
                    binding.progressBarApplications.visibility = View.GONE
                    val errorMessage = resource.message ?: getString(R.string.error_loading_applications)
                    binding.textViewNoApplications.text = errorMessage
                    binding.textViewNoApplications.visibility = View.VISIBLE
                    binding.recyclerViewApplications.visibility = View.GONE
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error loading applications: $errorMessage")
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // This observer is better for the initial full screen progress bar if the list is not yet populated
            // and for swipe-to-refresh states.
            if (isLoading && (viewModel.jobApplications.value?.data.isNullOrEmpty())) {
                binding.progressBarApplications.visibility = View.VISIBLE
                binding.recyclerViewApplications.visibility = View.GONE
                binding.textViewNoApplications.visibility = View.GONE
            } else if (!isLoading) {
                // If loading is finished, and jobApplications is still null or its data is empty,
                // the jobApplications observer will handle showing the "no applications" message.
                // This just ensures the progress bar is hidden if it was shown by this observer.
                binding.progressBarApplications.visibility = View.GONE
            }
            // If using SwipeRefreshLayout:
            // binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                // This can be used for errors not directly related to the list loading,
                // or if you want a more persistent error display (e.g., a Snackbar).
                Log.e(TAG, "ViewModel Global Error: $it")
                // Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                // viewModel.clearErrorMessage() // Important if the error is handled (e.g., shown in a dialog)
            }
        }
    }

    // TODO: Move this to JobRepository and access via ViewModel
    private fun fetchJobTitle(currentJobId: String) {
        if (currentJobId.isEmpty()) {
            binding.textViewJobTitleForApplications.text = getString(R.string.select_job_to_see_applications)
            return
        }
        // Set a loading/default state while fetching
        binding.textViewJobTitleForApplications.text = getString(R.string.applications_for_job_with_id, currentJobId)

        val jobDocRef = db.collection("jobs").document(currentJobId) // Assuming your Firestore collection is "jobs"
        jobDocRef.get().addOnSuccessListener { documentSnapshot ->
            if (!isAdded || _binding == null) return@addOnSuccessListener // Check fragment is still valid

            if (documentSnapshot.exists()) {
                val job = documentSnapshot.toObject(Job::class.java) // Using Job.kt model
                job?.title?.let { fetchedTitle ->
                    if (fetchedTitle.isNotEmpty()) {
                        this.jobTitle = fetchedTitle // Store for potential re-use (e.g. on rotation if not using ViewModel for this)
                        binding.textViewJobTitleForApplications.text = getString(R.string.applications_for_dynamic_job_title, fetchedTitle)
                    } else {
                        Log.w(TAG, "Fetched job $currentJobId but title is empty.")
                        // Keep "Applications for Job ID: ..."
                    }
                } ?: {
                    Log.w(TAG, "Failed to parse job object or title is null for job $currentJobId.")
                    // Keep "Applications for Job ID: ..."
                }
            } else {
                Log.w(TAG, "Job document $currentJobId not found.")
                binding.textViewJobTitleForApplications.text = getString(R.string.job_not_found_applications, currentJobId)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error fetching job title for $currentJobId", e)
            if (isAdded && _binding != null) {
                // Keep "Applications for Job ID: ..." or show a specific fetch error
                binding.textViewJobTitleForApplications.text = getString(R.string.applications_for_job_with_id, currentJobId)
                Toast.makeText(context, "Failed to fetch job title.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Crucial to prevent memory leaks
    }
}
