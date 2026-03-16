package com.example.volunteersApp.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modern entry point for History.
 * This Activity now calls the modernized TransactionHistoryScreen
 * and uses the correct TransactionHistoryViewModel.
 */
class TransactionActivity : ComponentActivity() {

    // Updated to use the correct ViewModel we built earlier
    private val viewModel: TransactionHistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolunteersAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Call the modernized screen we finalized
                    TransactionHistoryScreen(
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
