package com.example.volunteersApp.streams

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.Timestamp
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

/**
 * Modernized Live Stream Screen using Jetpack Compose and Material 3.
 * Features immersive video rendering, transparent chat overlay, and dynamic host controls.
 */
@Composable
fun LiveStreamScreen(
    viewModel: LiveStreamViewModel,
    onLeave: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session

    // Use a SideEffect to leave the stream if the session ends or is invalid
    SideEffect {
        if (!uiState.isLoading && session == null) {
            onLeave()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { padding -> // It's good practice to use the padding from Scaffold
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (session != null) {
                // --- 1. Immersive Video Layer ---
                VideoRenderer(
                    engine = viewModel.getEngine(),
                    isHost = uiState.isHost,
                    remoteUid = uiState.remoteUid
                )

                // --- 2. Top Information Bar ---
                StreamHeader(
                    session = session,
                    onLeave = {
                        if (uiState.isHost) viewModel.endStream() else viewModel.leaveStream()
                    }
                )

                // --- 3. Chat & Controls Layer ---
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ChatOverlay(
                            messages = viewModel.messages,
                            onSendMessage = { viewModel.sendMessage(it) },
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.isHost) {
                            Spacer(Modifier.width(16.dp))
                            HostControlPanel(
                                isAudioMuted = uiState.isAudioMuted,
                                isVideoMuted = uiState.isVideoMuted,
                                onToggleAudio = viewModel::toggleAudio,
                                onToggleVideo = viewModel::toggleVideo,
                                onSwitchCamera = viewModel::switchCamera
                            )
                        }
                    }
                }
            }

            // --- 4. Loading or Error Overlay ---
            if (uiState.isLoading) {
                LoadingOverlay("Connecting to stream...")
            } else if (uiState.error != null) {
                ErrorOverlay(uiState.error!!, onLeave)
            }
        }
    }
}

@Composable
private fun VideoRenderer(engine: RtcEngine?, isHost: Boolean, remoteUid: Int?) {
    val context = LocalContext.current

    // Keep the view alive across recompositions
    val surfaceView = remember {
        SurfaceView(context).apply { setZOrderMediaOverlay(true) }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            engine?.let {
                if (isHost) {
                    it.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                } else if (remoteUid != null) {
                    it.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid))
                } else {
                    // Clear the surface view if no host/remote is present by passing a null view
                    it.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                }
            }
        }
    )
}

@Composable
private fun StreamHeader(session: LiveSession, onLeave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stream Info Badge
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "LIVE | ${session.hostName}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Close Button
        IconButton(
            onClick = onLeave,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Close, "Leave Stream", tint = Color.White)
        }
    }
}

@Composable
private fun ChatOverlay(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true // New messages appear from the bottom
        ) {
            items(messages.reversed()) { msg ->
                ChatItem(msg)
            }
        }

        Spacer(Modifier.height(12.dp))

        ChatInputField(
            value = chatInput,
            onValueChange = { chatInput = it },
            onSend = {
                if (chatInput.isNotBlank()) {
                    onSendMessage(chatInput)
                    chatInput = ""
                }
            }
        )
    }
}

@Composable
private fun ChatItem(msg: ChatMessage) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                // IMPROVEMENT: Use the author's name for a better UX
                text = "${msg.authorName}: ",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = msg.message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Send a message...", color = Color.LightGray) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(Icons.Default.Send, null, tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray)
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Black.copy(alpha = 0.5f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        shape = CircleShape,
        singleLine = true
    )
}

@Composable
private fun HostControlPanel(
    isAudioMuted: Boolean,
    isVideoMuted: Boolean,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StreamControlButton(
            icon = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
            onClick = onToggleAudio,
            active = !isAudioMuted
        )
        StreamControlButton(
            icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
            onClick = onToggleVideo,
            active = !isVideoMuted
        )
        StreamControlButton(
            icon = Icons.Default.FlipCameraAndroid,
            onClick = onSwitchCamera
        )
    }
}

// --- ADDED: Missing composable function from original file ---
@Composable
private fun StreamControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = true
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            contentColor = Color.White
        )
    ) {
        Icon(icon, null)
    }
}

// --- ADDED: Missing composable function from original file ---
@Composable
private fun LoadingOverlay(text: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color.White)
        }
    }
}

// --- ADDED: Missing composable function from original file ---
@Composable
private fun ErrorOverlay(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream Error") },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

// --- ADDED: Missing data classes for completeness ---


