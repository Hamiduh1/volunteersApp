@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.streams

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun StartStreamScreen(
    viewModel: StartStreamViewModel,
    onBack: () -> Unit,
    onStreamStarted: (sessionId: String) -> Unit,
    offerShareChooserOnStart: Boolean = false,
    onResumeSession: ((sessionId: String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPrecheck by remember { mutableStateOf(false) }
    var precheckResult by remember { mutableStateOf(LiveBroadcastPrecheck.evaluate(context)) }
    var permissionRequestAttempted by remember { mutableStateOf(false) }
    var previewEnabled by remember {
        mutableStateOf(precheckResult.cameraGranted && precheckResult.microphoneGranted)
    }
    val previewController = remember { LiveCameraPreviewController() }
    val latestLoading by rememberUpdatedState(uiState.isLoading)

    val startLiveIfReady = {
        precheckResult = LiveBroadcastPrecheck.evaluate(context)
        if (precheckResult.isReady) {
            // Release the local-only preview before the room creates its publishing engine.
            previewController.stop()
            previewEnabled = false
            showPrecheck = false
            viewModel.startStream()
        } else {
            showPrecheck = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestAttempted = true
        precheckResult = LiveBroadcastPrecheck.evaluate(context)
        previewEnabled = precheckResult.cameraGranted && precheckResult.microphoneGranted
        if (precheckResult.isReady) {
            showPrecheck = false
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !latestLoading) {
                precheckResult = LiveBroadcastPrecheck.evaluate(context)
                previewEnabled = precheckResult.cameraGranted && precheckResult.microphoneGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewController.stop()
        }
    }

    LaunchedEffect(showPrecheck) {
        if (showPrecheck) {
            precheckResult = LiveBroadcastPrecheck.evaluate(context)
        }
    }

    LaunchedEffect(offerShareChooserOnStart) {
        if (!offerShareChooserOnStart) return@LaunchedEffect
        viewModel.events.collectLatest { event ->
            when (event) {
                is StartStreamEvent.Success -> {
                    onStreamStarted(event.sessionId)
                    event.mindLoomMessage?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Join my live stream!\n${event.shareLink}")
                        type = "text/plain"
                    }
                    if (shareIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(Intent.createChooser(shareIntent, "Share Live Link"))
                    } else {
                        Toast.makeText(context, "Could not find an app to share the link.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Go live releases the preview camera; bring it back only when the room was not created
    // (on success the room's own engine is starting and the preview must stay off).
    LaunchedEffect(uiState.isLoading, uiState.error, uiState.activeSessionId) {
        val failed = uiState.error != null || uiState.activeSessionId != null
        if (!uiState.isLoading && failed && !previewEnabled) {
            precheckResult = LiveBroadcastPrecheck.evaluate(context)
            previewEnabled = precheckResult.cameraGranted && precheckResult.microphoneGranted
        }
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!, duration = SnackbarDuration.Short)
                viewModel.resetError()
            }
        }
    }

    LiveStudioTheme {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = LiveStudioBackground,
        bottomBar = {
            Surface(color = LiveStudioSurface, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = when {
                            uiState.isLoading -> "Setting up your room…"
                            precheckResult.isReady -> "Everything looks good. You're ready to go live."
                            else -> "Finish the device check to go live."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (precheckResult.isReady || uiState.isLoading) LiveStudioMuted else LiveStudioDanger,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LiveAccentButton(
                        onClick = startLiveIfReady,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(50),
                        enabled = !uiState.isLoading,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Go live", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiveStudioHeader(
                liveCount = 0,
                onBack = onBack,
                onSearch = {},
                onRefresh = {},
                title = "Go Live",
                subtitle = "Configure your broadcast",
                showSearch = false,
                showRefresh = false,
                modifier = Modifier.padding(horizontal = 0.dp),
            )

            if (previewEnabled) {
                LiveCameraPreview(
                    controller = previewController,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Surface(
                    onClick = { showPrecheck = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(218.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF101A1E),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Camera preview is off", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap to allow camera and microphone",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            LivePrecheckChipsRow(
                result = precheckResult,
                onClick = { showPrecheck = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            val activeSessionId = uiState.activeSessionId
            if (activeSessionId != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = LiveStudioLiveMark.copy(alpha = 0.08f),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveRedBadge()
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "You're still live",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LiveStudioInk,
                            )
                        }
                        Text(
                            "\"${uiState.activeSessionTitle.orEmpty()}\" is still broadcasting. Return to it, or end it to start a new stream.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LiveStudioMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    previewController.stop()
                                    previewEnabled = false
                                    viewModel.dismissActiveSession()
                                    (onResumeSession ?: onStreamStarted)(activeSessionId)
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = LiveStudioInk, contentColor = Color.White),
                                enabled = !uiState.isLoading,
                            ) {
                                Text("Resume stream")
                            }
                            OutlinedButton(
                                onClick = viewModel::endActiveSession,
                                shape = RoundedCornerShape(50),
                                enabled = !uiState.isLoading,
                            ) {
                                Text("End it", color = LiveStudioDanger)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = LiveStudioSurface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Broadcast details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiveStudioInk,
                    )
                    Text(
                        text = "Give people a clear reason to join your room.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiveStudioMuted,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Stream Title") },
                        supportingText = { Text("${uiState.title.length}/120") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text("Description (optional)") },
                        supportingText = { Text("${uiState.description.length}/2000") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !uiState.isLoading
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Audience and stage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiveStudioInk,
                    )
                    Text(
                        text = "Choose who can watch, participate, and replay later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiveStudioMuted,
                    )
                    Spacer(Modifier.height(14.dp))
                    LiveEnumDropdown(
                        label = "Who can watch",
                        value = uiState.viewAccessMode.label,
                        options = LiveViewAccessMode.entries.map { it.label },
                        onSelect = { index -> viewModel.onViewAccessModeChange(LiveViewAccessMode.entries[index]) },
                        enabled = !uiState.isLoading
                    )
                    Spacer(Modifier.height(12.dp))
                    LiveEnumDropdown(
                        label = "Who can join stage",
                        value = uiState.stageAccessMode.label,
                        options = LiveStageAccessMode.entries.map { it.label },
                        onSelect = { index -> viewModel.onStageAccessModeChange(LiveStageAccessMode.entries[index]) },
                        enabled = !uiState.isLoading
                    )
                    Spacer(Modifier.height(12.dp))
                    LiveEnumDropdown(
                        label = "Replay visibility",
                        value = uiState.replayVisibility.label,
                        options = LiveReplayVisibility.entries.map { it.label },
                        onSelect = { index -> viewModel.onReplayVisibilityChange(LiveReplayVisibility.entries[index]) },
                        enabled = !uiState.isLoading
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiveStudioInk,
                    )
                    Text(
                        text = "Control notifications, MindLoom discovery, and conversation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiveStudioMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = LiveStudioAccentSoft.copy(alpha = 0.55f),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            LiveToggleRow("Notify followers", uiState.notifyFollowersOnStart, viewModel::onNotifyFollowersChange, uiState.isLoading)
                            LiveToggleRow("Post to MindLoom", uiState.postToMindLoomOnStart, viewModel::onPostToMindLoomChange, uiState.isLoading)
                            if (uiState.postToMindLoomOnStart && uiState.viewAccessMode != LiveViewAccessMode.PUBLIC) {
                                Text(
                                    "The MindLoom post won't include an invite. Only people allowed by \"${uiState.viewAccessMode.label}\" can join.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LiveStudioMuted,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            LiveToggleRow("Enable chat", uiState.chatEnabled, viewModel::onChatEnabledChange, uiState.isLoading)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showPrecheck) {
        val hostActivity = context as? Activity
        val hasMissingMediaPermission = !precheckResult.cameraGranted || !precheckResult.microphoneGranted
        val permissionsPermanentlyDenied = permissionRequestAttempted && hostActivity != null &&
            hasMissingMediaPermission &&
            !ActivityCompat.shouldShowRequestPermissionRationale(hostActivity, Manifest.permission.CAMERA) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(hostActivity, Manifest.permission.RECORD_AUDIO)
        ModalBottomSheet(
            onDismissRequest = { showPrecheck = false },
            containerColor = LiveStudioSurface,
        ) {
            LiveBroadcastPrecheckSheetContent(
                result = precheckResult,
                permissionsPermanentlyDenied = permissionsPermanentlyDenied,
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                        )
                    )
                },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                },
                onGoLive = startLiveIfReady,
                onRecheck = { precheckResult = LiveBroadcastPrecheck.evaluate(context) },
            )
        }
    }
    }
}

/** Inline pre-live checklist (camera, mic, network) shown under the preview, YouTube "Go live" style. */
@Composable
private fun LivePrecheckChipsRow(
    result: LiveBroadcastPrecheck.Result,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LivePrecheckChip("Camera", Icons.Default.Videocam, result.cameraGranted, onClick, Modifier.weight(1f))
        LivePrecheckChip("Mic", Icons.Default.Mic, result.microphoneGranted, onClick, Modifier.weight(1f))
        LivePrecheckChip("Network", Icons.Default.Wifi, result.networkAvailable, onClick, Modifier.weight(1f))
    }
}

@Composable
private fun LivePrecheckChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    passed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (passed) Color(0xFFF1F1F1) else LiveStudioDanger.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = LiveStudioInk, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = LiveStudioInk,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = if (passed) "$label ready" else "$label needs attention",
                tint = if (passed) LiveStudioSuccess else LiveStudioDanger,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveEnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private val LiveViewAccessMode.label: String
    get() = when (this) {
        LiveViewAccessMode.PUBLIC -> "Public"
        LiveViewAccessMode.FOLLOWERS_ONLY -> "Followers only"
        LiveViewAccessMode.INVITE_ONLY -> "Invite only"
        LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS -> "Accepted event volunteers"
    }

private val LiveStageAccessMode.label: String
    get() = when (this) {
        LiveStageAccessMode.HOST_ONLY -> "Host only"
        LiveStageAccessMode.REQUEST_TO_JOIN -> "Request to join"
        LiveStageAccessMode.APPROVED_VOLUNTEERS -> "Approved volunteers"
        LiveStageAccessMode.OPEN_TO_ACCEPTED_VOLUNTEERS -> "Open to accepted volunteers"
    }

internal val LiveReplayVisibility.label: String
    get() = when (this) {
        LiveReplayVisibility.OWNER_ONLY -> "Owner only"
        LiveReplayVisibility.SHARED_LINK -> "Anyone with link"
        LiveReplayVisibility.FOLLOWERS_ONLY -> "Followers only"
        LiveReplayVisibility.PUBLIC -> "Public"
    }
