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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.example.volunteersApp.wallet.WalletViewModel

class DateFragment : Fragment() {

    private val walletViewModel: WalletViewModel by activityViewModels()
    private val viewModel: DateEvaViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DateEvaViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return DateEvaViewModel(walletViewModel) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    private val vertexViewModel: VertexViewModel by activityViewModels()
    private val blindDateViewModel: BlindDateViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    DateEvaScreen(
                        viewModel = viewModel,
                        blindDateViewModel = blindDateViewModel,
                        vertexViewModel = vertexViewModel,
                        mainViewModel = null,
                        onOpenInbox = null
                    )
                }
            }
        }
    }
}
