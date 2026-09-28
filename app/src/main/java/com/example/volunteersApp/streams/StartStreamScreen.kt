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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Button
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

            LiveGoLiveHeroCard(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            if (previewEnabled) {
                LiveCameraPreview(
                    controller = previewController,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                            LiveToggleRow("Enable chat", uiState.chatEnabled, viewModel::onChatEnabledChange, uiState.isLoading)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = startLiveIfReady,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Go live", fontWeight = FontWeight.Bold)
                }
            }
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
            )
        }
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

private val LiveReplayVisibility.label: String
    get() = when (this) {
        LiveReplayVisibility.OWNER_ONLY -> "Owner only"
        LiveReplayVisibility.SHARED_LINK -> "Anyone with link"
        LiveReplayVisibility.FOLLOWERS_ONLY -> "Followers only"
        LiveReplayVisibility.PUBLIC -> "Public"
    }
