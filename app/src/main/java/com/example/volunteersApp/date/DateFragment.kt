package com.example.volunteersApp.date

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

class DateFragment : Fragment() {

    private val viewModel: DateEvaViewModel by viewModels()
    // FIX 1: Initialize the shared VertexViewModel using the activityViewModels delegate.
    private val vertexViewModel: VertexViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    // FIX 2: Pass the initialized vertexViewModel to the composable screen.
                    DateEvaScreen(
                        viewModel = viewModel,
                        vertexViewModel = vertexViewModel
                    )
                }
            }
        }
    }
}
