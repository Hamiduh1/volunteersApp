package com.example.volunteersApp.streams

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Toast
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Full-screen live room host activity (iOS parity — not a bottom sheet).
 */
class LiveStreamActivity : ComponentActivity() {

    private val viewModel: LiveStreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val target = LiveLaunchIntent.parse(intent)
        val sessionId = target?.sessionId ?: intent.getStringExtra("CHANNEL_NAME").orEmpty()
        val shareAccessToken = target?.shareAccessToken

        if (sessionId.isBlank()) {
            Toast.makeText(this, "Live session could not be opened.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.joinStream(sessionId, this, shareAccessToken)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Host exit ends the broadcast so the session is not left LIVE without a host.
                if (viewModel.uiState.value.isHost && !viewModel.uiState.value.sessionEnded) {
                    viewModel.endStream(onComplete = { finish() })
                } else {
                    viewModel.leaveStream()
                    finish()
                }
            }
        })

        setContent {
            VolunteersAppTheme {
                LiveStreamScreen(
                    viewModel = viewModel,
                    onLeave = {
                        viewModel.leaveStream()
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Recreation (dark mode, split-screen resize) keeps the ViewModel; dropping the channel there
        // would cut the broadcast. onCleared() still cleans up when the room really closes.
        if (isFinishing) {
            viewModel.leaveStream()
        }
    }
}
