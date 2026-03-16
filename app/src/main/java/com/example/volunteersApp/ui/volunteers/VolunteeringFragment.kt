package com.example.volunteersApp.ui.volunteers

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

class VolunteeringFragment : Fragment() {
    private val viewModel: VolunteeringViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        arguments?.getString("category_filter")?.let {
            viewModel.setCategoryFilter(it)
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    VolunteeringScreen(
                        title = "Volunteering Opportunities",
                        viewModel = viewModel,
                        onEventClick = { eventId ->
                             val action = VolunteeringFragmentDirections.actionNavBrowseEventsToEventDetail(eventId)
                             findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
