package com.example.volunteersApp.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Community Alerts Fragment.
 * Refactored to host the Jetpack Compose [CommunityAlertsScreen].
 */
class CommunityAlertsFragment : Fragment() {

    // Initialize the ViewModel using the viewModels() delegate
    private val viewModel: CommunityAlertsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                VolunteersAppTheme {
                    CommunityAlertsScreen(
                        viewModel = viewModel,
                        onAddAlertClick = {
                            // TODO: Implement navigation to Create Alert Screen
                            Toast.makeText(requireContext(), "Navigate to Create Alert Screen", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
