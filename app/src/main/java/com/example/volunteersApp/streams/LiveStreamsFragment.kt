package com.example.volunteersApp.streams

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

/**
 * Bridge Fragment that hosts the Compose-based Live Streams list UI.
 */
class LiveStreamsFragment : Fragment() {

    private val viewModel: LiveStreamsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                VolunteersAppTheme {
                    LiveStreamsScreen(
                        viewModel = viewModel,
                        onBack = { findNavController().popBackStack() },
                        onStreamClick = { session ->
                            // Handle Clicks: Launch LiveStreamActivity as a viewer
                            val intent = Intent(requireContext(), LiveStreamActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", session.agoraChannelName)
                                putExtra("IS_HOST", false) // User is joining as a viewer
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
