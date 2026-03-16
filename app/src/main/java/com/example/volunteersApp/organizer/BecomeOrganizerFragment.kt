package com.example.volunteersApp.organizer

import android.content.Intent
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

class BecomeOrganizerFragment : Fragment() {

    private val viewModel: BecomeOrganizerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    BecomeOrganizerScreen(
                        viewModel = viewModel,
                        onBack = { findNavController().popBackStack() },
                        onSuccess = {
                            // Redirect to Organizer Flow
                            val intent = Intent(activity, OrganizerMainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            activity?.finishAffinity()
                        }
                    )
                }
            }
        }
    }
}
