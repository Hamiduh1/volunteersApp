package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
// This import is no longer needed as the class is in the same package now
// import com.example.volunteersApp.ui.profile.SupportActivity
import com.example.volunteersApp.ui.profile.SupportViewModel
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Activity that hosts the Jetpack Compose Support UI.
 * This class has been refactored to delegate all UI rendering and data logic
 * to a Composable (SupportScreen) and a ViewModel (SupportViewModel).
 * It is now a simple, lightweight entry point.
 */
class SupportActivity : ComponentActivity() {

    // Initialize the ViewModel using the KTX property delegate.
    // The ViewModel will be automatically created and scoped to
    // this Activity's lifecycle.

    private val supportViewModel: SupportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The 'setContent' block replaces 'setContentView(R.layout.activity_support)'.
        // It defines the entire UI of the activity using Composable functions.
        setContent {
            // Apply the application's M3 theme.
            VolunteersAppTheme {
                // Render the main SupportScreen composable.
                // We pass it a lambda to handle the "Up" navigation (closing the activity)
                // and provide the ViewModel instance it needs to fetch and display data.
                SupportScreen(
                    onNavigateUp = { finish() },
                    viewModel = supportViewModel
                )
            }
        }
    }
    // All other methods from the old Activity are no longer needed:
    // - No RecyclerView, ProgressBar, or Adapter properties.
    // - No findViewById calls.
    // - No loadItemsFromFirestore() method (logic is now in the ViewModel).
    // - No onOptionsItemSelected() (handled by the Composable's TopAppBar).
}

// All other methods from the old Activity are no longer needed:
    // - No RecyclerView, ProgressBar, or Adapter properties.
    // - No findViewById calls.
    // - No loadItemsFromFirestore() method (logic is now in the ViewModel).
    // - No onOptionsItemSelected() (handled by the Composable's TopAppBar).

