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
import androidx.navigation.fragment.navArgs
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Job Detail Fragment.
 * Hosts the Jetpack Compose UI and connects it to the JobDetailViewModel.
 */
class JobDetailFragment : Fragment() {

    private val viewModel: JobDetailViewModel by viewModels()
    private val args: JobDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                VolunteersAppTheme {
                    JobDetailScreen(
                        jobId = args.jobId,
                        viewModel = viewModel,
                        onBack = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}
