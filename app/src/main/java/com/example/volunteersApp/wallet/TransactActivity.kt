package com.example.volunteersApp.wallet

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.volunteersApp.chat.UserDirectoryActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Host Activity for the Send Money (Transact) feature.
 * Migrated fully to Jetpack Compose.
 */
class TransactActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Using setContent to host the Compose UI
        setContent {
            VolunteersAppTheme {
                // Launching the main TransactScreen
                TransactScreen(
                    onBack = {
                        // Standard Activity back behavior
                        finish()
                    },
                    onNavigateToPayments = {
                        startActivity(Intent(this, PaymentsActivity::class.java))
                    },
                    onNavigateToUserDirectory = {
                        startActivity(Intent(this, UserDirectoryActivity::class.java))
                    },
                    onNavigateToTransactionHistory = {
                        startActivity(Intent(this, TransactionActivity::class.java))
                    }
                )
            }
        }
    }
}
