package com.example.volunteersApp.marketplace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Fragment container for the Marketplace feature.
 * Acts as the bridge between the Android Navigation component and Jetpack Compose.
 */
class MarketplaceFragment : Fragment() {

    private val viewModel: MarketplaceViewModel by viewModels()
    // Get the shared VertexViewModel instance scoped to the activity
    private val vertexViewModel: VertexViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    // Host the modern MarketplaceScreen and pass the NavController and VertexViewModel
                    MarketplaceScreen(
                        viewModel = viewModel,
                        vertexViewModel = vertexViewModel,
                        navController = findNavController()
                    )
                }
            }
        }
    }
}
