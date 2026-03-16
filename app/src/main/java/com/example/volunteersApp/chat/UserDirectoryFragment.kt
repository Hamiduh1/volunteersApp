package com.example.volunteersApp.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * This Fragment now acts as a simple host for the Jetpack Compose UI.
 * Its only job is to set up the Compose content. The ViewModel handles all the logic.
 */
class UserDirectoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the Fragment's view is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Set the content of the view to our new composable screen.
            // The viewModel() delegate will automatically create and scope the ViewModel.
            setContent {
                VolunteersAppTheme {
                    UserDirectoryScreen()
                }
            }
        }
    }
}

