package com.example.volunteersApp.events

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
import com.example.volunteersApp.host.EventDetailScreen
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A Fragment bridge that hosts the Jetpack Compose [com.example.volunteersApp.host.EventDetailScreen].
 * This allows the app to use modern Compose UI while still participating
 * in the XML-based Navigation Graph (Safe Args).
 */
class EventDetailFragment : Fragment() {

    // 1. Get arguments passed via Safe Args from the Navigation Graph
    private val args: EventDetailFragmentArgs by navArgs()

    // 2. Initialize the ViewModel scoped to this Fragment's lifecycle
    private val viewModel: EventDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // 3. Set the strategy to dispose of the composition when the fragment's view is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    // 4. Call the modern Compose Screen
                    EventDetailScreen(
                        eventId = args.eventId,
                        viewModel = viewModel,
                        onBack = {
                            // Use Fragment's findNavController to navigate back
                            findNavController().popBackStack()
                        },
                        onViewApplicants = { eventId ->
                            // Optional: Navigate to the organizer's applicant list if needed
                            // val action = EventDetailFragmentDirections.actionEventDetailToApplicants(eventId)
                            // findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
