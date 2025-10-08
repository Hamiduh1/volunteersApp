package com.example.volunteersApp.ui.browse

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.observe

//import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.adapters.JobAdapter
import com.example.volunteersApp.databinding.FragmentBrowseJobsBinding
import com.example.volunteersApp.models.Job
import com.example.volunteersApp.viewmodels.JobViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.HashSet

class BrowseJobsFragment : Fragment(), JobAdapter.OnJobInteractionListener {

    private var _binding: FragmentBrowseJobsBinding? = null
    private val binding get() = _binding!!

    private lateinit var jobAdapter: JobAdapter
    private lateinit var jobViewModel: JobViewModel
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUserAppliedJobIds = HashSet<String>()

    private val args: BrowseJobsFragmentArgs by navArgs()

    companion object {
        private const val TAG = "BrowseJobsFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing.")
        jobViewModel = ViewModelProvider(this)[JobViewModel::class.java]
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        args.categoryFilter?.let { category ->
            if (category.isNotEmpty()) {
                Log.d(TAG, "Category filter from arguments: $category")
                jobViewModel.setCategoryFilter(category)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up UI and observers.")

        setupRecyclerView()
        setupUIListeners()
        setupObservers()

        loadCurrentUserApplications()

        if (args.categoryFilter.isNullOrEmpty() && (jobViewModel.jobs.value == null || jobViewModel.jobs.value!!.isEmpty())) {
            Log.d(TAG, "No category from args, and no initial jobs. Fetching general jobs.")
            jobViewModel.fetchJobs()
        }
    }

    private fun setupRecyclerView() {
        jobAdapter = JobAdapter(requireContext(), this, HashSet())
        binding.recyclerViewBrowseJobs.apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            adapter = jobAdapter
        }
        Log.d(TAG, "RecyclerView setup complete.")
    }

    private fun setupUIListeners() {
        binding.swipeRefreshLayoutBrowseJobs.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh triggered.")
            jobViewModel.refreshJobs()
            loadCurrentUserApplications()
        }

        binding.buttonSearchJobs.setOnClickListener { performSearch() }
        binding.editTextSearchJobs.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = binding.editTextSearchJobs.text.toString().trim()
        Log.d(TAG, "Performing search for query: '$query'")
        jobViewModel.setSearchQuery(query)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val currentActivity = activity ?: return
        val view = currentActivity.currentFocus
        if (view != null) {
            val imm = currentActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun loadCurrentUserApplications() {
        val currentUser = mAuth.currentUser
        if (currentUser == null) {
            currentUserAppliedJobIds.clear()
            if (::jobAdapter.isInitialized) {
                jobAdapter.setAppliedJobIds(HashSet())
            }
            return@loadCurrentUserApplications
        }
        val volunteerUid = currentUser.uid
        Log.d(TAG, "Loading applications for user: $volunteerUid for JOBS")

        db.collection("job_applications")
            .whereEqualTo("volunteerUid", volunteerUid)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                val newAppliedJobIds = HashSet<String>()
                if (!documents.isEmpty) {
                    for (doc in documents) {
                        doc.getString("jobId")?.let { jobId -> newAppliedJobIds.add(jobId) }
                    }
                }
                Log.d(TAG, "Fetched ${newAppliedJobIds.size} applied job IDs for user $volunteerUid")
                currentUserAppliedJobIds = newAppliedJobIds
                if (::jobAdapter.isInitialized) {
                    jobAdapter.setAppliedJobIds(HashSet(currentUserAppliedJobIds))
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Error fetching user's job applications", e)
                Toast.makeText(context, R.string.error_fetching_application_status, Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupObservers() {
        jobViewModel.jobs.observe(viewLifecycleOwner) { jobs: List<Job>? ->
            Log.d(TAG, "Jobs LiveData updated. Count: ${jobs?.size ?: "null"}")
            if (jobs != null) {
                jobAdapter.submitList(jobs)
                binding.textViewStatusBrowseJobs.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
                if (jobs.isEmpty()) binding.textViewStatusBrowseJobs.text = getString(R.string.no_jobs_found_browse)
            } else {
                jobAdapter.submitList(emptyList())
                binding.textViewStatusBrowseJobs.text = getString(R.string.no_jobs_found_browse)
                binding.textViewStatusBrowseJobs.visibility = View.VISIBLE
            }
        }

        jobViewModel.isLoading.observe(viewLifecycleOwner) { isLoading: Boolean? ->
            isLoading?.let {
                binding.progressBarBrowseJobs.visibility = if (it && !binding.swipeRefreshLayoutBrowseJobs.isRefreshing) View.VISIBLE else View.GONE
                if (!it) binding.swipeRefreshLayoutBrowseJobs.isRefreshing = false
            }
        }

        jobViewModel.errorMessage.observe(viewLifecycleOwner) { error: String? ->
            if (!error.isNullOrEmpty()) {
                Log.e(TAG, "ErrorMessage LiveData updated: $error")
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                // Optionally update UI to show error text
            }
        }

        // CORRECTED: Observe the new success message LiveData
        jobViewModel.successMessage.observe(viewLifecycleOwner) { success ->
            if (!success.isNullOrEmpty()) {
                Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
                // Refresh the application statuses after a successful application
                loadCurrentUserApplications()
                jobViewModel.clearSuccessMessage() // Reset the message so it doesn't show again
            }
        }
    }

    override fun onJobClick(job: Job) {
        if (job.jobId.isEmpty()) {
            Log.w(TAG, "Job clicked with empty ID.")
            Toast.makeText(context, R.string.cannot_open_job_details_no_id, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Job clicked: ${job.title} (ID: ${job.jobId})")
        try {
            val action: NavDirections = BrowseJobsFragmentDirections.actionNavBrowseJobsToJobDetail(job.jobId)
            NavHostFragment.findNavController(this).navigate(action)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation to JobDetailFragment failed. Check NavGraph and Action ID.", e)
            Toast.makeText(context, R.string.error_navigating_to_details, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onApplyJobClick(job: Job, position: Int) {
        val currentUser = mAuth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, R.string.must_be_logged_in_to_apply_job, Toast.LENGTH_SHORT).show()
            return
        }
        if (job.jobId.isEmpty()) {
            Toast.makeText(context, R.string.job_data_incomplete_for_apply, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUserAppliedJobIds.contains(job.jobId)) {
            Toast.makeText(context, R.string.already_applied_to_this_job, Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Apply clicked for job: ${job.title}")

        // CORRECTED: Call the ViewModel without chaining listeners
        jobViewModel.applyForJob(
            jobId = job.jobId,
            volunteerUid = currentUser.uid,
            jobTitle = job.title,
            employerId = job.employerId,
            employerName = job.employerName
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
