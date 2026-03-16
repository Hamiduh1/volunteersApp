package com.example.volunteersApp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class NotificationSettingsFragment : Fragment() {

    private val viewModel: NotificationSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // We no longer need the old XML layout or ViewBinding
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the Fragment's View is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Set the Composable content
            setContent {
                VolunteersAppTheme {
                    NotificationSettingsScreen(
                        navController = findNavController(),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
