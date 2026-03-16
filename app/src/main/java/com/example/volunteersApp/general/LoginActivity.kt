package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.streams.LiveStreamActivity
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized LoginActivity.
 * Hosts the [LoginScreen] and handles App Link redirects and navigation routing.
 */
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle App Links (e.g. from stream invitations)
        if (handleAppLink(intent)) return

        // Auto-login check if not handling an app link
        viewModel.checkAutoLogin()

        setContent {
            VolunteersAppTheme {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { userType ->
                        navigateToDashboard(userType)
                    },
                    onForgotPassword = {
                        startActivity(Intent(this, ForgotPasswordActivity::class.java))
                    },
                    onSignUp = {
                        startActivity(Intent(this, SignUpActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAppLink(intent)
    }

    /**
     * Checks if the app was launched via an Agora stream link.
     */
    private fun handleAppLink(intent: Intent): Boolean {
        val action = intent.action
        val data = intent.data
        if (Intent.ACTION_VIEW == action && data != null) {
            val path = data.path
            if (path != null && path.startsWith("/stream/")) {
                val sessionId = data.lastPathSegment
                if (!sessionId.isNullOrEmpty()) {
                    if (viewModel.isUserLoggedIn()) {
                        redirectToStream(sessionId)
                    } else {
                        Toast.makeText(this, "Please log in to join the stream.", Toast.LENGTH_LONG).show()
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun redirectToStream(sessionId: String) {
        val intent = Intent(this, LiveStreamActivity::class.java).apply {
            putExtra("CHANNEL_NAME", sessionId)
            putExtra("IS_HOST", false)
        }
        startActivity(intent)
    }

    private fun navigateToDashboard(userType: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_TYPE", userType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
