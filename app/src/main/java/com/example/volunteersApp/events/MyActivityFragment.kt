package com.example.volunteersApp.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Bridge Fragment that hosts the Compose-based MyActivity UI.
 * This class translates XML Navigation actions into Compose callbacks.
 */
class MyActivityFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Cleanup composition when fragment view is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    MyActivityScreen(
                        // FIX: Updated the lambda to accept both parameters, resolving the type mismatch.
                        // The applicationId is not needed for this specific navigation action, so it is ignored.
                        onNavigateToEventDetail = { eventId, _ ->
                            // Use Safe Args from your navigation graph
                            val action = MyActivityFragmentDirections.actionMyActivityFragmentToEventDetailFragment(eventId)
                            findNavController().navigate(action)
                        },
                        onNavigateToJobDetail = { jobId ->
                            val action = MyActivityFragmentDirections.actionMyActivityFragmentToJobDetailFragment(jobId)
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
