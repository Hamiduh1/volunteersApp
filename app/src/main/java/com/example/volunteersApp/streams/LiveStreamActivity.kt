package com.example.volunteersApp.streams

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Live Stream Activity.
 * Hosts the Jetpack Compose UI and connects it to the LiveStreamViewModel.
 */
class LiveStreamActivity : ComponentActivity() {

    private val viewModel: LiveStreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        val isHost = intent.getBooleanExtra("IS_HOST", false)

        // Initialize SDKs and join channels
        viewModel.joinStream( channelName, this )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.leaveStream()
                finish()
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
        // Cleanup is handled by the onBackPressed callback or explicit leaveStream
        viewModel.leaveStream()
    }
}
