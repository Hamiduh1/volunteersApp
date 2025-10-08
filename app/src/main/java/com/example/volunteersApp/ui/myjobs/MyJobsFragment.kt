// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/ui/myjobs/MyJobsFragment.kt

// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/ui/myjobs/MyJobsFragment.kt

package com.example.volunteersApp.ui.myjobs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.semantics.setText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.adapters.MyJobsAdapter
import com.example.volunteersApp.databinding.FragmentMyJobsBinding
import com.example.volunteersApp.models.Job
import com.example.volunteersApp.viewmodels.MyJobsViewModel
import com.google.android.material.tabs.TabLayout

class MyJobsFragment : Fragment(), MyJobsAdapter.OnMyJobListener {

    private var _binding: FragmentMyJobsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MyJobsViewModel
    private lateinit var myJobsAdapter: MyJobsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyJobsBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[MyJobsViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // *** UPDATE 1: Call the new toolbar setup method ***
        setupToolbar()
        setupRecyclerView()
        setupTabLayout()
        observeViewModel()
    }

    // *** UPDATE 2: Add the new method to configure the Toolbar ***
    private fun setupToolbar() {
        // This makes the fragment's toolbar act as the main action bar
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbarMyJobs)

        val navController = findNavController()
        // This configuration ensures the "Up" button appears and is handled by NavController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        NavigationUI.setupWithNavController(binding.toolbarMyJobs, navController, appBarConfiguration)

        // Optionally set the title if it's not set in the XML or you want to override it
        binding.toolbarMyJobs.title = getString(R.string.my_jobs)
    }

    private fun setupRecyclerView() {
        myJobsAdapter = MyJobsAdapter(this) // 'this' is the listener
        binding.recyclerViewMyJobs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = myJobsAdapter
        }
    }

    private fun setupTabLayout() {
        binding.tabLayoutMyJobs.removeAllTabs()
        binding.tabLayoutMyJobs.addTab(binding.tabLayoutMyJobs.newTab().setText(R.string.applied))
        binding.tabLayoutMyJobs.addTab(binding.tabLayoutMyJobs.newTab().setText(R.string.approved))
        binding.tabLayoutMyJobs.addTab(binding.tabLayoutMyJobs.newTab().setText(R.string.completed))

        binding.tabLayoutMyJobs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = when (tab?.position) {
                    0 -> MyJobsViewModel.JobFilter.APPLIED
                    1 -> MyJobsViewModel.JobFilter.APPROVED
                    2 -> MyJobsViewModel.JobFilter.COMPLETED
                    else -> MyJobsViewModel.JobFilter.APPLIED // Default
                }
                viewModel.setFilter(filter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) { /* No-op */ }
            override fun onTabReselected(tab: TabLayout.Tab?) { /* No-op */ }
        })
    }

    private fun observeViewModel() {
        viewModel.filteredJobs.observe(viewLifecycleOwner) { jobs ->
            Log.d("MyJobsFragment", "Updating adapter with ${jobs.size} jobs.")
            myJobsAdapter.submitList(jobs)
            binding.textViewNoJobs.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBarMyJobs.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                binding.textViewNoJobs.text = error
                binding.textViewNoJobs.visibility = View.VISIBLE
            }
        }
    }

    override fun onJobClick(job: Job) {
        if (job.jobId.isNullOrEmpty()) {
            Toast.makeText(context, "Cannot open job details.", Toast.LENGTH_SHORT).show()
            return
        }
        val action = MyJobsFragmentDirections.actionNavMyJobsToJobDetail(job.jobId)
        findNavController().navigate(action)
    }

    override fun onWithdrawJobClick(job: Job) {
        if (job.jobId.isNullOrEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.withdraw_application)
            .setMessage(getString(R.string.withdraw_confirmation_message, job.title))
            .setPositiveButton(R.string.withdraw) { _, _ ->
                viewModel.withdrawFromJob(job.jobId)
                Toast.makeText(context, R.string.withdrawing_application_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewMyJobs.adapter = null
        _binding = null
    }
}
