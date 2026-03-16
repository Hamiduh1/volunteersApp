package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Activity that hosts the Jetpack Compose "How to Use" UI.
 *
 * This class has been fully refactored to delegate all UI rendering and data logic
 * to a Composable (`HowToUseScreen`) and a ViewModel (`HowToUseViewModel`).
 * It no longer uses RecyclerView, ViewBinding, or direct Firestore calls.
 */
class HowToUseActivity : ComponentActivity() {

    // Initialize the ViewModel using the standard Kotlin KTX property delegate.
    private val viewModel: HowToUseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content of the Activity to be a Composable UI.
        // This replaces the old setContentView() and XML layout.
        setContent {
            VolunteersAppTheme {
                // Render the main HowToUseScreen Composable, passing it the
                // ViewModel and a lambda function to handle "Up" navigation.
                HowToUseScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    // All old methods and properties are no longer needed:
    // - No 'binding', 'db', 'adapter', or 'tipList'.
    // - No 'loadTipsFromFirestore()'.
    // - No 'onSupportNavigateUp()'.
    // - No 'onDestroy()' for nulling the binding.
}
