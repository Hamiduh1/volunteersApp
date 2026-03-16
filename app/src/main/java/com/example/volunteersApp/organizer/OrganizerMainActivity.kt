package com.example.volunteersApp.organizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.R
import com.example.volunteersApp.ui.main.MainViewModel
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Organizer Main Activity.
 * Hosts the Jetpack Compose based Organizer Navigation Hub.
 */
class OrganizerMainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolunteersAppTheme {
                val uiState by viewModel.uiState.collectAsState()

                if (!uiState.isLoading && !uiState.isLoggedIn) {
                    redirectToLogin()
                } else {
                    OrganizerMainScreen(
                        uiState = uiState,
                        onSignOut = { promptLogout() }
                    )
                }
            }
        }
    }

    /**
     * Shows a confirmation dialog before logging out.
     */
    fun promptLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirmation_title)
            .setMessage(R.string.logout_confirmation_message)
            .setPositiveButton(R.string.logout_confirm) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performLogout() {
        viewModel.signOut()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
