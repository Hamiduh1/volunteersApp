package com.example.volunteersApp.streams

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
        val sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE)?.trim()?.ifBlank { null } ?: "standalone"
        val linkedEventId = intent.getStringExtra(EXTRA_EVENT_ID)?.trim()?.ifBlank { null }
        viewModel.setLaunchContext(sourceType, linkedEventId)

        setContent {
            VolunteersAppTheme {
                // Listen for navigation events from the ViewModel
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is StartStreamEvent.Success -> {
                                event.mindLoomMessage?.let { message ->
                                    Toast.makeText(this@StartStreamActivity, message, Toast.LENGTH_LONG).show()
                                }
                                startActivity(
                                    LiveLaunchIntent.liveRoomIntent(
                                        context = this@StartStreamActivity,
                                        target = LiveLaunchTarget(sessionId = event.sessionId),
                                        isHost = true,
                                    )
                                )
                                finish()
                            }
                        }
                    }
                }

                StartStreamScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onStreamStarted = { },
                    offerShareChooserOnStart = false,
                )
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_TYPE = "extra_live_source_type"
        const val EXTRA_EVENT_ID = "extra_live_event_id"
    }
}
