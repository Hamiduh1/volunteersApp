package com.example.volunteersApp.employer.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Activity for managing the Employer/Organization profile.
 * Refactored to Jetpack Compose, hosting the EmployerProfileScreen.
 */
class ManageProfileActivity : ComponentActivity() {

    private val viewModel: EmployerProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolunteersAppTheme {
                EmployerProfileScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
