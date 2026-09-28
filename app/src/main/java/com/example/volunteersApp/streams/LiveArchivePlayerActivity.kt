package com.example.volunteersApp.streams

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import kotlinx.coroutines.launch

class LiveArchivePlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Live Replay" }
        val shareAccessToken = intent.getStringExtra(EXTRA_REPLAY_SHARE_TOKEN)
            ?.trim()
            ?.ifBlank { null }
        if (sessionId.isBlank()) {
            Toast.makeText(this, "Replay is unavailable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            VolunteersAppTheme {
                LiveArchivePlayerScreen(
                    sessionId = sessionId,
                    title = title,
                    onBack = { finish() },
                    loadReplayUrl = { callback ->
                        lifecycleScope.launch {
                            runCatching {
                                LiveRepository.createLiveReplayAccessLink(
                                    sessionId = sessionId,
                                    shareAccessToken = shareAccessToken,
                                )
                            }.onSuccess(callback).onFailure {
                                Toast.makeText(
                                    this@LiveArchivePlayerActivity,
                                    "We couldn't open this replay. Refresh and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_live_archive_session_id"
        const val EXTRA_TITLE = "extra_live_archive_title"
        const val EXTRA_REPLAY_SHARE_TOKEN = "extra_live_archive_share_token"
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
private fun LiveArchivePlayerScreen(
    sessionId: String,
    title: String,
    onBack: () -> Unit,
    loadReplayUrl: (onReady: (String) -> Unit) -> Unit,
) {
    var playbackUrl by remember(sessionId) { mutableStateOf<String?>(null) }
    var isLoading by remember(sessionId) { mutableStateOf(true) }

    LaunchedEffect(sessionId) {
        isLoading = true
        loadReplayUrl { url ->
            playbackUrl = url
            isLoading = false
        }
    }

    LiveStudioTheme {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Replay", style = MaterialTheme.typography.labelSmall, color = LiveStudioMuted)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.85f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = LiveStudioAccent)
                        Text("Loading replay…", color = Color.White.copy(alpha = 0.85f))
                    }
                    playbackUrl.isNullOrBlank() -> Text(
                        "Replay unavailable",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    else -> LiveArchiveExoPlayer(playbackUrl = playbackUrl!!)
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun LiveArchiveExoPlayer(playbackUrl: String) {
    val context = LocalContext.current
    val player = remember(playbackUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(playbackUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
