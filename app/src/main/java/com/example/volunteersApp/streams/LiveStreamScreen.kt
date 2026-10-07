@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.streams

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.view.SurfaceView
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
    var shareSheet by remember { mutableStateOf<LiveRoomShareSheet?>(null) }
    // Do not retain a previous account when auth changes while this screen is in the back stack.
    val currentUid = Firebase.auth.currentUser?.uid
    val openLiveShareSheet: () -> Unit = {
        viewModel.shareLiveLink { url -> shareSheet = LiveRoomShareSheet(url = url, isReplay = false) }
    }
    val openReplayShareSheet: () -> Unit = {
        viewModel.shareReplayLink { url -> shareSheet = LiveRoomShareSheet(url = url, isReplay = true) }
    }
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

    var autoLeft by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, session, uiState.error) {
        // A second onLeave would pop the screen beneath a room that is still resolving.
        if (!autoLeft && !uiState.isLoading && session == null && uiState.error.isNullOrBlank()) {
            autoLeft = true
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

    // Covers both the Activity and the in-app NavHost route; the host must confirm before ending.
    BackHandler(enabled = true) { leaveOrConfirmEnd() }

    val hostView = LocalView.current
    DisposableEffect(hostView) {
        hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = false }
    }

    LiveStudioTheme {
    if (showEndStreamConfirm) {
        AlertDialog(
            onDismissRequest = { showEndStreamConfirm = false },
            title = { Text("End live stream?") },
            text = {
                Text("Ending stops the broadcast for everyone watching. Choose Cancel to keep streaming.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndStreamConfirm = false
                        viewModel.endStream(keepRoomForSummary = true)
                    }
                ) {
                    Text("End stream", color = LiveStudioDanger, fontWeight = FontWeight.Bold)
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
                // The host always fills the screen; stage guests (and a guest's own camera) are tiles.
                val mainRemoteUid = if (uiState.isHost) null else uiState.remoteUid
                val mainIsLocal = uiState.isHost || (uiState.isOnStage && mainRemoteUid == null)
                VideoRenderer(
                    engine = viewModel.getEngine(),
                    publishLocally = mainIsLocal,
                    remoteUid = mainRemoteUid
                )
                if (!uiState.sessionEnded) {
                    val hostLabel = session.hostName.ifBlank { "The host" }
                    when {
                        mainIsLocal && uiState.isVideoMuted -> LiveVideoPlaceholder(
                            session = session,
                            title = "Your camera is off",
                            subtitle = "Viewers can still hear you. Turn video back on from the controls.",
                        )
                        !mainIsLocal && mainRemoteUid == null && !uiState.isLoading -> LiveVideoPlaceholder(
                            session = session,
                            title = if (uiState.isReconnecting) "Reconnecting…" else "Waiting for $hostLabel",
                            subtitle = "The stream will start automatically.",
                            showProgress = true,
                        )
                        !mainIsLocal && mainRemoteUid != null && mainRemoteUid in uiState.mutedVideoUids -> LiveVideoPlaceholder(
                            session = session,
                            title = "$hostLabel paused the video",
                            subtitle = if (mainRemoteUid in uiState.mutedAudioUids) "Audio is muted too." else "You can still hear the stream.",
                        )
                    }
                    val mainAudioMuted = if (mainIsLocal) {
                        uiState.isAudioMuted
                    } else {
                        mainRemoteUid != null && mainRemoteUid in uiState.mutedAudioUids
                    }
                    if (mainAudioMuted) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 116.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.MicOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    if (mainIsLocal) "You're muted" else "$hostLabel is muted",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                if (!uiState.sessionEnded) {
                    StageGuestTiles(
                        engine = viewModel.getEngine(),
                        remoteUids = uiState.remoteUids.filter { it != mainRemoteUid },
                        showLocalTile = uiState.isOnStage && !mainIsLocal,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 76.dp, end = 12.dp)
                    )
                }

                AnimatedVisibility(
                    visible = uiState.isReconnecting && !uiState.sessionEnded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp)
                ) {
                    Surface(color = LiveChromeScrim, shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = LiveStudioAccent,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "Reconnecting…",
                                color = LiveStudioInk,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

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
                        viewerCount = uiState.liveViewerCount ?: session.viewerCount,
                        incomingRequestsCount = if (uiState.isHost) uiState.incomingRequests.size else 0,
                        onLeave = { leaveOrConfirmEnd() },
                        onHostTools = if (uiState.isHost) {
                            { showHostTools = true }
                        } else {
                            null
                        },
                        onEndStream = if (uiState.isHost) {
                            { showEndStreamConfirm = true }
                        } else {
                            null
                        },
                    )
                }

                // --- 2.5 INCOMING REQUESTS PANEL (Organizer Only) ---
                AnimatedVisibility(
                    visible = uiState.chromeVisible && uiState.isHost && uiState.incomingRequests.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp)
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
                        summary = if (uiState.isHost) uiState.endSummary else null,
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
                        onShareReplay = openReplayShareSheet,
                        onPostReplayToMindLoom = viewModel::shareReplayToMindLoom,
                        canWatchReplay = uiState.isHost ||
                            session.canWatchReplay(currentUid) ||
                            session.replayVisibility == LiveReplayVisibility.FOLLOWERS_ONLY,
                        onReplayVisibility = if (uiState.isHost) viewModel::updateReplayVisibility else null,
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
                    // Stage guests publish too, so they get the same mic / camera controls plus "Leave stage".
                    if (uiState.isHost || uiState.isOnStage) {
                        HostControlPanel(
                            isAudioMuted = uiState.isAudioMuted,
                            isVideoMuted = uiState.isVideoMuted,
                            onToggleAudio = viewModel::toggleAudio,
                            onToggleVideo = viewModel::toggleVideo,
                            onSwitchCamera = viewModel::switchCamera,
                            onLeaveStage = if (!uiState.isHost && uiState.isOnStage) viewModel::leaveStage else null,
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
                        // Keep the player clear by default. The full, writable conversation opens in a
                        // YouTube-style bottom sheet from this preview or the chat action.
                        LiveChatPeek(
                            comments = uiState.liveComments.filterNot { it.authorId in uiState.blockedViewerIds },
                            chatEnabled = session.chatEnabled,
                            modifier = Modifier.weight(1f),
                            totalCount = uiState.liveCommentsCount,
                            onOpenFullChat = { showLiveComments = true },
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
                            onShareClick = openLiveShareSheet,
                            // Only the host can mint invite links for non-public rooms.
                            showShare = uiState.isHost || session.viewAccessMode == LiveViewAccessMode.PUBLIC,
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
                            session.stageAccessMode != LiveStageAccessMode.HOST_ONLY &&
                            !uiState.isOnStage
                        )) {
                            val stageRequestPending = uiState.myStageRequestStatus
                                .equals(LiveJoinRequestStatus.PENDING.raw, ignoreCase = true)
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = viewModel::requestJoinStage,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (stageRequestPending) {
                                        LiveStudioHighlight
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            ) {
                                if (stageRequestPending) {
                                    Icon(Icons.Default.HourglassTop, "Stage request pending", tint = LiveStudioHighlightInk)
                                } else {
                                    Icon(Icons.Default.People, "Request stage", tint = LiveStudioSurface)
                                }
                            }
                        }
                    }
                }
                }

                if (showLiveComments) {
                    LiveCommentsBottomSheet(
                        comments = uiState.liveComments.filterNot { it.authorId in uiState.blockedViewerIds },
                        chatEnabled = session.chatEnabled,
                        onDismiss = { showLiveComments = false },
                        onSend = { text, replyTo -> viewModel.postLiveComment(text, replyTo) },
                        onDelete = if (uiState.isHost) viewModel::deleteLiveComment else null,
                        hostId = session.hostId,
                        totalCount = uiState.liveCommentsCount,
                        onHideUser = if (uiState.isHost) viewModel::blockViewer else null,
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
                    onDismiss = if (endStreamFailed && session != null) viewModel::dismissEndStreamError else onLeave,
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
                        endStreamFailed -> { { viewModel.endStream(keepRoomForSummary = true) } }
                        session != null && !uiState.sessionEnded -> { { viewModel.retryJoin(context) } }
                        else -> null
                    },
                    dismissLabel = if (endStreamFailed && session != null) "Keep streaming" else "Leave",
                )
            }

        if (showHostTools && uiState.isHost && session != null) {
            LiveHostToolsSheet(
                session = session,
                viewers = uiState.roomViewers.filter { it.userId != session.hostId },
                blockedViewerIds = uiState.blockedViewerIds,
                mindLoomShareInProgress = uiState.mindLoomShareInProgress,
                mindLoomReplayShareInProgress = uiState.mindLoomReplayShareInProgress,
                onDismiss = { showHostTools = false },
                onShareLive = openLiveShareSheet,
                onShareReplay = openReplayShareSheet,
                onRemoveFromStage = viewModel::removeFromStage,
                onShareMindLoom = viewModel::shareToMindLoom,
                onShareReplayMindLoom = viewModel::shareReplayToMindLoom,
                onToggleChat = viewModel::toggleChatEnabled,
                onBlockViewer = viewModel::blockViewer,
                onUnblockViewer = viewModel::unblockViewer,
                onReplayVisibility = viewModel::updateReplayVisibility
            )
        }

        val activeShareSheet = shareSheet
        if (activeShareSheet != null && session != null) {
            LiveShareSheet(
                title = session.title,
                url = activeShareSheet.url,
                isReplay = activeShareSheet.isReplay,
                onDismiss = { shareSheet = null },
                onPostToMindLoom = when {
                    !uiState.isHost -> null
                    activeShareSheet.isReplay -> viewModel::shareReplayToMindLoom
                    else -> viewModel::shareToMindLoom
                },
                mindLoomInProgress = if (activeShareSheet.isReplay) {
                    uiState.mindLoomReplayShareInProgress
                } else {
                    uiState.mindLoomShareInProgress
                },
            )
        }
    }
    }
}

private data class LiveRoomShareSheet(val url: String, val isReplay: Boolean)

@Composable
private fun VideoRenderer(engine: RtcEngine?, publishLocally: Boolean, remoteUid: Int?) {
    val context = LocalContext.current

    // Keep the view alive across recompositions
    val surfaceView = remember {
        SurfaceView(context).apply { setZOrderMediaOverlay(true) }
    }
    val boundRemoteUid = remember { intArrayOf(0) }

    AndroidView(
        factory = { surfaceView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            engine?.let {
                // Detach the previous broadcaster so a guest leaving cannot blank the host's view.
                val previous = boundRemoteUid[0]
                if (previous != 0 && (publishLocally || previous != remoteUid)) {
                    it.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, previous))
                    boundRemoteUid[0] = 0
                }
                if (!publishLocally && remoteUid != null) {
                    boundRemoteUid[0] = remoteUid
                }
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

/** Full-bleed card shown instead of a black frame: host not connected yet, camera paused, or own camera off. */
@Composable
private fun LiveVideoPlaceholder(
    session: LiveSession,
    title: String,
    subtitle: String,
    showProgress: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LivePalette.StageBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            LiveHostAvatar(name = session.hostName, photoUrl = session.hostProfilePicUrl, size = 88.dp)
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            if (showProgress) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Small picture-in-picture tiles for stage guests, YouTube-style co-stream layout. */
@Composable
private fun StageGuestTiles(
    engine: RtcEngine?,
    remoteUids: List<Int>,
    showLocalTile: Boolean,
    modifier: Modifier = Modifier,
) {
    if (engine == null || (remoteUids.isEmpty() && !showLocalTile)) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLocalTile) {
            StageVideoTile(engine = engine, uid = 0, isLocal = true, label = "You")
        }
        remoteUids.take(MAX_STAGE_TILES).forEach { uid ->
            key(uid) {
                StageVideoTile(engine = engine, uid = uid, isLocal = false, label = "Guest")
            }
        }
    }
}

@Composable
private fun StageVideoTile(engine: RtcEngine, uid: Int, isLocal: Boolean, label: String) {
    val context = LocalContext.current
    // TextureView composes above the full-screen SurfaceView without z-order conflicts.
    val textureView = remember(uid, isLocal) { TextureView(context) }
    DisposableEffect(uid, isLocal) {
        if (isLocal) {
            engine.setupLocalVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        } else {
            engine.setupRemoteVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
        }
        onDispose {
            if (!isLocal) {
                engine.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            }
        }
    }
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 136.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LiveStudioBackground)
    ) {
        AndroidView(factory = { textureView }, modifier = Modifier.fillMaxSize())
        Surface(
            color = LiveChromeScrim,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        ) {
            Text(
                label,
                color = LiveStudioInk,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private const val MAX_STAGE_TILES = 3

/** "LIVE 12:34" style elapsed label that ticks every second. */
@Composable
private fun rememberLiveElapsedLabel(startedAtMs: Long?): String? {
    if (startedAtMs == null) return null
    val nowMs by produceState(initialValue = System.currentTimeMillis(), startedAtMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return formatLiveDuration((nowMs - startedAtMs).coerceAtLeast(0L))
}

internal fun formatLiveDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun LiveSummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = LiveStudioInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, color = LiveStudioMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SessionEndedOverlay(
    session: LiveSession,
    summary: LiveEndSummary? = null,
    isHost: Boolean,
    mindLoomReplayShareInProgress: Boolean,
    onLeave: () -> Unit,
    onWatchReplay: () -> Unit,
    onShareReplay: () -> Unit,
    onPostReplayToMindLoom: () -> Unit,
    canWatchReplay: Boolean = true,
    onReplayVisibility: ((LiveReplayVisibility) -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = LiveStudioSurface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveHostAvatar(
                    name = session.hostName,
                    photoUrl = session.hostProfilePicUrl,
                    size = 64.dp,
                )
                Text(
                    "Stream ended",
                    color = LiveStudioInk,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${session.title.ifBlank { "Live session" }} · ${session.hostName.ifBlank { "Host" }}",
                    color = LiveStudioMuted,
                    textAlign = TextAlign.Center,
                )
                when {
                    session.isArchiveReady && canWatchReplay -> Unit
                    session.isArchiveReady -> Text(
                        "The host has kept this replay private.",
                        color = LiveStudioMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    session.isArchiveProcessing -> Unit
                    else -> Text(
                        "No replay was recorded for this stream.",
                        color = LiveStudioMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                if (onReplayVisibility != null && (session.isArchiveReady || session.isArchiveProcessing)) {
                    Text(
                        "Who can watch the replay",
                        color = LiveStudioInk,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LiveReplayVisibility.entries.forEach { mode ->
                            FilterChip(
                                selected = session.replayVisibility == mode,
                                onClick = { onReplayVisibility(mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LiveStudioInk,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
                summary?.let { stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        LiveSummaryStat(formatLiveDuration(stats.durationMs), "Duration", Modifier.weight(1f))
                        LiveSummaryStat(stats.peakViewers.toString(), "Peak viewers", Modifier.weight(1f))
                        LiveSummaryStat(stats.likes.toString(), "Likes", Modifier.weight(1f))
                        LiveSummaryStat(stats.comments.toString(), "Comments", Modifier.weight(1f))
                    }
                }
                if (session.isArchiveReady) {
                    if (canWatchReplay) {
                        LiveAccentButton(
                            onClick = onWatchReplay,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Watch replay", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (isHost) {
                        OutlinedButton(
                            onClick = onShareReplay,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share replay link")
                        }
                        Button(
                            onClick = onPostReplayToMindLoom,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !mindLoomReplayShareInProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiveStudioInk,
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
    onReplayVisibility: (LiveReplayVisibility) -> Unit,
    onRemoveFromStage: ((String) -> Unit)? = null,
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
            Text("Live control room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LiveStudioInk)
            Text("Share the room, manage chat, and keep the audience safe.", style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)

            LiveHostToolsHeading("Share")
            LiveHostToolRow(
                icon = Icons.Default.Share,
                title = "Share live link",
                subtitle = if (session.viewAccessMode == LiveViewAccessMode.PUBLIC) {
                    "Anyone with the link can watch."
                } else {
                    "Invite link for this ${session.viewAccessMode.name.lowercase().replace('_', ' ')} stream."
                },
                onClick = { onShareLive(); onDismiss() },
            )
            LiveHostToolRow(
                icon = Icons.Default.AutoAwesome,
                title = if (mindLoomShareInProgress) "Posting to MindLoom…" else "Post live to MindLoom",
                subtitle = if (session.viewAccessMode == LiveViewAccessMode.PUBLIC) {
                    "Feature this broadcast in the MindLoom feed."
                } else {
                    "Only people allowed by your audience setting can join from the post."
                },
                onClick = onShareMindLoom,
                enabled = !mindLoomShareInProgress,
                loading = mindLoomShareInProgress,
            )

            LiveHostToolsHeading("Chat")
            LiveHostToolRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Live chat",
                subtitle = if (session.chatEnabled) "Viewers can send messages." else "Paused. Viewers can still read the conversation.",
                onClick = onToggleChat,
                trailing = {
                    Switch(checked = session.chatEnabled, onCheckedChange = { onToggleChat() })
                },
            )

            LiveHostToolsHeading("Replay")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveReplayVisibility.entries.forEach { mode ->
                    FilterChip(
                        selected = session.replayVisibility == mode,
                        onClick = { onReplayVisibility(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LiveStudioInk,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            if (session.isEnded || session.isArchiveReady) {
                LiveHostToolRow(
                    icon = Icons.Default.Share,
                    title = "Share replay link",
                    subtitle = "Send the recording to people who missed it.",
                    onClick = { onShareReplay(); onDismiss() },
                )
                if (session.isArchiveReady) {
                    LiveHostToolRow(
                        icon = Icons.Default.AutoAwesome,
                        title = if (mindLoomReplayShareInProgress) "Posting replay…" else "Post replay to MindLoom",
                        subtitle = "Replay visibility still controls who can watch.",
                        onClick = onShareReplayMindLoom,
                        enabled = !mindLoomReplayShareInProgress,
                        loading = mindLoomReplayShareInProgress,
                    )
                }
            }

            LiveHostToolsHeading("Audience (${viewers.size})")
            if (viewers.isEmpty()) {
                Text("No other viewers are active right now.", style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)
            } else {
                viewers.forEach { viewer ->
                    val isGuestOnStage = viewer.userId in session.acceptedVolunteerIds
                    val isBlocked = blockedViewerIds.contains(viewer.userId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveHostAvatar(name = viewer.displayName, photoUrl = null, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(viewer.displayName, color = LiveStudioInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            when {
                                isBlocked -> Text("Blocked", color = LiveStudioDanger, style = MaterialTheme.typography.labelSmall)
                                isGuestOnStage -> Text("On stage", color = LiveStudioLiveMark, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (isGuestOnStage && onRemoveFromStage != null) {
                            TextButton(onClick = { onRemoveFromStage(viewer.userId) }) {
                                Text("Remove")
                            }
                        }
                        if (isBlocked) {
                            TextButton(onClick = { onUnblockViewer(viewer.userId) }) {
                                Text("Unblock")
                            }
                        } else {
                            TextButton(onClick = { onBlockViewer(viewer.userId) }) {
                                Text("Block", color = LiveStudioDanger)
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
private fun LiveHostToolsHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LiveStudioInk,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** YouTube Studio-style settings row: leading icon, title + subtitle, optional trailing control. */
@Composable
private fun LiveHostToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F1F1)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = LiveStudioInk, modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, tint = LiveStudioInk, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = LiveStudioInk)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)
        }
        trailing?.invoke()
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
    viewerCount: Long = session.viewerCount,
    incomingRequestsCount: Int = 0,
    onLeave: () -> Unit,
    onHostTools: (() -> Unit)? = null,
    onEndStream: (() -> Unit)? = null,
) {
    val elapsedLabel = rememberLiveElapsedLabel((session.startTime ?: session.createdAt)?.time)
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
                LiveHostAvatar(
                    name = session.hostName,
                    photoUrl = session.hostProfilePicUrl,
                    size = 36.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.hostName.ifBlank { "Host" },
                        color = LiveStudioInk,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = session.title.ifBlank { "Live session" },
                        color = LiveStudioMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    LiveRedBadge(text = elapsedLabel?.let { "LIVE $it" } ?: "LIVE")
                    LiveViewerCountLabel(count = viewerCount)
                }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onEndStream != null) {
                Button(
                    onClick = onEndStream,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .semantics { contentDescription = "End live stream" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveStudioDanger,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("End", fontWeight = FontWeight.Bold)
                }
            }
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
private fun LiveChatPeek(
    comments: List<LiveRoomComment>,
    chatEnabled: Boolean,
    totalCount: Long,
    onOpenFullChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A short preview preserves the broadcast as the focal point; the complete conversation lives
    // in the sheet, where its composer and moderation actions have enough space.
    val recentComments = comments.takeLast(2)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenFullChat)
            .semantics { contentDescription = "Open live chat, $totalCount messages" },
        color = LiveChromeScrim,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (chatEnabled) "Live chat" else "Live chat paused",
                    color = if (chatEnabled) LiveStudioInk else LiveStudioMuted,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCompactCount(maxOf(totalCount, comments.size.toLong())),
                    color = LiveStudioMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = LiveStudioMuted,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp),
                )
            }
            if (recentComments.isEmpty()) {
                Text(
                    text = if (chatEnabled) "Be the first to say hello." else "The host has paused live chat.",
                    color = LiveStudioMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                recentComments.forEach { comment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${comment.authorName.ifBlank { "Anonymous" }}  ",
                            color = LiveStudioInk,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.42f, fill = false),
                        )
                        Text(
                            text = comment.text,
                            color = LiveStudioMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatOverlay(
    comments: List<LiveRoomComment>,
    chatEnabled: Boolean,
    onSendMessage: (String, LiveRoomComment?) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteComment: ((LiveRoomComment) -> Unit)? = null,
    hostId: String? = null,
    totalCount: Long = comments.size.toLong(),
    onHideUser: ((String) -> Unit)? = null,
    onOpenFullChat: (() -> Unit)? = null,
) {
    var chatInput by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<LiveRoomComment?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            // reverseLayout puts the newest comment at index 0.
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier) {
        Surface(
            color = LiveChromeScrim,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (onOpenFullChat != null) Modifier.clickable(onClick = onOpenFullChat) else Modifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (chatEnabled) "Live chat" else "Live chat paused",
                        color = if (chatEnabled) LiveStudioInk else LiveStudioMuted,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatCompactCount(maxOf(totalCount, comments.size.toLong())),
                            color = LiveStudioMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (onOpenFullChat != null) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Open full live chat",
                                tint = LiveStudioMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
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
                            LiveChatMessageRow(
                                comment = comment,
                                isHostAuthor = !hostId.isNullOrBlank() && comment.authorId == hostId,
                                onReply = if (chatEnabled) {
                                    { replyTo = comment }
                                } else {
                                    null
                                },
                                onDelete = onDeleteComment?.let { delete -> { delete(comment) } },
                                onHideUser = onHideUser
                                    ?.takeIf { comment.authorId.isNotBlank() && comment.authorId != hostId }
                                    ?.let { hide -> { hide(comment.authorId) } },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = LiveChromeScrim,
            shape = RoundedCornerShape(28.dp),
        ) {
            LiveChatComposer(
                value = chatInput,
                onValueChange = { chatInput = it },
                enabled = chatEnabled,
                disabledPlaceholder = "Host paused chat",
                replyingTo = replyTo?.authorName?.ifBlank { "Anonymous" },
                onCancelReply = { replyTo = null },
                onSend = {
                    if (chatInput.isNotBlank()) {
                        onSendMessage(chatInput, replyTo)
                        chatInput = ""
                        replyTo = null
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ChatItem(
    msg: LiveRoomComment,
    onReply: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
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
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove comment from ${msg.authorName}",
                            tint = LiveStudioMuted,
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
    modifier: Modifier = Modifier,
    showShare: Boolean = true,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiveRailAction(
            icon = if (hasLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            label = if (likesCount > 0L) formatCompactCount(likesCount) else "Like",
            contentDescription = if (hasLiked) "Remove like, $likesCount likes" else "Like stream, $likesCount likes",
            onClick = onLikeClick,
            active = hasLiked,
        )
        LiveRailAction(
            icon = Icons.Outlined.ChatBubbleOutline,
            label = when {
                !chatEnabled -> "Paused"
                commentsCount > 0L -> formatCompactCount(commentsCount)
                else -> "Chat"
            },
            contentDescription = if (chatEnabled) "Open live chat, $commentsCount messages" else "Live chat paused",
            onClick = onCommentClick,
        )
        if (showShare) {
            LiveRailAction(
                icon = Icons.Default.Share,
                label = "Share",
                contentDescription = "Share live link",
                onClick = onShareClick,
            )
        }
    }
}

@Composable
private fun LiveCommentsBottomSheet(
    comments: List<LiveRoomComment>,
    chatEnabled: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, LiveRoomComment?) -> Unit,
    onDelete: ((LiveRoomComment) -> Unit)? = null,
    hostId: String? = null,
    totalCount: Long = comments.size.toLong(),
    onHideUser: ((String) -> Unit)? = null,
) {
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<LiveRoomComment?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showJumpToLatest by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }
    val isAtLatest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 2
        }
    }

    // Follow new messages only while the reader is at the bottom, like YouTube live chat.
    LaunchedEffect(comments.size) {
        if (comments.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone || isAtLatest) {
            listState.scrollToItem(comments.lastIndex)
            initialScrollDone = true
            showJumpToLatest = false
        } else {
            showJumpToLatest = true
        }
    }
    LaunchedEffect(isAtLatest) {
        if (isAtLatest) showJumpToLatest = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LiveStudioSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Live chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LiveStudioInk,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatCompactCount(maxOf(totalCount, comments.size.toLong())),
                    style = MaterialTheme.typography.titleSmall,
                    color = LiveStudioMuted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Clear, contentDescription = "Close live chat", tint = LiveStudioInk)
                }
            }
            if (!chatEnabled) {
                Surface(
                    color = LiveStudioHighlight,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "The host paused live chat. You can still read the conversation.",
                        color = LiveStudioHighlightInk,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = LiveStudioBorder, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(6.dp))

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (chatEnabled) "Welcome to live chat! Say hello to get things started." else "No messages yet.",
                        color = LiveStudioMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(comments, key = { it.id }) { item ->
                            LiveChatMessageRow(
                                comment = item,
                                isHostAuthor = !hostId.isNullOrBlank() && item.authorId == hostId,
                                onReply = if (chatEnabled) {
                                    { replyTo = item }
                                } else {
                                    null
                                },
                                onDelete = onDelete?.let { delete -> { delete(item) } },
                                onHideUser = onHideUser
                                    ?.takeIf { item.authorId.isNotBlank() && item.authorId != hostId }
                                    ?.let { hide -> { hide(item.authorId) } },
                            )
                        }
                    }
                    if (showJumpToLatest) {
                        LiveJumpToLatestChip(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(comments.lastIndex) }
                                showJumpToLatest = false
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = LiveStudioBorder, modifier = Modifier.padding(vertical = 8.dp))
            LiveChatComposer(
                value = input,
                onValueChange = { input = it },
                enabled = chatEnabled,
                replyingTo = replyTo?.authorName?.ifBlank { "Anonymous" },
                onCancelReply = { replyTo = null },
                onSend = {
                    val message = input.trim()
                    if (message.isNotEmpty()) {
                        onSend(message, replyTo)
                        input = ""
                        replyTo = null
                        scope.launch {
                            if (comments.isNotEmpty()) listState.animateScrollToItem(comments.lastIndex)
                        }
                    }
                },
                modifier = Modifier,
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
    modifier: Modifier = Modifier,
    onLeaveStage: (() -> Unit)? = null,
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
            if (onLeaveStage != null) {
                StreamControlButton(
                    icon = Icons.Default.PersonRemove,
                    text = "Leave stage",
                    contentDescription = "Leave the stage and keep watching",
                    onClick = onLeaveStage,
                    active = false,
                )
            }
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
        color = LivePalette.Ink
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(LivePalette.StageBackdrop),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
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
    dismissLabel: String = "Leave",
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
                    Text(dismissLabel)
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
        color = LiveStudioSurface.copy(alpha = 0.97f),
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
                        tint = LivePalette.Indigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Requests to join the stage (${requests.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = LiveStudioInk,
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
        color = Color(0xFFF1F1F1),
        shape = RoundedCornerShape(12.dp),
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
                LiveHostAvatar(
                    name = request.volunteerName,
                    photoUrl = request.volunteerProfilePicUrl,
                    size = 36.dp,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.volunteerName.ifBlank { "Viewer" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LiveStudioInk,
                    )
                    formatRelativeAgo(request.requestedAt?.time)?.let { ago ->
                        Text(
                            text = "Asked $ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiveStudioMuted,
                        )
                    }
                }
            }

            // Action buttons — icon + label (not color-only) for color-vision accessibility
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline")
                }
                Button(
                    onClick = onAccept,
                    enabled = enabled,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveStudioInk, contentColor = Color.White),
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


