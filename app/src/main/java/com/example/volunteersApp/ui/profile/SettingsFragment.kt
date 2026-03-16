package com.example.volunteersApp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Fragment that acts as a host for the Jetpack Compose Settings UI.
 * This class has been refactored to delegate all UI rendering and event handling
 * to the SettingsScreen composable, significantly simplifying its structure.
 */
class SettingsFragment : Fragment() {

    // All old properties like 'binding' and 'navController' are no longer needed here.

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Instead of inflating an XML layout, we programmatically create a ComposeView.
        return ComposeView(requireContext()).apply {
            // Set the strategy for managing the composition's lifecycle to prevent memory leaks.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // This is where we set the Composable content for our screen.
            setContent {
                // Apply your app's theme.
                VolunteersAppTheme {
                    // Render the SettingsScreen composable.
                    // We find the NavController directly within the composable context
                    // and pass it down for navigation actions.
                    SettingsScreen(navController = findNavController())
                }
            }
        }
    }

    // With Compose, we no longer need onViewCreated, setupToolbar,
    // setupClickListeners, or onDestroyView to null out the binding.
    // The Fragment is now just a simple container.
}
