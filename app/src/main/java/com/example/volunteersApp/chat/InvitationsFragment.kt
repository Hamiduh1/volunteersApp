package com.example.volunteersApp.chat
//a simple host for our new InvitationsScreen composable.
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class InvitationsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    // We pass the NavController so the Composable can trigger navigation
                    InvitationsScreen(navController = findNavController())
                }
            }
        }
    }
}
