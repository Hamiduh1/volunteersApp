package com.example.volunteersApp.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modern, Compose-based Activity for submitting user/event reports.
 * This class now acts as a lightweight host for the ReportScreen composable.
 */
class Report : ComponentActivity() {

    private val viewModel: ReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolunteersAppTheme {
                ReportScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }
}
