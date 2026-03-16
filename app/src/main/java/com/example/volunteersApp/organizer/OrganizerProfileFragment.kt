package com.example.volunteersApp.organizer

import android.content.Intent
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
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.ui.main.MainActivity
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
                        // Triggers the Logout confirmation dialog defined in the Host Activity
                        onLogout = {
                            (activity as? OrganizerMainActivity)?.promptLogout()
                        },
                        // Handles the complex logic of restarting the app task stack when changing roles
                        onNavigateToRole = { roleTechnicalName ->
                            val intent = when (val role = roleTechnicalName.lowercase()) {
                                "volunteer", "employer" -> Intent(activity, MainActivity::class.java).apply {
                                    // Add extra to specify which NavGraph to start
                                    putExtra("START_ROLE", role)
                                }
                                "organizer" -> Intent(activity, OrganizerMainActivity::class.java)
                                else -> Intent(activity, LoginActivity::class.java)
                            }

                            // Clear the current task stack to ensure the new Activity starts as the root
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)

                            // Close all activities associated with the old role
                            activity?.finishAffinity()
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
