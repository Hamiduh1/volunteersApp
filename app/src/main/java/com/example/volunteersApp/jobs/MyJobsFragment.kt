package com.example.volunteersApp.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Bridge Fragment that hosts the modern Compose-based MyJobs UI.
 */
class MyJobsFragment : Fragment() {

    private val viewModel: MyJobsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    MyJobsScreen(
                        viewModel = viewModel,
                        onJobClick = { jobId ->
                            val action = MyJobsFragmentDirections.actionNavMyJobsToJobDetail(jobId)
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
