package com.example.volunteersApp.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// WhatsApp-style call palette.
private val CallBackground = Color(0xFF0B141A)
private val CallBackgroundTop = Color(0xFF16232B)
private val CallBar = Color(0xF21F2C34)
private val CallButtonIdle = Color.White.copy(alpha = 0.14f)
private val CallButtonActive = Color.White
private val CallButtonActiveInk = Color(0xFF0B141A)
private val CallAccept = Color(0xFF00A884)
private val CallEnd = Color(0xFFEA0038)
private val CallSubtitle = Color(0xFFD1D7DB)
private val CallAvatarRing = Color(0xFF00A884)

private const val CONTROLS_AUTO_HIDE_MS = 5_000L

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    otherUserName: String,
    otherUserPhotoUrl: String?,
    callType: CallType,
    autoAcceptIncoming: Boolean = false,
    isInPictureInPicture: Boolean = false,
    onMinimize: () -> Unit = {},
    onEnd: () -> Unit,
) {
    MaterialTheme(colorScheme = SocialInboxA11yColorScheme) {
        val uiState by viewModel.uiState.collectAsState()
        val event by viewModel.events.collectAsState()
        val context = LocalContext.current
        val isVideoCall = callType == CallType.VIDEO
        var connectedSeconds by remember { mutableIntStateOf(0) }
        var controlsVisible by remember { mutableStateOf(true) }

        FullScreenCallMode(enabled = !isInPictureInPicture)

        LaunchedEffect(autoAcceptIncoming) {
            viewModel.startCall(context)
            if (autoAcceptIncoming) viewModel.acceptIncomingCall(context)
        }

        LaunchedEffect(uiState.remoteUid) {
            if (uiState.remoteUid == null) {
                connectedSeconds = 0
                return@LaunchedEffect
            }
            val connectedAt = SystemClock.elapsedRealtime()
            while (true) {
                connectedSeconds = ((SystemClock.elapsedRealtime() - connectedAt) / 1_000L).toInt()
                delay(1_000L)
            }
        }

        LaunchedEffect(event) {
            if (event == CallUiEvent.EndCall) onEnd()
        }

        // Like WhatsApp, a connected video call fades its chrome away; a tap brings it back.
        val canAutoHide = isVideoCall && uiState.remoteUid != null && !uiState.isAwaitingAnswer && !isInPictureInPicture
        LaunchedEffect(controlsVisible, canAutoHide) {
            if (!canAutoHide) {
                controlsVisible = true
            } else if (controlsVisible) {
                delay(CONTROLS_AUTO_HIDE_MS)
                controlsVisible = false
            }
        }
        val showChrome = controlsVisible || !canAutoHide
        val subtitle = connectedSeconds.takeIf { uiState.remoteUid != null }?.let(::formatDuration)
            ?: uiState.statusLabel

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CallBackground),
        ) {
            if (isVideoCall) {
                VideoLayer(
                    engine = viewModel.getEngine(),
                    remoteUid = uiState.remoteUid,
                    localVideoMuted = uiState.isVideoMuted,
                    otherUserName = otherUserName,
                    otherUserPhotoUrl = otherUserPhotoUrl,
                    status = uiState.statusLabel,
                    compactLocalPreview = isInPictureInPicture,
                    controlsVisible = showChrome,
                    onBackgroundTap = { if (canAutoHide) controlsVisible = !controlsVisible },
                )
            } else {
                AudioCallBackdrop(
                    name = otherUserName,
                    photoUrl = otherUserPhotoUrl,
                    status = uiState.statusLabel,
                    compact = isInPictureInPicture,
                )
            }

            if (!isInPictureInPicture) {
                AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    CallTopBar(
                        name = otherUserName,
                        subtitle = subtitle,
                        callType = callType,
                        showMinimize = !uiState.isAwaitingAnswer,
                        onMinimize = onMinimize,
                    )
                }

                if (!uiState.error.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 112.dp, start = 24.dp, end = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = CallBar,
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (uiState.isAwaitingAnswer) {
                    IncomingCallActions(
                        callType = callType,
                        onDecline = { viewModel.declineIncomingCall(context) },
                        onAccept = { viewModel.acceptIncomingCall(context) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        CallControls(
                            isAudioMuted = uiState.isAudioMuted,
                            isVideoMuted = uiState.isVideoMuted,
                            isSpeakerEnabled = uiState.isSpeakerEnabled,
                            showVideoControls = isVideoCall,
                            onToggleAudio = viewModel::toggleAudio,
                            onToggleSpeaker = viewModel::toggleSpeaker,
                            onToggleVideo = viewModel::toggleVideo,
                            onSwitchCamera = viewModel::switchCamera,
                            onEndCall = viewModel::endCall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenCallMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val activity = view.context.findActivity()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** WhatsApp-style incoming call: red Decline on the left, green Accept on the right. */
@Composable
private fun IncomingCallActions(
    callType: CallType,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .navigationBarsPadding()
            .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 44.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IncomingCallButton(
                icon = Icons.Default.CallEnd,
                label = "Decline",
                containerColor = CallEnd,
                onClick = onDecline,
            )
            IncomingCallButton(
                icon = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                label = "Accept",
                containerColor = CallAccept,
                onClick = onAccept,
            )
        }
    }
}

@Composable
private fun IncomingCallButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = Color.White,
            ),
            modifier = Modifier.size(68.dp),
        ) {
            Icon(imageVector = icon, contentDescription = "$label call", modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun VideoLayer(
    engine: RtcEngine?,
    remoteUid: Int?,
    localVideoMuted: Boolean,
    otherUserName: String,
    otherUserPhotoUrl: String?,
    status: String,
    compactLocalPreview: Boolean = false,
    controlsVisible: Boolean = true,
    onBackgroundTap: () -> Unit = {},
) {
    val context = LocalContext.current
    var localPreviewOffset by remember { mutableStateOf(Offset.Zero) }
    var hasPositionedLocalPreview by remember { mutableStateOf(false) }
    var isLocalPrimary by remember { mutableStateOf(false) }
    val previewWidth = if (compactLocalPreview) 72.dp else 108.dp
    val previewHeight = if (compactLocalPreview) 102.dp else 156.dp
    val remoteSurfaceView = remember {
        SurfaceView(context).apply {
            setZOrderMediaOverlay(false)
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
    }
    val localSurfaceView = remember {
        SurfaceView(context).apply {
            setZOrderMediaOverlay(true)
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
    }

    LaunchedEffect(remoteUid) {
        // A local-primary view only makes sense after the other participant joins.
        if (remoteUid == null) isLocalPrimary = false
    }

    // While ringing, WhatsApp shows your own camera full screen; the self-view tile appears once connected.
    val localIsMain = isLocalPrimary || remoteUid == null

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CallBackground)) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxOffsetX = remember(maxWidth) {
            with(density) { (maxWidth - previewWidth).coerceAtLeast(0.dp).toPx() }
        }
        val maxOffsetY = remember(maxHeight) {
            with(density) { (maxHeight - previewHeight).coerceAtLeast(0.dp).toPx() }
        }

        LaunchedEffect(maxOffsetX, maxOffsetY) {
            if (!hasPositionedLocalPreview && maxOffsetX > 0f && maxOffsetY > 0f) {
                val topInset = with(density) { 96.dp.toPx() }
                val sideInset = with(density) { 16.dp.toPx() }
                localPreviewOffset = Offset(
                    x = (maxOffsetX - sideInset).coerceAtLeast(0f),
                    y = topInset.coerceAtMost(maxOffsetY),
                )
                hasPositionedLocalPreview = true
            } else {
                localPreviewOffset = Offset(
                    x = localPreviewOffset.x.coerceIn(0f, maxOffsetX),
                    y = localPreviewOffset.y.coerceIn(0f, maxOffsetY),
                )
            }
        }

        key(isLocalPrimary, remoteUid) {
            if (localIsMain) {
                if (localVideoMuted) {
                    if (remoteUid == null) {
                        CallWaitingBackdrop(
                            name = otherUserName,
                            photoUrl = otherUserPhotoUrl,
                            status = status,
                            isVideo = true,
                            compact = compactLocalPreview,
                        )
                    } else {
                        LocalVideoMutedTile(
                            modifier = Modifier.fillMaxSize(),
                            isPreview = false,
                        )
                    }
                } else {
                    AndroidView(
                        factory = { localSurfaceView },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.setZOrderMediaOverlay(false)
                            engine?.setupLocalVideo(
                                VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0),
                            )
                        },
                    )
                }
            } else if (remoteUid != null) {
                AndroidView(
                    factory = { remoteSurfaceView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.setZOrderMediaOverlay(false)
                        engine?.setupRemoteVideo(
                            VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid),
                        )
                    },
                )
            }
        }

        // Tap target above the main video but below the self-view, so the tile keeps its own drag/tap.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (controlsVisible) "Hide call controls" else "Show call controls"
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBackgroundTap,
                ),
        )

        val secondaryPreviewModifier = Modifier
            .offset {
                IntOffset(
                    localPreviewOffset.x.roundToInt(),
                    localPreviewOffset.y.roundToInt(),
                )
            }
            .pointerInput(maxOffsetX, maxOffsetY) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    localPreviewOffset = Offset(
                        x = (localPreviewOffset.x + dragAmount.x).coerceIn(0f, maxOffsetX),
                        y = (localPreviewOffset.y + dragAmount.y).coerceIn(0f, maxOffsetY),
                    )
                }
            }

        key(isLocalPrimary, remoteUid) {
            if (remoteUid != null && isLocalPrimary) {
                Box(
                    modifier = secondaryPreviewModifier
                        .size(width = previewWidth, height = previewHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { isLocalPrimary = false },
                ) {
                    AndroidView(
                        factory = { remoteSurfaceView },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.setZOrderMediaOverlay(true)
                            engine?.setupRemoteVideo(
                                VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid),
                            )
                        },
                    )
                    VideoPreviewLabel(otherUserName)
                }
            } else if (remoteUid != null && localVideoMuted) {
                LocalVideoMutedTile(
                    modifier = secondaryPreviewModifier
                        .size(width = previewWidth, height = previewHeight)
                        .clickable { isLocalPrimary = true },
                    isPreview = true,
                )
            } else if (remoteUid != null) {
                Box(
                    modifier = secondaryPreviewModifier
                        .size(width = previewWidth, height = previewHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .semantics { contentDescription = "Your camera. Tap to switch views." }
                        .clickable { isLocalPrimary = true },
                ) {
                    AndroidView(
                        factory = { localSurfaceView },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.setZOrderMediaOverlay(true)
                            engine?.setupLocalVideo(
                                VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.VideoPreviewLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LocalVideoMutedTile(
    modifier: Modifier = Modifier,
    isPreview: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (isPreview) 14.dp else 0.dp),
        color = CallBackgroundTop,
        border = if (isPreview) BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = CallSubtitle)
            Spacer(Modifier.height(8.dp))
            Text("Camera off", color = CallSubtitle, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AudioCallBackdrop(
    name: String,
    photoUrl: String?,
    status: String,
    compact: Boolean = false,
) {
    CallWaitingBackdrop(
        name = name,
        photoUrl = photoUrl,
        status = status,
        isVideo = false,
        compact = compact,
    )
}

/** Avatar stage used for voice calls and for a video call whose camera is off while ringing. */
@Composable
private fun CallWaitingBackdrop(
    name: String,
    photoUrl: String?,
    status: String,
    isVideo: Boolean,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CallBackgroundTop, CallBackground))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 32.dp),
        ) {
            Box(
                modifier = Modifier.size(if (compact) 72.dp else 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!compact) {
                    AudioPresenceRings()
                }
                Surface(
                    modifier = Modifier.size(if (compact) 72.dp else 136.dp),
                    shape = CircleShape,
                    color = Color(0xFF6A7175),
                ) {
                    if (photoUrl.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(if (compact) 16.dp else 30.dp),
                            tint = Color(0xFFCFD4D6),
                        )
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "$name profile photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            if (compact) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (isVideo) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Your camera is off",
                    color = CallSubtitle.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AudioPresenceRings() {
    val transition = rememberInfiniteTransition(label = "audio_call_presence")
    val outerScale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200, easing = LinearEasing),
        ),
        label = "audio_call_outer_ring",
    )
    val innerScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
        ),
        label = "audio_call_inner_ring",
    )

    Surface(
        modifier = Modifier
            .size(164.dp)
            .scale(outerScale)
            .alpha((1.32f - outerScale).coerceIn(0f, 0.4f)),
        shape = CircleShape,
        color = CallAvatarRing.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, CallAvatarRing.copy(alpha = 0.35f)),
    ) {}
    Surface(
        modifier = Modifier
            .size(152.dp)
            .scale(innerScale)
            .alpha((1.2f - innerScale).coerceIn(0f, 0.3f)),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {}
}

/** WhatsApp-style header: back (minimize) on the left, name and status/timer centred. */
@Composable
private fun CallTopBar(
    name: String,
    subtitle: String,
    callType: CallType,
    showMinimize: Boolean,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 32.dp),
    ) {
        if (showMinimize) {
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.align(Alignment.TopStart).size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Minimize call and return to the app",
                    tint = Color.White,
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 56.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    tint = CallSubtitle,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    color = CallSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** WhatsApp-style rounded bottom bar of circular controls, with End on the right. */
@Composable
private fun CallControls(
    isAudioMuted: Boolean,
    isVideoMuted: Boolean,
    isSpeakerEnabled: Boolean,
    showVideoControls: Boolean,
    onToggleAudio: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        color = CallBar,
        shape = RoundedCornerShape(32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showVideoControls) {
                CallControlButton(
                    onClick = onSwitchCamera,
                    active = false,
                    icon = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                )
                CallControlButton(
                    onClick = onToggleVideo,
                    active = isVideoMuted,
                    icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    contentDescription = if (isVideoMuted) "Turn camera on" else "Turn camera off",
                )
            }
            CallControlButton(
                onClick = onToggleSpeaker,
                active = isSpeakerEnabled,
                icon = if (isSpeakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (isSpeakerEnabled) "Use earpiece" else "Use loud speaker",
            )
            CallControlButton(
                onClick = onToggleAudio,
                active = isAudioMuted,
                icon = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isAudioMuted) "Unmute microphone" else "Mute microphone",
            )
            FilledIconButton(
                onClick = onEndCall,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = CallEnd,
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(imageVector = Icons.Default.CallEnd, contentDescription = "End call")
            }
        }
    }
}

/** Circular WhatsApp-style toggle: translucent when off, solid white when the option is on. */
@Composable
private fun CallControlButton(
    onClick: () -> Unit,
    active: Boolean,
    icon: ImageVector,
    contentDescription: String,
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) CallButtonActive else CallButtonIdle,
            contentColor = if (active) CallButtonActiveInk else Color.White,
        ),
        modifier = Modifier.size(52.dp),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
