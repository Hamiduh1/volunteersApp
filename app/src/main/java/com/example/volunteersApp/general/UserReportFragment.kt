package com.example.volunteersApp.general // Or .ui.reports depending on your package structure

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
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.example.volunteersApp.models.User


class UserReportFragment : Fragment() {

    private val viewModel: UserReportViewModel by viewModels()

    // Assuming you use SafeArgs to pass the user to be reported
    // If not using SafeArgs, you can use arguments?.getParcelable("user")
    // private val args: UserReportFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    // Logic to get data from arguments
                    val reportedUser = arguments?.getSerializable("reportedUser") as? User ?: User()
                    val eventTitle = arguments?.getString("eventTitle") ?: "Unknown Event"

                    UserReportScreen(
                        reportedUser = reportedUser,
                        eventTitle = eventTitle,
                        viewModel = viewModel,
                        onNavigateBack = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}
