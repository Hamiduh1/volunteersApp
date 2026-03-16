package com.example.volunteersApp.jokes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class JokesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create a ComposeView to host our Jetpack Compose UI.
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the Fragment's view lifecycle is destroyed.
            // This is a best practice to prevent memory leaks.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Set the content of the view to our composable UI.
            setContent {
                VolunteersAppTheme {
                    // Call the main feature screen. The ViewModel will be created
                    // by default within the composable.
                    JokesFeatureScreen()
                }
            }
        }
    }
}
