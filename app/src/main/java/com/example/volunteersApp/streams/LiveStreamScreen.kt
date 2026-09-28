@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.streams

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
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
    val context = LocalContext.current
    var showLiveComments by remember { mutableStateOf(false) }
    var showHostTools by remember { mutableStateOf(false) }
    var showEndStreamConfirm by remember { mutableStateOf(false) }
    val broadcastPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onBroadcastPermissionsResult(context)
    }

    LaunchedEffect(uiState.needsBroadcastPermissions) {
        if (uiState.needsBroadcastPermissions) {
            broadcastPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    SideEffect {
        if (!uiState.isLoading && session == null && uiState.error.isNullOrBlank()) {
            onLeave()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(uiState.shareError) {
        uiState.shareError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearShareError()
        }
    }

    fun leaveOrConfirmEnd() {
        if (uiState.isHost && !uiState.sessionEnded) {
            showEndStreamConfirm = true
        } else {
            viewModel.leaveStream()
            onLeave()
        }
    }

    LiveStudioTheme {
    if (showEndStreamConfirm) {
        AlertDialog(
            onDismissRequest = { showEndStreamConfirm = false },
            title = { Text("End live stream?") },
            text = {
                Text("Ending stops the broadcast for everyone watching. You can leave without ending only from Leave if you choose Cancel.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndStreamConfirm = false
                        viewModel.endStream(onComplete = onLeave)
                    }
                ) {
                    Text("End stream")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndStreamConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(LiveStudioBackground)) {
            if (session != null) {
                VideoRenderer(
                    engine = viewModel.getEngine(),
                    publishLocally = uiState.isOnStage,
                    remoteUid = uiState.remoteUid
                )

                if (!uiState.sessionEnded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = if (uiState.chromeVisible) {
                                    "Hide live controls"
                                } else {
                                    "Show live controls"
                                }
                            }
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.toggleChromeVisibility() }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.chromeVisible && !uiState.sessionEnded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    StreamHeader(
                        session = session,
                        incomingRequestsCount = if (uiState.isHost) uiState.incomingRequests.size else 0,
                        onLeave = { leaveOrConfirmEnd() },
                        onHostTools = if (uiState.isHost) {
                            { showHostTools = true }
                        } else {
                            null
                        }
                    )
                }

                // --- 2.5 INCOMING REQUESTS PANEL (Organizer Only) ---
                AnimatedVisibility(
                    visible = uiState.chromeVisible && uiState.isHost && uiState.incomingRequests.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                ) {
                    IncomingRequestsPanel(
                        requests = uiState.incomingRequests,
                        isLoading = uiState.requestsLoading,
                        onAccept = viewModel::acceptJoinRequest,
                        onReject = viewModel::rejectJoinRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                if (uiState.sessionEnded) {
                    SessionEndedOverlay(
                        session = session,
                        isHost = uiState.isHost,
                        mindLoomReplayShareInProgress = uiState.mindLoomReplayShareInProgress,
                        onLeave = onLeave,
                        onWatchReplay = {
                            context.startActivity(
                                LiveArchivePlayerIntent.create(
                                    context = context,
                                    sessionId = session.sessionId,
                                    title = session.title,
                                )
                            )
                        },
                        onShareReplay = {
                            viewModel.shareReplayLink { url ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Replay"))
                            }
                        },
                        onPostReplayToMindLoom = viewModel::shareReplayToMindLoom,
                    )
                }

                AnimatedVisibility(
                    visible = uiState.chromeVisible && !uiState.sessionEnded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (uiState.isHost) {
                        HostControlPanel(
                            isAudioMuted = uiState.isAudioMuted,
                            isVideoMuted = uiState.isVideoMuted,
                            onToggleAudio = viewModel::toggleAudio,
                            onToggleVideo = viewModel::toggleVideo,
                            onSwitchCamera = viewModel::switchCamera,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 16.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ChatOverlay(
                            comments = uiState.liveComments,
                            chatEnabled = session.chatEnabled,
                            onSendMessage = { text, replyTo ->
                                viewModel.postLiveComment(text, replyTo)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        LiveEngagementRail(
                            likesCount = uiState.liveLikesCount,
                            commentsCount = uiState.liveCommentsCount,
                            hasLiked = uiState.hasLikedLive,
                            chatEnabled = session.chatEnabled,
                            onLikeClick = viewModel::toggleLiveLike,
                            // Pausing chat stops writes, not access to the existing conversation.
                            onCommentClick = { showLiveComments = true },
                            onShareClick = {
                                viewModel.shareLiveLink { url ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Live"))
                                }
                            }
                        )
                        if (!uiState.isHost && !uiState.isOnStage) {
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = viewModel::toggleSpeakerMute,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = LiveStudioSurface.copy(alpha = 0.96f),
                                    contentColor = LiveStudioInk,
                                )
                            ) {
                                Icon(
                                    if (uiState.isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Toggle sound",
                                    tint = LiveStudioInk
                                )
                            }
                        }
                        if (!uiState.isHost && (
                            session.stageAccessMode == LiveStageAccessMode.REQUEST_TO_JOIN &&
                            !uiState.isOnStage
                        )) {
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = viewModel::requestJoinStage,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.People, "Request stage", tint = LiveStudioSurface)
                            }
                        }
                    }
                }
                }

                if (showLiveComments) {
                    LiveCommentsBottomSheet(
                        comments = uiState.liveComments,
                        chatEnabled = session.chatEnabled,
                        onDismiss = { showLiveComments = false },
                        onSend = { text, replyTo -> viewModel.postLiveComment(text, replyTo) }
                    )
                }
            }

            // --- 4. Loading or Error Overlay ---
            if (uiState.isLoading) {
                LoadingOverlay("Connecting to stream...")
            } else if (uiState.error != null) {
                val needsBroadcastSettings = uiState.error!!.startsWith("Camera and microphone access")
                val endStreamFailed = uiState.error!!.startsWith("We couldn't end the stream")
                ErrorOverlay(
                    text = uiState.error!!,
                    title = if (endStreamFailed) "Couldn't end stream" else "Can't join stream",
                    onDismiss = onLeave,
                    onOpenSettings = if (needsBroadcastSettings) {
                        {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                            )
                        }
                    } else {
                        null
                    },
                    onRetry = when {
                        needsBroadcastSettings -> { { viewModel.retryStageJoin(context) } }
                        endStreamFailed -> { { viewModel.endStream(onComplete = onLeave) } }
                        else -> null
                    },
                )
            }

        if (showHostTools && uiState.isHost) {
            LiveHostToolsSheet(
                session = session!!,
                viewers = uiState.roomViewers.filter { it.userId != session.hostId },
                blockedViewerIds = uiState.blockedViewerIds,
                mindLoomShareInProgress = uiState.mindLoomShareInProgress,
                mindLoomReplayShareInProgress = uiState.mindLoomReplayShareInProgress,
                onDismiss = { showHostTools = false },
                onShareLive = {
                    viewModel.shareLiveLink { url ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Live"))
                    }
                },
                onShareReplay = {
                    viewModel.shareReplayLink { url ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Replay"))
                    }
                },
                onShareMindLoom = viewModel::shareToMindLoom,
                onShareReplayMindLoom = viewModel::shareReplayToMindLoom,
                onToggleChat = viewModel::toggleChatEnabled,
                onBlockViewer = viewModel::blockViewer,
                onUnblockViewer = viewModel::unblockViewer,
                onReplayVisibility = viewModel::updateReplayVisibility
            )
        }
    }
    }
}

@Composable
private fun VideoRenderer(engine: RtcEngine?, publishLocally: Boolean, remoteUid: Int?) {
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
                if (publishLocally) {
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
private fun SessionEndedOverlay(
    session: LiveSession,
    isHost: Boolean,
    mindLoomReplayShareInProgress: Boolean,
    onLeave: () -> Unit,
    onWatchReplay: () -> Unit,
    onShareReplay: () -> Unit,
    onPostReplayToMindLoom: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            color = LiveStudioSurface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Stream ended",
                    color = LiveStudioInk,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(session.title, color = LiveStudioMuted, textAlign = TextAlign.Center)
                if (session.isArchiveReady) {
                    Button(
                        onClick = onWatchReplay,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveStudioAccent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Watch replay", fontWeight = FontWeight.SemiBold)
                    }
                    if (isHost) {
                        OutlinedButton(
                            onClick = onShareReplay,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Share replay link")
                        }
                        Button(
                            onClick = onPostReplayToMindLoom,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !mindLoomReplayShareInProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiveStudioLiveMark,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                if (mindLoomReplayShareInProgress) "Posting to MindLoom…" else "Post replay to MindLoom",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (session.isArchiveProcessing) {
                    CircularProgressIndicator(color = LiveStudioAccent, strokeWidth = 2.dp)
                    Text("Replay is processing…", color = LiveStudioMuted)
                }
                TextButton(onClick = onLeave) { Text("Close") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveHostToolsSheet(
    session: LiveSession,
    viewers: List<LiveRoomViewer>,
    blockedViewerIds: Set<String>,
    mindLoomShareInProgress: Boolean,
    mindLoomReplayShareInProgress: Boolean,
    onDismiss: () -> Unit,
    onShareLive: () -> Unit,
    onShareReplay: () -> Unit,
    onShareMindLoom: () -> Unit,
    onShareReplayMindLoom: () -> Unit,
    onToggleChat: () -> Unit,
    onBlockViewer: (String) -> Unit,
    onUnblockViewer: (String) -> Unit,
    onReplayVisibility: (LiveReplayVisibility) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LiveStudioSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Broadcast desk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiveStudioInk)
            Text("Share the room, manage chat, and keep the audience safe.", style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)
            LiveHostToolsSection(
                title = "Share and discovery",
                subtitle = "Invite viewers or feature this broadcast in MindLoom.",
            ) {
            Button(
                onClick = { onShareLive(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LiveStudioAccent, contentColor = Color.White),
            ) {
                Text("Share live link", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onShareMindLoom,
                modifier = Modifier.fillMaxWidth(),
                enabled = !mindLoomShareInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = LiveStudioLiveMark, contentColor = Color.White),
            ) {
                Text(if (mindLoomShareInProgress) "Sharing to MindLoom…" else "Post live to MindLoom", fontWeight = FontWeight.SemiBold)
            }
            }
            LiveHostToolsSection(
                title = "Live room",
                subtitle = "Pause new comments without hiding the existing conversation.",
            ) {
            OutlinedButton(onClick = onToggleChat, modifier = Modifier.fillMaxWidth()) {
                Text(if (session.chatEnabled) "Pause live chat" else "Resume live chat")
            }
            }
            LiveHostToolsSection(
                title = "Replay access",
                subtitle = "Choose who can replay the broadcast after it ends.",
            ) {
            if (session.isEnded || session.isArchiveReady) {
                OutlinedButton(onClick = { onShareReplay(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Share replay link")
                }
                if (session.isArchiveReady) {
                    Button(
                        onClick = onShareReplayMindLoom,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !mindLoomReplayShareInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = LiveStudioAccent, contentColor = Color.White),
                    ) {
                        Text(
                            if (mindLoomReplayShareInProgress) "Posting replay…" else "Post replay to MindLoom",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text("Replay visibility", style = MaterialTheme.typography.titleSmall, color = LiveStudioInk, fontWeight = FontWeight.SemiBold)
            LiveReplayVisibility.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { onReplayVisibility(mode); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (session.replayVisibility == mode) LiveStudioAccentSoft else Color.Transparent,
                        contentColor = if (session.replayVisibility == mode) LiveStudioAccent else LiveStudioInk,
                    ),
                ) {
                    Text(
                        mode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                        fontWeight = if (session.replayVisibility == mode) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            }
            LiveHostToolsSection(
                title = "Audience moderation",
                subtitle = "Block disruptive viewers. You can reverse this at any time.",
            ) {
            if (viewers.isEmpty()) {
                Text("No other viewers are active right now.", style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)
            } else {
                viewers.forEach { viewer ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = LiveStudioAccentSoft.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(viewer.displayName, color = LiveStudioInk, modifier = Modifier.weight(1f))
                            if (blockedViewerIds.contains(viewer.userId)) {
                                TextButton(onClick = { onUnblockViewer(viewer.userId) }) {
                                    Text("Unblock")
                                }
                            } else {
                                TextButton(onClick = { onBlockViewer(viewer.userId) }) {
                                    Text("Block")
                                }
                            }
                        }
                    }
                }
            }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LiveHostToolsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LiveStudioAccentSoft.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = LiveStudioInk)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)
            content()
        }
    }
}

@Composable
private fun StreamHeader(
    session: LiveSession,
    incomingRequestsCount: Int = 0,
    onLeave: () -> Unit,
    onHostTools: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = LiveChromeScrim,
            shape = RoundedCornerShape(22.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LiveStudioAccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.hostName.firstOrNull()?.uppercase() ?: "?",
                        color = LiveStudioAccent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title.ifBlank { "Live session" },
                        color = LiveStudioInk,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${session.hostName.ifBlank { "Host" }} - ${session.viewerCount} watching",
                        color = LiveStudioMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                LiveStatusPill(label = "LIVE", isLive = true)
                if (incomingRequestsCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = LiveStudioAccent,
                    ) {
                        Text(
                            text = incomingRequestsCount.coerceAtMost(9).toString(),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = LiveStudioSurface,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onHostTools != null) {
                FilledIconButton(
                    onClick = onHostTools,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LiveStudioSurface.copy(alpha = 0.96f),
                        contentColor = LiveStudioInk,
                    ),
                ) {
                    Icon(Icons.Default.MoreVert, "Host tools", tint = LiveStudioInk)
                }
            }
            FilledIconButton(
                onClick = onLeave,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LiveStudioSurface.copy(alpha = 0.96f),
                    contentColor = LiveStudioInk,
                ),
            ) {
                Icon(Icons.Default.Close, "Leave stream", tint = LiveStudioInk)
            }
        }
    }
}

@Composable
private fun ChatOverlay(
    comments: List<LiveRoomComment>,
    chatEnabled: Boolean,
    onSendMessage: (String, LiveRoomComment?) -> Unit,
    modifier: Modifier = Modifier
) {
    var chatInput by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<LiveRoomComment?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    Column(modifier = modifier) {
        Surface(
            color = LiveChromeScrim,
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (chatEnabled) "LIVE CHAT" else "CHAT PAUSED",
                        color = if (chatEnabled) LiveStudioInk else LiveStudioMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${comments.size} comments",
                        color = LiveStudioMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (comments.isEmpty()) {
                    Text(
                        text = if (chatEnabled) "Be the first to say hello." else "The host has paused live chat.",
                        color = LiveStudioMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 184.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        reverseLayout = true,
                    ) {
                        items(comments.reversed(), key = { it.id }) { comment ->
                            ChatItem(
                                msg = comment,
                                onReply = if (chatEnabled) {
                                    { replyTo = comment }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        replyTo?.let { parent ->
            Surface(
                color = LiveStudioSurface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to ${parent.authorName.ifBlank { "Anonymous" }}",
                        color = LiveStudioInk,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { replyTo = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Cancel reply", tint = LiveStudioInk)
                    }
                }
            }
        }

        ChatInputField(
            value = chatInput,
            onValueChange = { chatInput = it },
            enabled = chatEnabled,
            onSend = {
                if (chatInput.isNotBlank()) {
                    onSendMessage(chatInput, replyTo)
                    chatInput = ""
                    replyTo = null
                }
            }
        )
    }
}

@Composable
private fun ChatItem(
    msg: LiveRoomComment,
    onReply: (() -> Unit)? = null,
) {
    Surface(
        color = LiveStudioSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            msg.replyToAuthorName?.let { parentName ->
                Text(
                    text = "↩ $parentName: ${msg.replyToText.orEmpty()}",
                    color = LiveStudioMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${msg.authorName.ifBlank { "Anonymous" }}: ",
                    color = LiveStudioInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = msg.text,
                    color = LiveStudioInk,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onReply != null) {
                    IconButton(
                        onClick = onReply,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Reply,
                            contentDescription = "Reply to ${msg.authorName}",
                            tint = LiveStudioAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                if (enabled) "Add a comment..." else "Host paused chat",
                color = LiveStudioMuted
            )
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        leadingIcon = {
            Icon(Icons.Default.Comment, contentDescription = null, tint = LiveStudioMuted)
        },
        trailingIcon = {
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LiveStudioAccent,
                    contentColor = Color.White,
                    disabledContainerColor = LiveStudioAccentSoft,
                    disabledContentColor = LiveStudioMuted,
                ),
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send comment",
                )
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LiveStudioSurface.copy(alpha = 0.96f),
            unfocusedContainerColor = LiveStudioSurface.copy(alpha = 0.96f),
            focusedTextColor = LiveStudioInk,
            unfocusedTextColor = LiveStudioInk,
            cursorColor = LiveStudioAccent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(24.dp),
        singleLine = true
    )
}

@Composable
private fun LiveEngagementRail(
    likesCount: Long,
    commentsCount: Long,
    hasLiked: Boolean,
    chatEnabled: Boolean,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = LiveChromeScrim,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StreamControlButton(
                icon = if (hasLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                text = if (hasLiked) "Liked ($likesCount)" else likesCount.toString(),
                contentDescription = if (hasLiked) "Unlike stream" else "Like stream",
                onClick = onLikeClick,
                active = hasLiked
            )
            StreamControlButton(
                icon = Icons.Default.Comment,
                text = if (chatEnabled) commentsCount.toString() else "Paused",
                contentDescription = if (chatEnabled) "Open comments" else "Chat paused",
                onClick = onCommentClick,
                active = chatEnabled
            )
            StreamControlButton(
                icon = Icons.Default.Share,
                text = "Share",
                contentDescription = "Share live link",
                onClick = onShareClick,
                active = true
            )
        }
    }
}

@Composable
private fun LiveCommentsBottomSheet(
    comments: List<LiveRoomComment>,
    chatEnabled: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, LiveRoomComment?) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<LiveRoomComment?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Live Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!chatEnabled) {
                Text(
                    text = "Chat is paused by the host.",
                    color = LiveStudioMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No comments yet. Start the conversation.",
                        color = LiveStudioMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(comments, key = { it.id }) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                item.replyToAuthorName?.let { parentName ->
                                    Text(
                                        text = "↩ $parentName: ${item.replyToText.orEmpty()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LiveStudioMuted,
                                        maxLines = 2,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.authorName.ifBlank { "Anonymous" },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (chatEnabled) {
                                        TextButton(onClick = { replyTo = item }) {
                                            Text("Reply")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            replyTo?.let { parent ->
                Text(
                    text = "Replying to ${parent.authorName.ifBlank { "Anonymous" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LiveStudioAccent,
                )
                TextButton(onClick = { replyTo = null }) { Text("Cancel reply") }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = chatEnabled,
                placeholder = {
                    Text(if (chatEnabled) "Add a comment..." else "Chat is paused")
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val message = input.trim()
                            if (message.isNotEmpty()) {
                                onSend(message, replyTo)
                                input = ""
                                replyTo = null
                            }
                        },
                        enabled = chatEnabled && input.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send comment")
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
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
    Surface(
        modifier = modifier,
        color = LiveChromeScrim,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StreamControlButton(
                icon = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                text = if (isAudioMuted) "Unmute" else "Mute",
                onClick = onToggleAudio,
                active = !isAudioMuted
            )
            StreamControlButton(
                icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                text = if (isVideoMuted) "Video On" else "Video Off",
                onClick = onToggleVideo,
                active = !isVideoMuted
            )
            StreamControlButton(
                icon = Icons.Default.FlipCameraAndroid,
                text = "Flip",
                onClick = onSwitchCamera
            )
        }
    }
}

// --- ADDED: Missing composable function from original file ---
@Composable
private fun StreamControlButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    active: Boolean = true,
    contentDescription: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics {
            this.contentDescription = contentDescription ?: text
        }
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) {
                    LiveStudioAccentSoft
                } else {
                    LiveStudioHighlight
                },
                contentColor = if (active) LiveStudioAccent else LiveStudioHighlightInk
            )
        ) {
            Icon(icon, contentDescription = contentDescription ?: text)
        }

        Text(
            text = text,
            color = LiveStudioInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// --- ADDED: Missing composable function from original file ---
@Composable
private fun LoadingOverlay(text: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LiveChromeScrim
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = LiveStudioAccent)
            Spacer(Modifier.height(16.dp))
            Text(text, color = LiveStudioInk)
        }
    }
}

// --- ADDED: Missing composable function from original file ---
@Composable
private fun ErrorOverlay(
    text: String,
    title: String = "Can't join stream",
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiveChromeScrim),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = LiveStudioSurface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LiveStudioInk,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiveStudioMuted,
                )
                Spacer(Modifier.height(18.dp))
                if (onOpenSettings != null) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Open app settings")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (onRetry != null) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Try again")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Leave")
                }
            }
        }
    }
}

// --- NEW: Incoming Requests Panel for Organizers ---
@Composable
private fun IncomingRequestsPanel(
    requests: List<JoinLiveStreamRequest>,
    isLoading: Boolean,
    onAccept: (JoinLiveStreamRequest) -> Unit,
    onReject: (JoinLiveStreamRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Join Requests (${requests.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Request list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(requests, key = { it.requestId }) { request ->
                    RequestCard(
                        request = request,
                        onAccept = { onAccept(request) },
                        onReject = { onReject(request) },
                        enabled = !isLoading
                    )
                }
            }
        }
    }
}

// --- NEW: Individual Request Card Component ---
@Composable
private fun RequestCard(
    request: JoinLiveStreamRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volunteer info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Volunteer avatar
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    if (request.volunteerProfilePicUrl != null) {
                        AsyncImage(
                            model = request.volunteerProfilePicUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Name
                Text(
                    text = request.volunteerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action buttons — icon + label (not color-only) for color-vision accessibility
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline")
                }
                Button(
                    onClick = onAccept,
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept")
                }
            }
        }
    }
}

// --- ADDED: Missing data classes for completeness ---


