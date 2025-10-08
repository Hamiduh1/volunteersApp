/** The JobDetailFragment no longer performs any database checks.
 * It is now a "dumb" view that simply observes LiveData and updates the UI,
 * which is the recommended Android architecture. The checkIfUserApplied method
 * and hasApplied variable have been removed from the fragment because
 * the ViewModel now handles this.  Real-Time Updates: When an employer changes
 * the status field in a job_applications document from "pending" to "approved,"
 * the applicationListener in the ViewModel will fire. This will update
 * the _applicationStatus LiveData, which in turn causes the updateApplyButtonState
 * method in the fragment to run, changing the button text and color instantly
 * for the applicant. **/

package com.example.volunteersApp.ui.jobdetail

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
//import androidx.glance.visibility
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentJobDetailBinding
import com.example.volunteersApp.models.Job
import com.example.volunteersApp.viewmodels.JobDetailViewModel
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Locale

class JobDetailFragment : Fragment() {

    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: JobDetailViewModel
    private val args: JobDetailFragmentArgs by navArgs()

    companion object {
        private const val TAG = "JobDetailFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[JobDetailViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeViewModel()

        val jobId = args.jobId
        if (jobId.isNotEmpty()) {
            // MODIFIED: Tell the ViewModel to start listening for real-time updates
            viewModel.listenForJobDetails(jobId)
        } else {
            showError(getString(R.string.job_not_found_or_invalid_id))
        }

        // FIXED: Use the correct binding property name 'buttonApplyNowDetail'
        binding.buttonApplyNowDetail.setOnClickListener {
            // TODO: Add logic to apply for the job via the ViewModel
            Toast.makeText(context, "Apply button clicked!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToolbar() {
        if (activity is AppCompatActivity) {
            // FIXED: Use the correct binding property name 'toolbarJobDetail'
            (activity as AppCompatActivity).setSupportActionBar(binding.toolbarJobDetail)
            (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
            binding.toolbarJobDetail.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.job.observe(viewLifecycleOwner) { job ->
            job?.let {
                populateUi(it)
                showContent()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // FIXED: Use the correct binding property name 'progressBarJobDetail'
            binding.progressBarJobDetail.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let { showError(it) }
        }

        // ADDED: Observer for the application status
        viewModel.applicationStatus.observe(viewLifecycleOwner) { status ->
            updateApplyButtonState(status)
        }
    }

    private fun populateUi(job: Job) {
        // FIXED: Use the correct binding property names throughout
        binding.toolbarJobDetail.title = job.title
        binding.textViewJobTitleDetail.text = job.title
        binding.textViewEmployerNameDetail.text = job.employerName
        binding.textViewJobLocationDetail.text = job.locationString
        job.employerLogoUrl?.let { url ->
            if (url.isNotEmpty()) {
                Picasso.get().load(url)
                    .placeholder(R.drawable.ic_default_work_outline)
                    .error(R.drawable.ic_default_work_outline)
                    .into(binding.imageViewEmployerLogoDetail)
            }
        }
    }

    /**
     * MODIFIED: This function now updates the button based on the real-time status from the ViewModel.
     */
    private fun updateApplyButtonState(status: JobDetailViewModel.ApplicationStatus) {
        // FIXED: Use the correct binding property name 'buttonApplyNowDetail'
        val button = binding.buttonApplyNowDetail
        when (status) {
            JobDetailViewModel.ApplicationStatus.APPLIED_PENDING -> {
                button.text = getString(R.string.application_sent_status)
                button.isEnabled = false
                button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.grey_400)
            }
            JobDetailViewModel.ApplicationStatus.APPROVED -> {
                button.text = getString(R.string.application_approved_status)
                button.isEnabled = false
                button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green500) //grren_500
            }
            JobDetailViewModel.ApplicationStatus.REJECTED -> {
                button.text = getString(R.string.application_not_selected_status)
                button.isEnabled = false
                button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red500) //red_500
            }
            JobDetailViewModel.ApplicationStatus.CAN_APPLY -> {
                button.text = getString(R.string.apply_now)
                button.isEnabled = true
                button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.buttonColor)
            }
            JobDetailViewModel.ApplicationStatus.JOB_CLOSED -> {
                button.text = getString(R.string.job_not_accepting_applications)
                button.isEnabled = false
                button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.grey_400)
            }
            else -> { // UNKNOWN
                button.visibility = View.GONE
            }
        }
        if (button.visibility == View.GONE) button.visibility = View.VISIBLE // Make sure it's visible after status is known
    }

    private fun showContent() {
        // FIXED: Use the correct binding property names
        binding.textViewErrorJobDetail.visibility = View.GONE
        binding.scrollViewJobDetail.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        // FIXED: Use the correct binding property names
        binding.progressBarJobDetail.visibility = View.GONE
        binding.scrollViewJobDetail.visibility = View.GONE
        binding.textViewErrorJobDetail.visibility = View.VISIBLE
        binding.textViewErrorJobDetail.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
