package com.example.volunteersApp.ui.volunteers
// This uses ApplicationViewModel can handle the "Organizer's View"
// where it fetches all applications across multiple events.
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class ViewApplicationsActivity : ComponentActivity() {

    // Initialize with Factory since Repository is required
    private val viewModel: ApplicationViewModel by viewModels {
        ApplicationViewModelFactory(ApplicationRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolunteersAppTheme {
                ViewApplicationsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onItemClick = { app ->
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
