package com.example.volunteersApp.employer.ui.profile

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
 * Modernized Employer Profile Fragment.
 * Hosts the Jetpack Compose UI and connects it to the EmployerProfileViewModel.
 */
class EmployerProfileFragment : Fragment() {

    private val viewModel: EmployerProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    EmployerProfileScreen(
                        viewModel = viewModel,
                        onBack = { findNavController().popBackStack() },
                        onProfileSaved = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}
