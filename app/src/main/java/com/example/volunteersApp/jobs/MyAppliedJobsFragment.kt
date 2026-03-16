package com.example.volunteersApp.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Bridge Fragment that hosts the Compose-based MyAppliedJobs UI.
 * Refactored to use Safe Args for navigation.
 */
class MyAppliedJobsFragment : Fragment() {

    private val viewModel: MyAppliedJobsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                VolunteersAppTheme {
                    MyAppliedJobsScreen(
                        viewModel = viewModel,
                        onBack = { findNavController().popBackStack() },
                        onItemClick = { application ->
                            // FIXED: Now using Safe Args for type-safe navigation
                            val jobId = application.jobId
                            if (!jobId.isNullOrEmpty()) {
                                try {
                                    val action = MyAppliedJobsFragmentDirections.actionMyAppliedJobsToJobDetail(jobId)
                                    findNavController().navigate(action)
                                } catch (e: Exception) {
                                    // Fallback if Safe Args generation hasn't completed
                                    val bundle = Bundle().apply { putString("jobId", jobId) }
                                    findNavController().navigate(com.example.volunteersApp.R.id.jobDetailFragment, bundle)
                                }
                            } else {
                                Toast.makeText(requireContext(), "Job ID not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
