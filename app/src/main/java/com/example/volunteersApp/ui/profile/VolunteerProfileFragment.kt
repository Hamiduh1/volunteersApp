package com.example.volunteersApp.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Correct import
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class VolunteerProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    // FIX: Correctly get the shared VertexViewModel instance scoped to the activity.
    private val vertexViewModel: VertexViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View
    {
       //   observeLogoutEvent()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    val navController = findNavController()
                    ProfileScreen(
                        // FIX: Pass the actual ViewModel instance, not the class name.
                        vertexViewModel = vertexViewModel,
                        navController = navController,
                        viewModel = viewModel,
                        onLogoutRequested = { viewModel.logout() },
                    )
                }
            }
        }
    }
    // ... (rest of the file is unchanged)
}