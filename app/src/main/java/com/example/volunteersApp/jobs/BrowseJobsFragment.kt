package com.example.volunteersApp.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class BrowseJobsFragment : Fragment() {

    private lateinit var jobViewModel: JobViewModel
    private val args: BrowseJobsFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jobViewModel = ViewModelProvider(this)[JobViewModel::class.java]

        // Handle category filter from navigation arguments
        args.categoryFilter?.let { category ->
            if (category.isNotEmpty()) {
                jobViewModel.setCategory(category)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    BrowseJobsScreen(
                        viewModel = jobViewModel,
                        onJobClick = { jobId ->
                            val action = BrowseJobsFragmentDirections.actionNavBrowseJobsToJobDetail(jobId)
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }

    // REMOVED: Obsolete onViewCreated logic. Data is now loaded reactively.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
