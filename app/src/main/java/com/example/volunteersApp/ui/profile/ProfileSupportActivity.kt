package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Activity that hosts the horizontal "Profile Support" actions UI.
 * This class is now a lightweight host for the ProfileSupportScreen Composable.
 */
class ProfileSupportActivity : ComponentActivity() {

    // The idiomatic Kotlin way to initialize a ViewModel using the KTX library.
    private val viewModel: ProfileSupportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the standard, clean trailing lambda syntax for setContent.
        setContent {
            VolunteersAppTheme {
                // The ProfileSupportScreen composable is defined in its own file.
                ProfileSupportScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }
    // All old code (init block, complex setContent) is no longer needed.
}
