/**  This is handled by MyJobsFragment.kt


package com.example.volunteersApp.ui.myjobs // Or a shared location

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.lifecycle.ViewModelProvider // Added for potential ViewModel usage
import androidx.navigation.fragment.findNavController
//import androidx.preference.contains
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.adapters.JobAdapter
import com.example.volunteersApp.databinding.FragmentJobListBinding
import com.example.volunteersApp.models.Job
// Consider using a ViewModel to handle data fetching and applied job IDs
// import com.example.volunteersApp.viewmodels.JobListViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObjects

// You might need to adjust this if MyJobsFragmentDirections is not always the correct context
// This implies JobListFragment is primarily used within the flow of MyJobsFragment
import com.example.volunteersApp.ui.myjobs.MyJobsFragmentDirections

class JobListFragment : Fragment(), JobAdapter.OnJobInteractionListener { // Implement the interface

    private var _binding: FragmentJobListBinding? = null
    private val binding get() = _binding!!

    private lateinit var jobAdapter: JobAdapter
    // private val jobsList = mutableListOf<Job>() // No longer directly manage list like this for ListAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var filterType: String? = null
    private var userAppliedJobIds: MutableSet<String> = mutableSetOf() // Store applied job IDs

    // Optional: Consider using a ViewModel
    // private lateinit var viewModel: JobListViewModel

    companion object {
        const val ARG_FILTER_TYPE = "filter_type"
        const val FILTER_ALL_POSTED = "ALL_POSTED"
        const val FILTER_APPLIED = "APPLIED" // Example: User's applied jobs
        const val FILTER_POSTED_BY_USER = "POSTED_BY_USER" // Example: Jobs posted by current user (if employer)
        const val FILTER_PENDING = "PENDING_APPROVAL" // Or just "PENDING" if you prefer
        const val FILTER_ACCEPTED = "ACCEPTED_APPLICATION" // Or just "ACCEPTED"

        // Add more filter constants as needed

        fun newInstance(filterType: String): JobListFragment {
            val fragment = JobListFragment()
            val args = Bundle()
            args.putString(ARG_FILTER_TYPE, filterType)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filterType = it.getString(ARG_FILTER_TYPE)
        }
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Optional: Initialize ViewModel
        // viewModel = ViewModelProvider(this).get(JobListViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadUserAppliedJobs() // Load applied jobs first to correctly set button states
        loadJobs() // Then load the main list of jobs

        // Optional: Observe LiveData from ViewModel
        // viewModel.jobs.observe(viewLifecycleOwner) { jobs ->
        //     jobAdapter.submitList(jobs)
        //     updateNoJobsView(jobs.isEmpty())
        // }
        // viewModel.appliedJobIds.observe(viewLifecycleOwner) { appliedIds ->
        //    userAppliedJobIds = appliedIds
        //    jobAdapter.setAppliedJobIds(appliedIds) // Update adapter's known applied jobs
        //    // Potentially re-submit list or notify specific items if view needs full refresh based on this
        // }
        // viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
        //    binding.progressBarJobList?.visibility = if (isLoading) View.VISIBLE else View.GONE
        // }
        // viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
        //    if (!error.isNullOrEmpty()) {
        //        binding.textViewNoJobs?.text = error
        //        binding.textViewNoJobs?.visibility = View.VISIBLE
        //    }
        // }
    }

    private fun setupRecyclerView() {
        // 'this' (the fragment) now implements OnJobInteractionListener
        jobAdapter = JobAdapter(
            requireContext(),
            this,
            userAppliedJobIds // Pass the set, will be populated by loadUserAppliedJobs
        )
        binding.recyclerViewJobs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = jobAdapter
        }
    }

    private fun loadUserAppliedJobs() {
        val currentUserUid = auth.currentUser?.uid ?: return

        // Assuming you have a collection 'job_applications' that stores which user applied to which job
        // This is a simplified example. You might store this information differently.
        firestore.collection("job_applications")
            .whereEqualTo("volunteerUid", currentUserUid)
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                val appliedIds = documents.mapNotNull { it.getString("jobId") }.toMutableSet()
                userAppliedJobIds.clear()
                userAppliedJobIds.addAll(appliedIds)
                jobAdapter.setAppliedJobIds(userAppliedJobIds) // Update adapter
                // After applied jobs are known, you might want to refresh the currently displayed list
                // if it's already loaded, to update button states.
                // However, loadJobs() is called next, which will use the updated set.
                Log.d("JobListFragment", "Loaded applied job IDs: $userAppliedJobIds")
            }
            .addOnFailureListener { e ->
                Log.e("JobListFragment", "Error loading user's applied jobs", e)
                // Handle error, maybe show a toast
            }
    }

    private fun loadJobs() {
        binding.progressBarJobList?.visibility = View.VISIBLE
        binding.textViewNoJobs?.visibility = View.GONE

        val currentUserUid = auth.currentUser?.uid

        // Initial check for filters requiring login
        if (currentUserUid == null && (filterType == FILTER_APPLIED || filterType == FILTER_POSTED_BY_USER)) {
            updateNoJobsView(true, getString(R.string.please_log_in_to_see_my_jobs))
            binding.progressBarJobList?.visibility = View.GONE
            return
        }

        var query: Query = firestore.collection("jobs")

        when (filterType) {
            FILTER_ALL_POSTED -> {
                query = query.whereEqualTo("status", "open")
                    .orderBy("postedDate", Query.Direction.DESCENDING)
            }
            FILTER_APPLIED -> {
                if (currentUserUid == null) { // Should be caught above, but defensive
                    updateNoJobsView(true, getString(R.string.please_log_in_to_see_my_jobs))
                    binding.progressBarJobList?.visibility = View.GONE
                    return
                }
                // Fetch jobs where the user's ID is in an 'applicantIds' array field on the job document
                // This means your Job model should have a way to know if a user has applied,
                // or you perform a second query/check.
                // For simplicity, let's assume 'job_applications' is the source of truth for "applied"
                // and we filter the main job list based on that.
                // This approach is more complex for direct Firestore query unless jobs have 'applicantIds'.
                // If jobs don't have applicantIds, you'd fetch jobs and then filter locally based on userAppliedJobIds,
                // or fetch job documents whose IDs are in userAppliedJobIds.
                // Let's assume you want to get jobs where the current user is an applicant (by ID)
                // This query requires 'applicantIds' field on your 'jobs' documents
                // query = query.whereArrayContains("applicantIds", currentUserUid)

                // A more common approach for "My Applied Jobs": Query 'job_applications' collection.
                // This fragment might be better named or specialized if it only shows applied jobs.
                // For now, if FILTER_APPLIED, we'll fetch based on 'job_applications' and get job details.
                // This is a more complex scenario for a generic JobListFragment.
                // Let's assume for now, if FILTER_APPLIED, we want to see jobs the user has applied to.
                // The current structure of JobAdapter relies on a list of Job objects.
                // We'd need to fetch job IDs from 'job_applications' and then fetch those jobs.
                // This example will be simplified: if applied, it will try to find jobs where
                // the current user's ID appears in an 'applicantIds' field.
                query = query.whereArrayContains("applicantIds", currentUserUid!!) // Requires 'applicantIds' on Job doc
                    .orderBy("postedDate", Query.Direction.DESCENDING)

            }
            FILTER_POSTED_BY_USER -> {
                if (currentUserUid == null) {
                    updateNoJobsView(true, getString(R.string.please_log_in_to_see_my_jobs))
                    binding.progressBarJobList?.visibility = View.GONE
                    return
                }
                // Fetch jobs where 'employerId' matches currentUserUid
                query = query.whereEqualTo("employerId", currentUserUid)
                    .orderBy("postedDate", Query.Direction.DESCENDING)
            }
            // Add cases for FILTER_PENDING, FILTER_ACCEPTED if your Job model and Firestore structure support them directly
            else -> {
                updateNoJobsView(true, getString(R.string.unknown_filter_type))
                binding.progressBarJobList?.visibility = View.GONE
                return
            }
        }

        query.get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                binding.progressBarJobList?.visibility = View.GONE
                if (documents.isEmpty) {
                    val message = when(filterType) {
                        FILTER_ALL_POSTED -> getString(R.string.no_jobs_available_currently)
                        FILTER_APPLIED -> getString(R.string.you_have_not_applied_to_any_jobs)
                        FILTER_POSTED_BY_USER -> getString(R.string.you_have_not_posted_any_jobs)
                        else -> getString(R.string.no_jobs_found_for_this_category)
                    }
                    updateNoJobsView(true, message)
                } else {
                    updateNoJobsView(false)
                    val jobs = documents.toObjects<Job>()
                    jobAdapter.submitList(jobs) // Use submitList for ListAdapter
                }
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                binding.progressBarJobList?.visibility = View.GONE
                updateNoJobsView(true, getString(R.string.error_loading_jobs, exception.localizedMessage))
                Log.e("JobListFragment", "Error loading jobs: ", exception)
            }
    }

    private fun updateNoJobsView(show: Boolean, message: String? = null) {
        if (!isAdded || _binding == null) return

        if (show) {
            binding.textViewNoJobs?.text = message ?: getString(R.string.no_jobs_found) // Default message
            binding.textViewNoJobs?.visibility = View.VISIBLE
            binding.recyclerViewJobs.visibility = View.GONE
        } else {
            binding.textViewNoJobs?.visibility = View.GONE
            binding.recyclerViewJobs.visibility = View.VISIBLE
        }
    }

    // --- JobAdapter.OnJobInteractionListener Implementation ---

    override fun onJobClick(job: Job) {
        Log.d("JobListFragment", "Job clicked: ${job.title}")
        if (job.id.isEmpty()) {
            Toast.makeText(context, "Cannot open job, ID is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // This assumes MyJobsFragmentDirections is appropriate.
            // If JobListFragment is a destination itself or used elsewhere,
            // this navigation action needs to be adjusted or made more generic.
            val action = MyJobsFragmentDirections.actionNavMyJobsToJobDetailFragment(job.id)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e("JobListFragment", "Navigation failed for onJobClick: ${e.message}")
            Toast.makeText(context, "Could not open job details.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onApplyJobClick(job: Job, position: Int) {
        Log.d("JobListFragment", "Apply clicked for job: ${job.title}")
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, R.string.please_log_in_to_apply, Toast.LENGTH_SHORT).show();
            // TODO: Navigate to login screen
            return;
        }

        if (job.id.isEmpty()) {
            Toast.makeText(context, "Cannot apply, job ID is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        if (userAppliedJobIds.contains(job.id)) {
            Toast.makeText(context, R.string.already_applied_to_this_job, Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Show loading indicator on the specific item or globally
        // For simplicity, showing a general toast. For a better UX, disable the button and show progress.
        binding.progressBarJobList?.visibility = View.VISIBLE // Example: show global progress

        val applicationData = hashMapOf(
            "applicationId" to firestore.collection("job_applications").document().id, // Generate new ID
            "jobId" to job.id,
            "jobTitle" to job.title,
            "employerId" to job.employerId, // Assuming Job model has employerId
            "volunteerUid" to currentUser.uid,
            "volunteerName" to (currentUser.displayName ?: "Volunteer"),
            "status" to "pending", // Initial application status
            "applicationTimestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("job_applications").add(applicationData)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                binding.progressBarJobList?.visibility = View.GONE
                Toast.makeText(context, R.string.application_submitted_successfully, Toast.LENGTH_SHORT).show()
                userAppliedJobIds.add(job.id)
                jobAdapter.setAppliedJobIds(userAppliedJobIds) // Update the adapter's knowledge
                jobAdapter.notifyItemChanged(position) // Update the specific item's view

                // Optional: Increment an application counter on the job document
                // This is better done via a Cloud Function for atomicity at scale
                firestore.collection("jobs").document(job.id)
                    .update("applicationCount", FieldValue.increment(1))
                    .addOnFailureListener { e -> Log.w("JobListFragment", "Failed to update applicationCount", e) }

            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                binding.progressBarJobList?.visibility = View.GONE
                Toast.makeText(context, getString(R.string.application_submission_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                Log.e("JobListFragment", "Error submitting application", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
**/