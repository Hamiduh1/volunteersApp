package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

// Rename class to follow Kotlin conventions (PascalCase)
class FeedbackActivity : ComponentActivity() {

    // The idiomatic Kotlin way to initialize a ViewModel
    private val viewModel: FeedbackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content of the Activity to be a Composable UI
        setContent {
            VolunteersAppTheme {
                FeedbackScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    // All old code (ViewBinding, manual validation, onOptionsItemSelected) is no longer needed.
}
