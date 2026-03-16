package com.example.volunteersApp.ui.volunteers

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modern Activity that hosts the Jetpack Compose "My Applications" UI.
 * Connects the volunteer's view to their specific application details.
 */
class MyApplicationsActivity : ComponentActivity() {

    // Using Factory to inject Repository into the ViewModel
    // FIXED: Now uses the correctly defined Factory and Repository package
    private val viewModel: MyApplicationsViewModel by viewModels {
        MyApplicationsViewModelFactory(ApplicationRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VolunteersAppTheme {
                MyApplicationsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onItemClick = { app ->
                        // Navigate to ApplicationDetailActivity to see status and cover letter
                        val intent = Intent(this, ApplicationDetailActivity::class.java).apply {
                            putExtra("APPLICATION_ID", app.applicationId)
                            putExtra("EVENT_ID", app.eventId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
