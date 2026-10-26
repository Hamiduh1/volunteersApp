package com.example.volunteersApp.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    viewModel: CallHistoryViewModel = viewModel(),
    searchQuery: String = "",
    typeFilter: CallHistoryTypeFilter = CallHistoryTypeFilter.All,
    directionFilter: CallHistoryDirectionFilter = CallHistoryDirectionFilter.All,
    onOpenChat: (chatId: String, otherUserId: String) -> Unit = { _, _ -> },
) {
    MaterialTheme(colorScheme = SocialInboxA11yColorScheme) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SocialInboxBackground)
    ) {
        when (val currentState = state) {
            is CallHistoryState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }

            is CallHistoryState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is CallHistoryState.Success -> {
                val visible = remember(currentState.items, searchQuery, typeFilter, directionFilter) {
                    currentState.items.filterForSocialInbox(typeFilter, directionFilter, searchQuery)
                }

                if (visible.isEmpty()) {
                    EmptyCallHistory()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visible.groupConsecutiveCalls(), key = { "${it.head.call.chatId}:${it.head.call.id}" }) { group ->
                            DismissibleCallHistoryRow(
                                group = group,
                                onOpenChat = onOpenChat,
                                onHide = viewModel::hideCallHistoryItem,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleCallHistoryRow(
    group: CallHistoryRowGroup,
    onOpenChat: (chatId: String, otherUserId: String) -> Unit,
    onHide: (CallHistoryItem) -> Unit,
) {
    val head = group.head
    val canMessage = head.call.chatId.isNotBlank()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    group.items.forEach(onHide)
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (canMessage) onOpenChat(head.call.chatId, head.otherUserId)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = canMessage,
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WaGreenDark)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Text("Message", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WaRed)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove from history",
                        tint = Color.White,
                    )
                }
            }
        }
    ) {
        CallHistoryRow(
            group = group,
            onOpenChat = onOpenChat,
        )
    }
}

/** One redial at a time: a quick second tap must not start the same call twice. */
private object CallRedialGuard {
    private const val MIN_INTERVAL_MS = 2_000L
    private var lastLaunchAtMs = 0L

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAtMs < MIN_INTERVAL_MS) return false
        lastLaunchAtMs = now
        return true
    }
}

private fun formatCallRowTime(date: Date): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun Calendar.sameDayAs(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    return when {
        target.sameDayAs(now) -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        target.sameDayAs(yesterday) -> "Yesterday"
        target.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

@Composable
private fun CallHistoryRow(
    group: CallHistoryRowGroup,
    onOpenChat: (chatId: String, otherUserId: String) -> Unit,
) {
    val context = LocalContext.current
    val item = group.head
    val call = item.call
    val isMissedCall = item.direction == CallDirection.MISSED
    val directionTint = if (isMissedCall) WaRed else WaGreenDark
    val typeIcon = if (item.isVideo) Icons.Default.Videocam else Icons.Default.Call
    val directionIcon = when (item.direction) {
        CallDirection.DIALED -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.RECEIVED -> Icons.AutoMirrored.Filled.CallReceived
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
    }
    val directionLabel = when (item.direction) {
        CallDirection.DIALED -> "Outgoing"
        CallDirection.RECEIVED -> "Incoming"
        CallDirection.MISSED -> "Missed"
    }
    val timestampLabel = remember(call.startedAt, call.endedAt) {
        (call.startedAt ?: call.endedAt)?.let(::formatCallRowTime).orEmpty()
    }
    val detailLabel = remember(item.direction, call.durationSeconds) {
        if (!isMissedCall && call.durationSeconds > 0) {
            "$directionLabel · ${formatDuration(call.durationSeconds)}"
        } else {
            directionLabel
        }
    }
    val nameLabel = item.otherUserName.ifBlank { "Unknown" } +
        if (group.count > 1) " (${group.count})" else ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (call.chatId.isNotBlank()) {
                    Modifier.clickable {
                        onOpenChat(call.chatId, item.otherUserId)
                    }
                } else {
                    Modifier
                }
            ),
        color = SocialInboxSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationAvatar(
                imageUrl = if (item.isGroup) null else item.otherUserPhotoUrl,
                isOnline = false,
                isGroup = item.isGroup,
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = nameLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMissedCall) WaRed else WaInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (timestampLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timestampLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = WaMeta,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = directionTint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = detailLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = WaMeta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = {
                    if (!CallRedialGuard.tryAcquire()) return@IconButton
                    val callType = if (item.isVideo) CallType.VIDEO else CallType.AUDIO
                    context.startActivity(
                        CallActivity.newIntent(
                            context = context,
                            chatId = call.chatId,
                            otherUserId = if (item.isGroup) null else item.otherUserId.ifBlank { null },
                            callType = callType,
                        )
                    )
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = "Call back ${item.otherUserName.ifBlank { "contact" }}",
                    tint = WaGreenDark,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun CallBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = contentColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyCallHistory(modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            color = WaGreen.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = WaGreenDark
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No calls yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Dialed, received, and missed calls will appear here.",
            style = MaterialTheme.typography.bodyLarge,
            color = SocialInboxMutedInk,
            textAlign = TextAlign.Center
        )
    }
}

fun LazyListScope.appendCallHistoryInboxItems(
    state: CallHistoryState,
    searchQuery: String,
    typeFilter: CallHistoryTypeFilter,
    directionFilter: CallHistoryDirectionFilter,
    onRetryLoad: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenChat: (chatId: String, otherUserId: String) -> Unit,
    onHideCall: (CallHistoryItem) -> Unit,
) {
    when (val currentState = state) {
        is CallHistoryState.Loading -> item(key = "inbox_calls_loading") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Loading call history...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SocialInboxMutedInk
                )
                SocialInboxSkeletonList(rowCount = 3)
            }
        }

        is CallHistoryState.Error -> item(key = "inbox_calls_error") {
            SocialInboxErrorPanel(
                message = currentState.message,
                onRetry = onRetryLoad,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        is CallHistoryState.Success -> {
            val visible = currentState.items.filterForSocialInbox(typeFilter, directionFilter, searchQuery)
            if (visible.isEmpty()) {
                val isFilteredEmpty = currentState.items.isNotEmpty() &&
                    (
                        searchQuery.isNotBlank() ||
                            typeFilter != CallHistoryTypeFilter.All ||
                            directionFilter != CallHistoryDirectionFilter.All
                        )
                if (isFilteredEmpty) {
                    item(key = "inbox_calls_filtered_empty") {
                        SocialInboxFilteredEmptyPanel(
                            headline = "No calls match filters",
                            body = "Adjust type, direction, or search, or reset filters to see all calls.",
                            onClearFilter = onClearFilters,
                            clearLabel = "Clear filters",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item(key = "inbox_calls_empty") {
                        EmptyCallHistory(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp)
                        )
                    }
                }
            } else {
                items(visible.groupConsecutiveCalls(), key = { "${it.head.call.chatId}:${it.head.call.id}" }) { group ->
                    DismissibleCallHistoryRow(
                        group = group,
                        onOpenChat = onOpenChat,
                        onHide = onHideCall,
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
