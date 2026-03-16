package com.example.volunteersApp.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.R

class MyEventsFragment : Fragment() {

    // Initialize the ViewModel
    private val myEventsViewModel: MyEventsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                MaterialTheme {
                    // Call the screen defined in MyEventsScreen.kt
                    MyEventsScreen(
                        viewModel = myEventsViewModel,
                        // FIX: Updated lambda signature to accept both parameters, resolving the error.
                        onEventClick = { eventId, _ ->
                            val bundle = Bundle().apply { putString("eventId", eventId) }
                            findNavController().navigate(R.id.eventDetailFragment, bundle)
                        }
                    )
                }
            }
        }
    }
}
