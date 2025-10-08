/** since myvolunteeractivityfragment is gone and there is no

need of this file. Everything is handled by MyJobsfragment

package com.example.volunteersApp.ui.myactivity.myapplied // Or your chosen package

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
import androidx.lifecycle.observe
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.adapters.MyAppliedJobAdapter // You will create this adapter
import com.example.volunteersApp.databinding.FragmentMyAppliedJobsBinding // ViewBinding
import com.example.volunteersApp.models.JobApplication // You will create/use this model
import com.example.volunteersApp.ui.myactivity.MyVolunteerActivityFragmentDirections
import com.example.volunteersApp.viewmodels.MyAppliedJobsViewModel // You will create this ViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlin.jvm.java

class MyAppliedJobsFragment : Fragment(), MyAppliedJobAdapter.OnAppliedJobClickListener {

    private var _binding: FragmentMyAppliedJobsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MyAppliedJobsViewModel
    private lateinit var appliedJobAdapter: MyAppliedJobAdapter
    private lateinit var currentUserId: String

    companion object {
        private const val TAG = "MyAppliedJobsFragment"
        @JvmStatic
        fun newInstance() = MyAppliedJobsFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "User not logged in!")
            // Handle not logged in case - perhaps navigate away or show login prompt from parent
            Toast.makeText(context, "Please log in to view applied jobs.", Toast.LENGTH_LONG).show()
            // Consider returning or preventing further initialization if critical
            return
        }
        currentUserId = currentUser.uid
        viewModel = ViewModelProvider(this)[MyAppliedJobsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        _binding = FragmentMyAppliedJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        if (!::currentUserId.isInitialized) { // Check if userId was initialized (user was logged in)
            updateUIVisibility(true, R.string.please_log_in_to_see_applied_jobs, false)
            binding.swipeRefreshLayoutAppliedJobs.isEnabled = false
            return
        }

        setupRecyclerView()
        setupSwipeRefreshLayout()
        observeViewModel()

        viewModel.fetchAppliedJobs(currentUserId)
    }

    private fun setupRecyclerView() {
        if (!isAdded) return
        appliedJobAdapter = MyAppliedJobAdapter(this) // Pass click listener
        binding.recyclerViewAppliedJobs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appliedJobAdapter
        }
        Log.d(TAG, "RecyclerView setup for applied jobs.")
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayoutAppliedJobs.setColorSchemeResources(
            R.color.colorPrimary, R.color.colorAccent, R.color.colorPrimaryDark
        )
        binding.swipeRefreshLayoutAppliedJobs.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh applied jobs.")
            if (::currentUserId.isInitialized) {
                viewModel.fetchAppliedJobs(currentUserId, true) // true for force refresh
            } else {
                binding.swipeRefreshLayoutAppliedJobs.isRefreshing = false
                Toast.makeText(context, getString(R.string.login_required_to_refresh), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.appliedJobs.observe(viewLifecycleOwner) { jobApplications ->
            if (!isAdded) return@observe
            Log.d(TAG, "Applied jobs LiveData updated with ${jobApplications?.size ?: 0} items.")
            appliedJobAdapter.submitList(jobApplications)
            updateUIVisibility(
                jobApplications.isNullOrEmpty(),
                if (jobApplications.isNullOrEmpty()) R.string.no_jobs_applied_for_yet else 0,
                false
            )
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded) return@observe
            Log.d(TAG, "isLoading LiveData for applied jobs: $isLoading")
            if (isLoading && !binding.swipeRefreshLayoutAppliedJobs.isRefreshing) {
                binding.progressBarAppliedJobs.visibility = View.VISIBLE
            } else if (!isLoading) {
                binding.progressBarAppliedJobs.visibility = View.GONE
                binding.swipeRefreshLayoutAppliedJobs.isRefreshing = false
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!isAdded) return@observe
            if (!error.isNullOrEmpty()) {
                Log.e(TAG, "Error LiveData for applied jobs: $error")
                updateUIVisibility(true, 0, false) // 0 means don't set specific text from here
                binding.textViewStatusAppliedJobs.text = error // Display the error message
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUIVisibility(listIsEmpty: Boolean, emptyListMessageResId: Int, showProgress: Boolean) {
        if (!isAdded || _binding == null) return // Check _binding too

        binding.progressBarAppliedJobs.visibility = if (showProgress) View.VISIBLE else View.GONE
        if (!showProgress) {
            binding.swipeRefreshLayoutAppliedJobs.isRefreshing = false
        }

        if (listIsEmpty && !showProgress) {
            binding.textViewStatusAppliedJobs.text = if (emptyListMessageResId != 0) getString(emptyListMessageResId) else ""
            binding.textViewStatusAppliedJobs.visibility = View.VISIBLE
            binding.recyclerViewAppliedJobs.visibility = View.GONE
        } else if (!showProgress) {
            binding.textViewStatusAppliedJobs.visibility = View.GONE
            binding.recyclerViewAppliedJobs.visibility = View.VISIBLE
        }
    }

    // Implementation of MyAppliedJobAdapter.OnAppliedJobClickListener
    override fun onAppliedJobClicked(jobApplication: JobApplication) {
        Log.d(TAG, "Applied job clicked: ${jobApplication.jobTitle}, ID: ${jobApplication.jobId}")
        if (jobApplication.jobId.isNullOrEmpty()) {
            Toast.makeText(context, "Cannot open job details: Job ID is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        // Navigate to JobDetailFragment
        // This assumes MyVolunteerActivityFragment (the parent) handles or defines this action
        try {
            val action = MyVolunteerActivityFragmentDirections.actionNavMyActivityToJobDetail(jobApplication.jobId!!) // Pass jobId
            NavHostFragment.findNavController(this).navigate(action)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation to JobDetailFragment failed.", e)
            Toast.makeText(context, "Error opening job details.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView for applied jobs")
        binding.recyclerViewAppliedJobs.adapter = null
        _binding = null
    }
}
**/