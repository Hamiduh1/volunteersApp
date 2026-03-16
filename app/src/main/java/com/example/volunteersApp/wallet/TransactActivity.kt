package com.example.volunteersApp.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                    }
                )
            }
        }
    }
}
