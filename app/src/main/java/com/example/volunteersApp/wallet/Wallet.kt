package com.example.volunteersApp.wallet

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
 * Fragment host that renders the [WalletScreen] composable.
 * Fixed: Added missing navigation callbacks required by the modern WalletScreen.
 */
class Wallet : Fragment() {

    private val viewModel: WalletViewModel by viewModels()
    private val paymentsViewModel: PaymentsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                VolunteersAppTheme {
                    WalletScreen(
                        viewModel = viewModel,
                        paymentsViewModel = paymentsViewModel,
                        onBack = { findNavController().popBackStack() },
                        // If using Fragments, these should point to your nav_graph IDs
                        onNavigateToTransact = {
                            // findNavController().navigate(R.id.transactFragment)
                        },
                        onNavigateToHistory = {
                            // findNavController().navigate(R.id.historyFragment)
                        },
                        onNavigateToPayments = {
                            // findNavController().navigate(R.id.paymentsFragment)
                        }
                    )
                }
            }
        }
    }
}
