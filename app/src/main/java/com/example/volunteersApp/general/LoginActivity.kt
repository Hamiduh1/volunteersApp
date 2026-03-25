package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.volunteersApp.streams.LiveStreamActivity
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized LoginActivity.
 * Hosts the [LoginScreen] and handles App Link redirects and navigation routing.
 */
class LoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EMAIL_VERIFIED_SUCCESS = "extra_email_verified_success"
    }

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle App Links (e.g. from stream invitations)
        if (handleAppLink(intent)) return

        if (intent.getBooleanExtra(SignUpActivity.EXTRA_SHOW_VERIFY_HINT, false)) {
            Toast.makeText(
                this,
                "Verify your email first. Open the latest email and enter the 6-digit code (check Spam).",
                Toast.LENGTH_LONG
            ).show()
        }
        if (intent.getBooleanExtra(EXTRA_EMAIL_VERIFIED_SUCCESS, false)) {
            Toast.makeText(
                this,
                "Email verified successfully. Please log in.",
                Toast.LENGTH_LONG
            ).show()
        }
        val openLoginFormImmediately =
            intent.getBooleanExtra(SignUpActivity.EXTRA_SHOW_VERIFY_HINT, false) ||
                intent.getBooleanExtra(EXTRA_EMAIL_VERIFIED_SUCCESS, false)

        // Auto-login check if not handling an app link
        viewModel.checkAutoLogin()

        setContent {
            VolunteersAppTheme {
                var showLoginForm by rememberSaveable { mutableStateOf(openLoginFormImmediately) }
                if (showLoginForm) {
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
                        },
                        onVerifyEmail = {
                            startActivity(Intent(this, EmailVerificationActivity::class.java))
                        }
                    )
                } else {
                    AuthLaunchScreen(
                        onSignIn = { showLoginForm = true },
                        onCreateAccount = { startActivity(Intent(this, SignUpActivity::class.java)) },
                        onVerifyEmail = { startActivity(Intent(this, EmailVerificationActivity::class.java)) },
                        onForgotPassword = { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
                    )
                }
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
