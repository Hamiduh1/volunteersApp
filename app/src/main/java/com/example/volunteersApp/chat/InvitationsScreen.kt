package com.example.volunteersApp.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InvitationsScreen(
    navController: NavController,
    viewModel: InvitationsViewModel = viewModel(),
    statusFilter: InvitationStatusFilter = InvitationStatusFilter.Pending,
    searchQuery: String = ""
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(event) {
        when (val currentEvent = event) {
            is InvitationEvent.NavigateToChat -> {
                currentEvent.successMessage?.let { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                runCatching {
                    navController.navigate("chat/${currentEvent.chatId}/${currentEvent.otherUserId}")
                }
                viewModel.onEventHandled()
            }

            is InvitationEvent.ShowToast -> {
                Toast.makeText(context, currentEvent.message, Toast.LENGTH_SHORT).show()
                viewModel.onEventHandled()
            }

            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is InvitationsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }

            is InvitationsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is InvitationsUiState.Success -> {
                val visible = remember(state.invitations, statusFilter, searchQuery) {
                    state.invitations.filterForSocialInbox(statusFilter, searchQuery)
                }

                if (visible.isEmpty()) {
                    EmptyInvitationsPlaceholder()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { "${it.source}-${it.senderId}" }) { invitation ->
                            ModernInvitationCard(
                                invitation = invitation,
                                showActions = normalizeInvitationStatus(invitation.status) == "pending",
                                onAccept = { viewModel.acceptInvitation(invitation) },
                                onDecline = { viewModel.declineInvitation(invitation) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernInvitationCard(
    invitation: Invitation,
    showActions: Boolean = true,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val timestampLabel = remember(invitation.timestamp) {
        formatInvitationTimestamp(invitation.timestamp)
    }
    val statusLabel = remember(invitation.status) {
        normalizeInvitationStatus(invitation.status)
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
    }

    val statusColors = invitationStatusColors(invitation.status)
    val sourceColors = invitationSourceColors(invitation.source)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SocialInboxSurface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ConversationAvatar(
                    imageUrl = invitation.senderProfileImageUrl,
                    isOnline = false,
                    isGroup = false,
                )

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = invitation.senderName.ifBlank {
                            invitation.inviterName.ifBlank { "Someone" }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Wants to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = SocialInboxMutedInk
                    )
                }

                if (timestampLabel.isNotBlank()) {
                    Text(
                        text = timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = SocialInboxMutedInk,
                        maxLines = 1
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvitationPill(
                    label = resolveSourceLabel(invitation.source),
                    icon = resolveSourceIcon(invitation.source),
                    containerColor = sourceColors.background,
                    contentColor = sourceColors.tint
                )
                InvitationPill(
                    label = statusLabel,
                    icon = if (showActions) Icons.Default.Mail else Icons.Default.Check,
                    containerColor = statusColors.background,
                    contentColor = statusColors.tint
                )
            }

            if (invitation.context.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialInboxNeutralChip
                ) {
                    Text(
                        text = invitation.context,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = SocialInboxMutedInk
                    )
                }
            }

            if (showActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Decline")
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Accept")
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitationPill(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun EmptyInvitationsPlaceholder(modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            color = InboxOrange.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = InboxOrange
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "No invitations found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "When someone wants to chat with you, their request will appear here.",
            style = MaterialTheme.typography.bodyLarge,
            color = SocialInboxMutedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

fun LazyListScope.appendInvitationInboxItems(
    uiState: InvitationsUiState,
    statusFilter: InvitationStatusFilter,
    searchQuery: String,
    viewModel: InvitationsViewModel,
    onRetryLoad: () -> Unit,
    onClearFilters: () -> Unit,
) {
    when (val state = uiState) {
        is InvitationsUiState.Loading -> item(key = "inbox_inv_loading") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Loading invitations...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SocialInboxMutedInk
                )
                SocialInboxSkeletonList(rowCount = 3)
            }
        }

        is InvitationsUiState.Error -> item(key = "inbox_inv_error") {
            SocialInboxErrorPanel(
                message = state.message,
                onRetry = onRetryLoad,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        is InvitationsUiState.Success -> {
            val visible = state.invitations.filterForSocialInbox(statusFilter, searchQuery)
            if (visible.isEmpty()) {
                val isFilteredEmpty = state.invitations.isNotEmpty() &&
                    (searchQuery.isNotBlank() || statusFilter != InvitationStatusFilter.All)
                if (isFilteredEmpty) {
                    item(key = "inbox_inv_filtered_empty") {
                        SocialInboxFilteredEmptyPanel(
                            headline = "No invitations match filters",
                            body = "Try another status tab, adjust your search, or reset filters.",
                            onClearFilter = onClearFilters,
                            clearLabel = "Clear filters",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item(key = "inbox_inv_empty") {
                        EmptyInvitationsPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp)
                        )
                    }
                }
            } else {
                items(visible, key = { "${it.source}-${it.senderId}" }) { invitation ->
                    ModernInvitationCard(
                        invitation = invitation,
                        showActions = normalizeInvitationStatus(invitation.status) == "pending",
                        onAccept = { viewModel.acceptInvitation(invitation) },
                        onDecline = { viewModel.declineInvitation(invitation) }
                    )
                }
            }
        }
    }
}

private fun formatInvitationTimestamp(raw: Any?): String {
    val timeMillis = when (raw) {
        is com.google.firebase.Timestamp -> raw.toDate().time
        is Date -> raw.time
        is Long -> raw
        is Int -> raw.toLong()
        is Double -> raw.toLong()
        else -> return ""
    }
    if (timeMillis <= 0L) return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timeMillis))
}

private fun resolveSourceLabel(source: String): String {
    return when (source) {
        "blind_date" -> "Blind Date"
        "legacy_chat" -> "Volunteers"
        "chat" -> "Community"
        else -> "Community"
    }
}

private fun resolveSourceIcon(source: String): ImageVector {
    return when (source) {
        "blind_date" -> Icons.Default.Favorite
        "legacy_chat" -> Icons.Default.Groups
        "chat" -> Icons.Default.ChatBubbleOutline
        else -> Icons.Default.Mail
    }
}
