package com.example.volunteersApp.general

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.volunteersApp.streams.LiveLaunchIntent
import com.example.volunteersApp.streams.LiveLaunchTarget
import com.example.volunteersApp.streams.LiveShareRouter
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import kotlinx.coroutines.launch

/**
 * Modernized LoginActivity.
 * Hosts the [LoginScreen] and handles App Link redirects and navigation routing.
 */
class LoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EMAIL_VERIFIED_SUCCESS = "extra_email_verified_success"
        private const val EXTRA_PENDING_STREAM_SESSION_ID = "extra_pending_stream_session_id"
        private const val EXTRA_PENDING_LIVE_HOST_ID = "extra_pending_live_host_id"
        private const val EXTRA_PENDING_LIVE_SHARE_TOKEN = "extra_pending_live_share_token"
    }

    private val viewModel: LoginViewModel by viewModels()
    private var pendingStreamSessionId: String? = null
    private var pendingLiveHostId: String? = null
    private var pendingLiveShareToken: String? = null

    private var routingLiveLink = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingStreamSessionId = savedInstanceState?.getString(EXTRA_PENDING_STREAM_SESSION_ID)
        pendingLiveHostId = savedInstanceState?.getString(EXTRA_PENDING_LIVE_HOST_ID)
        pendingLiveShareToken = savedInstanceState?.getString(EXTRA_PENDING_LIVE_SHARE_TOKEN)

        // Handle App Links (e.g. from stream invitations)
        handleAppLink(intent)

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
                val uiState by viewModel.uiState.collectAsState()
                var showLoginForm by rememberSaveable { mutableStateOf(openLoginFormImmediately) }
                var hasNavigated by rememberSaveable { mutableStateOf(false) }
                var hasOpenedVerification by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(uiState.loginSuccess) {
                    if (uiState.loginSuccess && !hasNavigated) {
                        hasNavigated = true
                        navigateAfterLogin()
                    }
                }
                LaunchedEffect(uiState.emailVerificationRequired) {
                    if (uiState.emailVerificationRequired && !hasOpenedVerification) {
                        hasOpenedVerification = true
                        startActivity(Intent(this@LoginActivity, EmailVerificationActivity::class.java))
                    }
                }

                if (showLoginForm) {
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = {
                            if (!hasNavigated) {
                                hasNavigated = true
                                navigateAfterLogin()
                            }
                        },
                        onForgotPassword = {
                            startActivity(Intent(this, ForgotPasswordActivity::class.java))
                        },
                        onSignUp = {
                            startActivity(Intent(this, SignUpActivity::class.java))
                        }
                    )
                } else {
                    AuthLaunchScreen(
                        onSignIn = { showLoginForm = true },
                        onCreateAccount = { startActivity(Intent(this, SignUpActivity::class.java)) },
                        onVerifyEmail = { showLoginForm = true },
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingStreamSessionId?.let { outState.putString(EXTRA_PENDING_STREAM_SESSION_ID, it) }
        pendingLiveHostId?.let { outState.putString(EXTRA_PENDING_LIVE_HOST_ID, it) }
        pendingLiveShareToken?.let { outState.putString(EXTRA_PENDING_LIVE_SHARE_TOKEN, it) }
    }

    /**
     * Checks if the app was launched via an Agora stream link.
     */
    private fun handleAppLink(intent: Intent): Boolean {
        // Restore pending session id (e.g. process recreation)
        pendingStreamSessionId = pendingStreamSessionId
            ?: intent.getStringExtra(EXTRA_PENDING_STREAM_SESSION_ID)
            ?: intent.getStringExtra(LiveLaunchIntent.EXTRA_LIVE_SESSION_ID)
        pendingLiveHostId = pendingLiveHostId
            ?: intent.getStringExtra(EXTRA_PENDING_LIVE_HOST_ID)
            ?: intent.getStringExtra(LiveLaunchIntent.EXTRA_LIVE_HOST_ID)
        pendingLiveShareToken = pendingLiveShareToken
            ?: intent.getStringExtra(EXTRA_PENDING_LIVE_SHARE_TOKEN)
            ?: intent.getStringExtra(LiveLaunchIntent.EXTRA_LIVE_SHARE_TOKEN)

        val target = when {
            Intent.ACTION_VIEW == intent.action && intent.data != null -> LiveLaunchIntent.parse(intent.data!!)
            else -> LiveLaunchIntent.parse(intent)
        }
        if (target != null) {
            if (viewModel.isUserLoggedIn()) {
                redirectToStream(target)
            } else {
                pendingStreamSessionId = target.sessionId
                pendingLiveHostId = target.hostId
                pendingLiveShareToken = target.shareAccessToken
                Toast.makeText(this, "Please log in to join the stream.", Toast.LENGTH_LONG).show()
            }
            return true
        }
        return false
    }

    // Finishing before LiveShareRouter resumes would cancel lifecycleScope and drop the link.
    private fun redirectToStream(target: LiveLaunchTarget) {
        if (routingLiveLink) return
        routingLiveLink = true
        lifecycleScope.launch {
            runCatching {
                LiveShareRouter.launch(this@LoginActivity, target)
            }.onFailure { error ->
                Toast.makeText(
                    this@LoginActivity,
                    error.localizedMessage ?: "Could not open live stream.",
                    Toast.LENGTH_LONG
                ).show()
                if (viewModel.isUserLoggedIn()) {
                    startActivity(
                        Intent(this@LoginActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
            }
            finish()
        }
    }

    private fun navigateAfterLogin() {
        // A live link is already being opened; MainActivity's CLEAR_TASK would close the live room.
        if (routingLiveLink) return
        val pendingSessionId = pendingStreamSessionId ?: intent.getStringExtra(EXTRA_PENDING_STREAM_SESSION_ID)
            ?: intent.getStringExtra(LiveLaunchIntent.EXTRA_LIVE_SESSION_ID)

        if (!pendingSessionId.isNullOrBlank()) {
            val target = LiveLaunchTarget(
                sessionId = pendingSessionId,
                hostId = pendingLiveHostId ?: intent.getStringExtra(LiveLaunchIntent.EXTRA_LIVE_HOST_ID),
                shareAccessToken = pendingLiveShareToken ?: intent.getStringExtra(LiveLaunchIntent.EXTRA_LIVE_SHARE_TOKEN),
            )
            pendingStreamSessionId = null
            pendingLiveHostId = null
            pendingLiveShareToken = null
            redirectToStream(target)
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
