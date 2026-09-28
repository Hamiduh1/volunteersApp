package com.example.volunteersApp.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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

private val CallCanvasStart = Color(0xFF07131F)
private val CallCanvasEnd = Color(0xFF123B4D)
private val CallCanvasGlow = Color(0xFF1B6680)
private val CallGlass = Color(0xE6162838)
private val CallControlIdle = Color(0xFF263D50)
private val CallControlActive = Color(0xFFBCEBFF)
private val CallEnd = Color(0xFFE55663)
private val CallPreviewLabel = Color(0xFFDBF5FF)

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CallCanvasStart, CallCanvasEnd))),
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
                if (isVideoCall) {
                    VideoCallTopBar(
                        durationSeconds = connectedSeconds.takeIf { uiState.remoteUid != null },
                        showMinimize = !uiState.isAwaitingAnswer,
                        onMinimize = onMinimize,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                } else {
                    CallHeader(
                        name = otherUserName,
                        status = uiState.statusLabel,
                        callType = callType,
                        durationSeconds = connectedSeconds.takeIf { uiState.remoteUid != null },
                        showMinimize = !uiState.isAwaitingAnswer,
                        onMinimize = onMinimize,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }

                if (!uiState.error.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 104.dp, start = 20.dp, end = 20.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
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

@Composable
private fun IncomingCallActions(
    callType: CallType,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        color = CallGlass,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = CallControlActive.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, CallControlActive.copy(alpha = 0.36f)),
                ) {
                    Icon(
                        imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = CallControlActive,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Incoming ${if (callType == CallType.VIDEO) "video" else "voice"} call",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Choose how you want to respond",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CallEnd.copy(alpha = 0.78f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Decline")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CallControlActive,
                        contentColor = CallCanvasStart,
                    ),
                ) {
                    Icon(
                        imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Answer")
                }
            }
        }
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
) {
    val context = LocalContext.current
    var localPreviewOffset by remember { mutableStateOf(Offset.Zero) }
    var hasPositionedLocalPreview by remember { mutableStateOf(false) }
    var isLocalPrimary by remember { mutableStateOf(false) }
    val previewWidth = if (compactLocalPreview) 72.dp else 116.dp
    val previewHeight = if (compactLocalPreview) 102.dp else 164.dp
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxOffsetX = remember(maxWidth) {
            with(density) { (maxWidth - previewWidth).coerceAtLeast(0.dp).toPx() }
        }
        val maxOffsetY = remember(maxHeight) {
            with(density) { (maxHeight - previewHeight).coerceAtLeast(0.dp).toPx() }
        }

        LaunchedEffect(maxOffsetX, maxOffsetY) {
            if (!hasPositionedLocalPreview && maxOffsetX > 0f && maxOffsetY > 0f) {
                val topInset = with(density) { 72.dp.toPx() }
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
        if (isLocalPrimary) {
            if (localVideoMuted) {
                LocalVideoMutedTile(
                    modifier = Modifier.fillMaxSize(),
                    isPreview = false,
                )
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
        } else {
            CallWaitingBackdrop(
                name = otherUserName,
                photoUrl = otherUserPhotoUrl,
                status = status,
                isVideo = true,
            )
        }
        }

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
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
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
                VideoPreviewLabel(if (compactLocalPreview) otherUserName else "$otherUserName - tap to switch")
            }
        } else if (localVideoMuted) {
            LocalVideoMutedTile(
                modifier = secondaryPreviewModifier
                    .size(width = previewWidth, height = previewHeight)
                    .clickable { if (remoteUid != null) isLocalPrimary = true },
                isPreview = true,
            )
        } else {
            Box(
                modifier = secondaryPreviewModifier
                    .size(width = previewWidth, height = previewHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .clickable { if (remoteUid != null) isLocalPrimary = true },
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
                VideoPreviewLabel(if (compactLocalPreview) "You" else "You - tap to switch")
            }
        }
        }
    }
}

@Composable
private fun BoxScope.VideoPreviewLabel(text: String) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(8.dp),
        color = CallGlass,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = CallPreviewLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LocalVideoMutedTile(
    modifier: Modifier = Modifier,
    isPreview: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = CallGlass,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Camera off", color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (isPreview) {
                Spacer(Modifier.height(3.dp))
                Text("Tap to switch", color = CallPreviewLabel, style = MaterialTheme.typography.labelSmall)
            }
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
            .background(
                Brush.radialGradient(
                    colors = listOf(CallCanvasGlow, CallCanvasStart),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 32.dp),
        ) {
            Box(
                modifier = Modifier.size(if (compact) 72.dp else 220.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!isVideo && !compact) {
                    AudioPresenceRings()
                }
                Surface(
                    modifier = Modifier.size(if (compact) 72.dp else 156.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                ) {
                    if (photoUrl.isNullOrBlank()) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.padding(if (compact) 18.dp else 48.dp),
                            tint = Color.White,
                        )
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            if (!compact) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(text = status, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodyMedium)
                if (isVideo) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Your preview is ready",
                        color = Color.White.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
            .size(184.dp)
            .scale(outerScale)
            .alpha((1.32f - outerScale).coerceIn(0f, 0.48f)),
        shape = CircleShape,
        color = CallControlActive.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, CallControlActive.copy(alpha = 0.38f)),
    ) {}
    Surface(
        modifier = Modifier
            .size(174.dp)
            .scale(innerScale)
            .alpha((1.2f - innerScale).coerceIn(0f, 0.36f)),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    ) {}
}

@Composable
private fun VideoCallTopBar(
    durationSeconds: Int?,
    showMinimize: Boolean,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, top = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val elapsedLabel = durationSeconds?.let(::formatElapsedMinutes).orEmpty()
        Text(
            text = elapsedLabel,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (showMinimize) {
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Minimize call and return to the app",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CallHeader(
    name: String,
    status: String,
    callType: CallType,
    durationSeconds: Int?,
    showMinimize: Boolean = false,
    onMinimize: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val callLabel = if (callType == CallType.VIDEO) "VIDEO CALL" else "VOICE CALL"
    Surface(
        modifier = modifier.padding(start = 18.dp, top = 18.dp, end = 148.dp),
        shape = RoundedCornerShape(20.dp),
        color = CallGlass,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = CallControlActive.copy(alpha = 0.13f),
            ) {
                Icon(
                    imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = CallControlActive,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = durationSeconds?.let(::formatDuration) ?: status,
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    modifier = Modifier.padding(top = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = CallControlActive.copy(alpha = 0.13f),
                ) {
                    Text(
                        text = callLabel,
                        color = CallControlActive,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (showMinimize) {
                FilledIconButton(
                    onClick = onMinimize,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CallControlIdle,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Minimize call and return to the app",
                    )
                }
            }
        }
    }
}

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
    var isExpanded by remember { mutableStateOf(false) }
    val menuLabel = if (isExpanded) "Hide call controls" else "Show call controls"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        color = CallGlass,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CONTROLS",
                        color = CallControlActive,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (showVideoControls) "Video call menu" else "Voice call menu",
                        color = Color.White.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = menuLabel,
                        tint = Color.White,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallControlButton(
                            onClick = onToggleAudio,
                            containerColor = if (isAudioMuted) CallControlActive else CallControlIdle,
                            contentColor = if (isAudioMuted) CallCanvasStart else Color.White,
                            icon = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isAudioMuted) "Unmute" else "Mute",
                            contentDescription = if (isAudioMuted) "Unmute microphone" else "Mute microphone",
                        )
                        CallControlButton(
                            onClick = onToggleSpeaker,
                            containerColor = if (isSpeakerEnabled) CallControlActive else CallControlIdle,
                            contentColor = if (isSpeakerEnabled) CallCanvasStart else Color.White,
                            icon = if (isSpeakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            label = if (isSpeakerEnabled) "Speaker" else "Earpiece",
                            contentDescription = if (isSpeakerEnabled) "Use earpiece" else "Use loud speaker",
                        )
                        if (showVideoControls) {
                            CallControlButton(
                                onClick = onToggleVideo,
                                containerColor = if (isVideoMuted) CallControlActive else CallControlIdle,
                                contentColor = if (isVideoMuted) CallCanvasStart else Color.White,
                                icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                label = if (isVideoMuted) "Camera on" else "Camera off",
                                contentDescription = if (isVideoMuted) "Turn camera on" else "Turn camera off",
                            )
                            CallControlButton(
                                onClick = onSwitchCamera,
                                containerColor = CallControlIdle,
                                contentColor = Color.White,
                                icon = Icons.Default.Cached,
                                label = "Flip",
                                contentDescription = "Switch camera",
                            )
                        }
                        CallControlButton(
                            onClick = onEndCall,
                            containerColor = CallEnd,
                            contentColor = Color.White,
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            contentDescription = "End call",
                        )
                    }
                }
            }

            if (!isExpanded) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallControlButton(
                        onClick = onToggleAudio,
                        containerColor = if (isAudioMuted) CallControlActive else CallControlIdle,
                        contentColor = if (isAudioMuted) CallCanvasStart else Color.White,
                        icon = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isAudioMuted) "Unmute" else "Mute",
                        contentDescription = if (isAudioMuted) "Unmute microphone" else "Mute microphone",
                    )
                    if (showVideoControls) {
                        CallControlButton(
                            onClick = onToggleVideo,
                            containerColor = if (isVideoMuted) CallControlActive else CallControlIdle,
                            contentColor = if (isVideoMuted) CallCanvasStart else Color.White,
                            icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            label = if (isVideoMuted) "Camera on" else "Camera off",
                            contentDescription = if (isVideoMuted) "Turn camera on" else "Turn camera off",
                        )
                    }
                    CallControlButton(
                        onClick = onEndCall,
                        containerColor = CallEnd,
                        contentColor = Color.White,
                        icon = Icons.Default.CallEnd,
                        label = "End",
                        contentDescription = "End call",
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            modifier = Modifier.size(54.dp),
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
        Spacer(Modifier.height(5.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatElapsedMinutes(totalSeconds: Int): String =
    "${(totalSeconds / 60).coerceAtLeast(0)} min"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
