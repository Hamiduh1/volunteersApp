package com.example.volunteersApp.general

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Privacy & Security Activity.
 * Hosts the [PrivacySecurityScreen] and manages the lifecycle of the settings data.
 */
class PrivacySecurityActivity : ComponentActivity() {

    private val viewModel: PrivacySecurityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolunteersAppTheme {
                PrivacySecurityScreen(
                    viewModel = viewModel,
                    onBack = {
                        onBackPressedDispatcher.onBackPressed()
                    }
                )
            }
        }
    }
}
