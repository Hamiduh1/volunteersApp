package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Activity that hosts the Jetpack Compose Terms & Conditions UI.
 * It has been refactored to delegate all UI and data logic to a Composable
 * (TermsConditionsScreen) and a ViewModel (TermsViewModel).

 */
class TermsConditionsActivity : ComponentActivity() {

    // Initialize the ViewModel using the KTX property delegate.
    private val viewModel: TermsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The 'setContent' block replaces the old XML layout inflation.
        setContent {
            VolunteersAppTheme {
                // Render the main Composable, passing the ViewModel and a navigation lambda.
                TermsConditionsScreen(
                    onNavigateUp = { finish() }, // The 'Up' button will close the activity
                    viewModel = viewModel
                )
            }
        }
    }

    // All old methods and properties (findViewById, progress bars, Firestore logic)
    // are no longer needed in the Activity.
}
