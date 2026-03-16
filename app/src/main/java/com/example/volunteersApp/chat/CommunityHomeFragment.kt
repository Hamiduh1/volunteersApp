package com.example.volunteersApp.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
// *** FIX 1: ADD THE MISSING IMPORT ***
import com.example.volunteersApp.chat.CommunityHomeScreen
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * This Fragment is the entry point for the "Community" section.
 * It hosts the CommunityHomeScreen composable, which displays all available features.
 */
class CommunityHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the Fragment's view is destroyed to prevent memory leaks
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Set the content of the view to our new composable screen
            setContent {
                VolunteersAppTheme {
                    // The CommunityHomeScreen handles the UI, and we provide the navigation logic.
                    CommunityHomeScreen(
                        // *** FIX 2: EXPLICITLY DEFINE THE TYPE FOR THE LAMBDA PARAMETER ***
                        onFeatureClick = { navigationAction: Int ->
                            // Use the provided navigation action ID to navigate to the correct destination.
                            findNavController().navigate(navigationAction)
                        }
                    )
                }
            }
        }
    }
}
