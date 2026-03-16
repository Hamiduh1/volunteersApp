package com.example.volunteersApp.streams

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Modernized Setup screen for hosts to enter stream details.
 * Refactored to Jetpack Compose, hosting the [StartStreamScreen].
 */
class StartStreamActivity : ComponentActivity() {

    private val viewModel: StartStreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VolunteersAppTheme {
                // Listen for navigation events from the ViewModel
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is StartStreamEvent.Success -> {
                                val intent = Intent(this@StartStreamActivity, LiveStreamActivity::class.java).apply {
                                    putExtra("CHANNEL_NAME", event.sessionId)
                                    putExtra("IS_HOST", true)
                                    putExtra("SHARE_LINK", event.shareLink)
                                }
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                }

                StartStreamScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onStreamStarted = {
                        // This is intentionally left blank because navigation is handled
                        // by a LaunchedEffect within this activity, which observes the
                        // same view model event. This avoids duplicate navigation logic.
                    }
                )
            }
        }
    }
}
