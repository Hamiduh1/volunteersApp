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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.volunteersApp.wallet.WalletAccent
import com.example.volunteersApp.wallet.WalletAccentContainer
import com.example.volunteersApp.wallet.WalletActionGold
import com.example.volunteersApp.wallet.WalletActionGoldText
import com.example.volunteersApp.wallet.WalletBackground
import com.example.volunteersApp.wallet.WalletCardBorder
import com.example.volunteersApp.wallet.WalletSurface
import com.example.volunteersApp.wallet.WalletTextPrimary
import com.example.volunteersApp.wallet.WalletTextSecondary

/**
 * Live palette shared with iOS (`LivePalette` in LiveModernUI.swift). Change colors here and there
 * together. Danger actions (end stream, delete, block, failures, warnings) stay red via LiveStudioDanger.
 */
object LivePalette {
    val Indigo = Color(0xFF665CFA)
    val Teal = Color(0xFF14C7B8)
    val OnAir = Color(0xFFFF5478)
    val OnAirDeep = Color(0xFFB345F5)
    val Ink = Color(0xFF0D0F24)
    val Accent = Indigo
    val AccentGradient = Brush.horizontalGradient(listOf(Indigo, Teal))
    val OnAirGradient = Brush.horizontalGradient(listOf(OnAir, OnAirDeep))
    val HeroGradient = Brush.linearGradient(
        listOf(Color(0xFF121740), Color(0xFF382E8F), Color(0xFF0A6678))
    )
    val HeroGlow = Brush.radialGradient(listOf(Teal.copy(alpha = 0.40f), Color.Transparent))
    val StageBackdrop = Brush.verticalGradient(
        listOf(Ink, Color(0xFF17143D), Color(0xFF0A3345))
    )
    val AvatarPlaceholder = Brush.linearGradient(
        listOf(Indigo.copy(alpha = 0.90f), Teal.copy(alpha = 0.80f))
    )

    /** One pair is picked per stream so thumbnails stay stable between refreshes. */
    val ThumbnailPairs: List<List<Color>> = listOf(
        listOf(Color(0xFFFA7394), Color(0xFF7345DB)),
        listOf(Color(0xFFFCB04A), Color(0xFFED5C87)),
        listOf(Color(0xFF3378F5), Color(0xFF5C33BF)),
        listOf(Color(0xFF0FA68F), Color(0xFF1A548C)),
        listOf(Color(0xFF8C54F2), Color(0xFF293387)),
        listOf(Color(0xFF1F9EDB), Color(0xFF0D5C66)),
    )

    fun thumbnailPair(key: String): List<Color> =
        ThumbnailPairs[(key.hashCode() and Int.MAX_VALUE) % ThumbnailPairs.size]
}

/** Accent-gradient primary action (Go live, Watch replay, Watch live). */
@Composable
fun LiveAccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier
            .clip(shape)
            .background(
                if (enabled) LivePalette.AccentGradient else androidx.compose.ui.graphics.SolidColor(Color(0x1F767680)),
                shape,
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = LiveStudioMuted,
        ),
        content = content,
    )
}

/** High-contrast studio palette — not color-only for state (shape/icon/label preferred). */
val LiveStudioBackground = WalletBackground
val LiveStudioSurface = WalletSurface
val LiveStudioInk = WalletTextPrimary
val LiveStudioMuted = WalletTextSecondary
val LiveStudioAccent = LivePalette.Accent
val LiveStudioAccentSoft = LivePalette.Indigo.copy(alpha = 0.12f)
// On-air signal only. Danger actions must use LiveStudioDanger, which stays red.
val LiveStudioLiveMark = LivePalette.OnAir
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
                    brush = LivePalette.HeroGradient,
                    shape = RoundedCornerShape(22.dp),
                )
                .background(
                    brush = LivePalette.HeroGlow,
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
                    brush = LivePalette.HeroGradient,
                    shape = RoundedCornerShape(22.dp),
                )
                .background(
                    brush = LivePalette.HeroGlow,
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

/** YouTube-style "Create → Go live" entry on the Live feed. */
@Composable
fun LiveGoLiveBanner(
    onGoLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = LiveStudioSurface,
        border = BorderStroke(1.dp, LiveStudioBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LivePalette.Indigo.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = LivePalette.Indigo,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Go live",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LiveStudioInk,
                )
                Text(
                    "Broadcast to your community now",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiveStudioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LiveAccentButton(
                onClick = onGoLive,
                modifier = Modifier.height(40.dp),
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("Go live", fontWeight = FontWeight.Bold)
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
                color = if (subtitle == null && liveCount > 0) LivePalette.OnAir else LiveStudioMuted,
                fontWeight = if (subtitle == null && liveCount > 0) FontWeight.SemiBold else null,
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
            placeholder = { Text("Search titles, hosts or @handles") },
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
    // One scrolling chip row: accent-gradient selected chip (drawn via modifier), neutral grey idle chips.
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(0xFFF1F1F1),
        labelColor = LiveStudioInk,
        selectedContainerColor = Color.Transparent,
        selectedLabelColor = Color.White,
    )
    val chipShape = RoundedCornerShape(8.dp)
    fun selectedChipModifier(selected: Boolean): Modifier =
        if (selected) Modifier.background(LivePalette.AccentGradient, chipShape) else Modifier
    val noBorder = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = false,
        borderColor = Color.Transparent,
        selectedBorderColor = Color.Transparent,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveStudioFeed.entries.forEach { feed ->
            FilterChip(
                selected = selectedFeed == feed,
                onClick = { onFeedSelected(feed) },
                modifier = selectedChipModifier(selectedFeed == feed),
                label = {
                    Text(
                        when (feed) {
                            LiveStudioFeed.DISCOVER -> "Discover"
                            LiveStudioFeed.HOSTED -> "My streams"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                shape = RoundedCornerShape(8.dp),
                border = noBorder,
                colors = chipColors,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 24.dp)
                .background(LiveStudioBorder)
        )
        LiveStudioFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                modifier = selectedChipModifier(selectedFilter == filter),
                label = {
                    Text(
                        when (filter) {
                            LiveStudioFilter.ALL -> "All"
                            LiveStudioFilter.LIVE -> "Live now"
                            LiveStudioFilter.SCHEDULED -> "Upcoming"
                            LiveStudioFilter.ENDED -> "Past streams"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                shape = RoundedCornerShape(8.dp),
                border = noBorder,
                colors = chipColors,
            )
        }
    }
}

/** Circular host photo with an initial fallback, as on YouTube channel rows. */
@Composable
fun LiveHostAvatar(
    name: String,
    photoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LivePalette.AvatarPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** On-air gradient "LIVE" pill used on thumbnails and the live room header; grey once ended. */
@Composable
fun LiveRedBadge(text: String = "LIVE", modifier: Modifier = Modifier, ended: Boolean = false) {
    val pill = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .semantics { contentDescription = if (ended) "Stream ended" else "Live now" }
            .clip(pill)
            .background(
                if (ended) androidx.compose.ui.graphics.SolidColor(Color(0xFF6B7280)) else LivePalette.OnAirGradient,
                pill,
            ),
        shape = pill,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(7.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun LiveViewerCountLabel(count: Long, modifier: Modifier = Modifier, color: Color = LiveStudioMuted) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Visibility, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(
            text = formatCompactCount(count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** 950, 1.2K, 3.4M — YouTube-style compact counts. */
fun formatCompactCount(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 1_000_000L -> trimDecimal(safe / 1_000_000.0) + "M"
        safe >= 1_000L -> trimDecimal(safe / 1_000.0) + "K"
        else -> safe.toString()
    }
}

private fun trimDecimal(value: Double): String {
    val rounded = String.format(java.util.Locale.US, "%.1f", value)
    return rounded.removeSuffix(".0")
}

/** "5 minutes ago", "2 hours ago", "3 days ago". */
fun formatRelativeAgo(timeMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
    if (timeMs == null || timeMs <= 0L) return null
    val minutes = ((nowMs - timeMs).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        minutes < 60L * 24L -> (minutes / 60L).let { "$it hour${if (it == 1L) "" else "s"} ago" }
        minutes < 60L * 24L * 30L -> (minutes / (60L * 24L)).let { "$it day${if (it == 1L) "" else "s"} ago" }
        else -> (minutes / (60L * 24L * 30L)).let { "$it month${if (it == 1L) "" else "s"} ago" }
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
        color = if (isLive) LivePalette.OnAir.copy(alpha = 0.14f) else LiveStudioAccentSoft,
        border = if (isLive) null else BorderStroke(width = 1.5.dp, color = LiveStudioBorder),
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
                color = if (isLive) LivePalette.OnAir else LiveStudioMuted,
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
    onRecheck: (() -> Unit)? = null,
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
        if (!result.networkAvailable && onRecheck != null) {
            OutlinedButton(
                onClick = onRecheck,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Text("Check connection again", fontWeight = FontWeight.SemiBold)
            }
        }
        if (result.isReady) {
            LiveAccentButton(
                onClick = onGoLive,
                modifier = Modifier.fillMaxWidth(),
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
