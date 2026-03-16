package com.example.volunteersApp.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class AvailableJobsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    AvailableJobsScreen(
                        // Updated AvailableJobsFragment.kt
                        onJobClick = { jobId ->
                            try {
                                // Now using its own generated Directions class
                                val action = AvailableJobsFragmentDirections.actionAvailableJobsToJobDetail(jobId)
                                findNavController().navigate(action)
                            } catch (e: Exception) {
                                // Fallback using the explicit ID
                                val bundle = Bundle().apply { putString("jobId", jobId) }
                                findNavController().navigate(com.example.volunteersApp.R.id.jobDetailFragment, bundle)
                            }
                        }
                    )
                }
            }
        }
    }
}
