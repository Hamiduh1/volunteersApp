package com.example.volunteersApp.organizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Fragment host for the Organizer Profile.
 * This class handles high-level navigation (role switching) and bridges
 * the Activity-based navigation with the Compose-based UI.
 */
class OrganizerProfileFragment : Fragment() {

    // Initialize the ViewModel scoped to this Fragment's lifecycle
    private val viewModel: OrganizerProfileViewModel by viewModels()

    // Get the shared VertexViewModel instance from the hosting activity
    private val vertexViewModel: VertexViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Best practice for Fragments: Dispose the composition when the fragment's
            // view lifecycle is destroyed to prevent memory leaks.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    OrganizerProfileScreen(
                        viewModel = viewModel,
                        // Pass the shared VertexViewModel to the composable
                        vertexViewModel = vertexViewModel,
                        onLogout = {
                            (activity as? OrganizerMainActivity)?.promptLogout()
                        }
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Factory method to create a new instance of this fragment.
         */
        @JvmStatic
        fun newInstance(): OrganizerProfileFragment = OrganizerProfileFragment()
    }
}
