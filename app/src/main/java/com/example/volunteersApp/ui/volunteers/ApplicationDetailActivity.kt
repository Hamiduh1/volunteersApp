
package com.example.volunteersApp.ui.volunteers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * A modern Activity that hosts the Jetpack Compose Application Detail UI.
 * Corrected to retrieve standard IDs and pass them to the Compose screen.
 */
class ApplicationDetailActivity : ComponentActivity() {

    // Using Factory to provide required Repository
    private val viewModel: ApplicationDetailViewModel by viewModels {
        ApplicationDetailViewModelFactory(ApplicationRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Extract all required IDs passed from the calling screen
        val applicationId = intent.getStringExtra("APPLICATION_ID") ?: ""
        val eventId = intent.getStringExtra("EVENT_ID") ?: ""
        // FIX: The screen now requires the volunteer's ID as well.
        val volunteerId = intent.getStringExtra("VOLUNTEER_ID") ?: ""

        // 2. Set the Compose Content
        setContent {
            VolunteersAppTheme {
                ApplicationDetailScreen(
                    eventId = eventId,
                    volunteerId = volunteerId,
                    applicationId = applicationId,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
