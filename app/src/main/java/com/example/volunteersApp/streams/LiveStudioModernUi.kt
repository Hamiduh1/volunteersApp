@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.volunteersApp.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.wallet.WalletAccent
import com.example.volunteersApp.wallet.WalletAccentContainer
import com.example.volunteersApp.wallet.WalletActionGold
import com.example.volunteersApp.wallet.WalletActionGoldText
import com.example.volunteersApp.wallet.WalletBackground
import com.example.volunteersApp.wallet.WalletCardBorder
import com.example.volunteersApp.wallet.WalletSurface
import com.example.volunteersApp.wallet.WalletTextPrimary
import com.example.volunteersApp.wallet.WalletTextSecondary

/** High-contrast studio palette — not color-only for state (shape/icon/label preferred). */
val LiveStudioBackground = WalletBackground
val LiveStudioSurface = WalletSurface
val LiveStudioInk = WalletTextPrimary
val LiveStudioMuted = WalletTextSecondary
val LiveStudioAccent = WalletAccent
val LiveStudioAccentSoft = WalletAccentContainer
val LiveStudioLiveMark = Color(0xFFC62828)
val LiveStudioSuccess = Color(0xFF2E7D32)
val LiveStudioDanger = Color(0xFFB42318)
val LiveStudioBorder = WalletCardBorder
val LiveStudioHighlight = WalletActionGold.copy(alpha = 0.30f)
val LiveStudioHighlightInk = WalletActionGoldText
val LiveChromeScrim = WalletSurface.copy(alpha = 0.96f)

private val LiveStudioColorScheme = lightColorScheme(
    primary = LiveStudioAccent,
    onPrimary = LiveStudioSurface,
    primaryContainer = LiveStudioAccentSoft,
    onPrimaryContainer = LiveStudioInk,
    secondary = LiveStudioHighlightInk,
    onSecondary = LiveStudioSurface,
    secondaryContainer = LiveStudioHighlight,
    onSecondaryContainer = LiveStudioInk,
    tertiary = LiveStudioSuccess,
    onTertiary = LiveStudioSurface,
    background = LiveStudioBackground,
    onBackground = LiveStudioInk,
    surface = LiveStudioSurface,
    onSurface = LiveStudioInk,
    surfaceVariant = LiveStudioAccentSoft,
    onSurfaceVariant = LiveStudioInk,
    surfaceTint = LiveStudioAccent,
    outline = LiveStudioBorder,
    error = LiveStudioDanger,
    onError = LiveStudioSurface,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = LiveStudioDanger,
)

@Composable
fun LiveStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiveStudioColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
fun LiveGoLiveHeroCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7C1D6F),
                            LiveStudioLiveMark,
                            Color(0xFFE53935),
                        )
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Go live",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "Set access, stage rules, and MindLoom sharing before you broadcast.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
fun LiveStudioHeroCard(
    liveCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7C1D6F),
                            Color(0xFFB42318),
                            Color(0xFFE53935),
                        )
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Community broadcasts",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    if (liveCount > 0) "$liveCount live now" else "Live Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    if (liveCount > 0) {
                        "Tap a stream to watch, request a stage spot, or catch up on replays."
                    } else {
                        "No one is live right now. Pull to refresh or start your own broadcast."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
fun LiveStudioErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF4F3),
        border = BorderStroke(1.dp, Color(0xFFFFDAD6)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = LiveStudioDanger,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiveStudioInk,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveStudioAccent,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Retry", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = LiveStudioMuted,
                    ),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
fun LiveStudioHeader(
    liveCount: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Live Studio",
    subtitle: String? = null,
    showSearch: Boolean = true,
    showRefresh: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LiveStudioInk,
            )
            Text(
                text = subtitle ?: if (liveCount > 0) "$liveCount live now" else "No live broadcasts",
                style = MaterialTheme.typography.bodyMedium,
                color = LiveStudioMuted,
            )
        }
        if (showRefresh) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        if (showSearch) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
    }
}

@Composable
fun LiveStudioSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search streams or hosts") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LiveStudioSurface,
                unfocusedContainerColor = LiveStudioSurface,
            ),
        )
    }
}

@Composable
fun LiveStudioFilterRow(
    selectedFilter: LiveStudioFilter,
    selectedFeed: LiveStudioFeed,
    onFilterSelected: (LiveStudioFilter) -> Unit,
    onFeedSelected: (LiveStudioFeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = LiveStudioSurface,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiveStudioFeed.entries.forEach { feed ->
                    FilterChip(
                        selected = selectedFeed == feed,
                        onClick = { onFeedSelected(feed) },
                        label = {
                            Text(
                                when (feed) {
                                    LiveStudioFeed.DISCOVER -> "Discover"
                                    LiveStudioFeed.HOSTED -> "My streams"
                                }
                            )
                        },
                        border = if (selectedFeed == feed) {
                            FilterChipDefaults.filterChipBorder(
                                borderColor = LiveStudioAccent,
                                selectedBorderColor = LiveStudioAccent,
                                enabled = true,
                                selected = true,
                                disabledBorderColor = Color.Transparent,
                                disabledSelectedBorderColor = LiveStudioAccent,
                                borderWidth = 1.5.dp,
                                selectedBorderWidth = 1.5.dp,
                            )
                        } else {
                            FilterChipDefaults.filterChipBorder(
                                borderColor = Color(0xFFD0D0D5),
                                selectedBorderColor = LiveStudioAccent,
                                enabled = true,
                                selected = false,
                                disabledBorderColor = Color.Transparent,
                                disabledSelectedBorderColor = LiveStudioAccent,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LiveStudioAccentSoft,
                            selectedLabelColor = LiveStudioInk,
                        ),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiveStudioFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    LiveStudioFilter.ALL -> "All"
                                    LiveStudioFilter.LIVE -> "Live"
                                    LiveStudioFilter.SCHEDULED -> "Scheduled"
                                    LiveStudioFilter.ENDED -> "Ended"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LiveStudioAccentSoft,
                            selectedLabelColor = LiveStudioInk,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun LiveStatusPill(
    label: String,
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = if (isLive) "Status: live" else "Status: $label"
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = RoundedCornerShape(999.dp),
        color = if (isLive) LiveStudioSurface else LiveStudioAccentSoft,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isLive) LiveStudioLiveMark else LiveStudioBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isLive) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = LiveStudioLiveMark,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isLive) LiveStudioInk else LiveStudioMuted,
            )
        }
    }
}

@Composable
fun LiveBroadcastPrecheckSheetContent(
    result: LiveBroadcastPrecheck.Result,
    permissionsPermanentlyDenied: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onGoLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Broadcast precheck",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LiveStudioInk,
            )
            Text(
                "Camera, microphone, and network must be ready before you go live.",
                style = MaterialTheme.typography.bodyMedium,
                color = LiveStudioMuted,
            )
        }
        LivePrecheckStatusRow(
            label = "Camera",
            passed = result.cameraGranted,
            icon = Icons.Default.Videocam,
        )
        LivePrecheckStatusRow(
            label = "Microphone",
            passed = result.microphoneGranted,
            icon = Icons.Default.Mic,
        )
        LivePrecheckStatusRow(
            label = "Network",
            passed = result.networkAvailable,
            icon = Icons.Default.Wifi,
        )
        if (result.issues.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    result.issues.forEach { issue ->
                        Text(
                            issue,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        val hasMissingMediaPermission = !result.cameraGranted || !result.microphoneGranted
        if (hasMissingMediaPermission) {
            Button(
                onClick = if (permissionsPermanentlyDenied) onOpenSettings else onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveStudioAccent,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    if (permissionsPermanentlyDenied) "Open app settings" else "Grant camera and microphone",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (result.isReady) {
            Button(
                onClick = onGoLive,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveStudioLiveMark,
                    contentColor = Color.White,
                ),
            ) {
                Text("Go live", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LivePrecheckStatusRow(
    label: String,
    passed: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (passed) LiveStudioAccentSoft else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (passed) LiveStudioBorder else MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (passed) LiveStudioAccent else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Text(label, style = MaterialTheme.typography.bodyLarge, color = LiveStudioInk)
            }
            Text(
                if (passed) "Ready" else "Needs attention",
                color = if (passed) LiveStudioSuccess else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
