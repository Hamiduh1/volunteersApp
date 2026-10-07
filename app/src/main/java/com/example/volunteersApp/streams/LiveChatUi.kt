package com.example.volunteersApp.streams

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/** Matches the Firestore rule on `live_sessions/{id}/comments`. */
const val LIVE_COMMENT_MAX_CHARS = 500
private const val LIVE_COMMENT_COUNTER_FROM = 400

private val LiveChatOwnerPill = Color(0xFFFFD600)
private val LiveChatOwnerInk = Color(0xFF1F1F1F)
private val LiveChatFieldGrey = Color(0xFFF1F1F1)

/**
 * One YouTube live-chat line: small avatar, author name (the host gets the yellow owner pill),
 * message inline, and an overflow menu for reply / moderation.
 */
@Composable
fun LiveChatMessageRow(
    comment: LiveRoomComment,
    isHostAuthor: Boolean,
    modifier: Modifier = Modifier,
    timeLabel: String? = null,
    onReply: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onHideUser: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val authorName = comment.authorName.ifBlank { "Anonymous" }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LiveHostAvatar(name = authorName, photoUrl = comment.authorPhotoUrl, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            comment.replyToAuthorName?.let { parentName ->
                Text(
                    text = "↪ @$parentName: ${comment.replyToText.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiveStudioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = buildAnnotatedString {
                    if (!timeLabel.isNullOrBlank()) {
                        withStyle(SpanStyle(color = LiveStudioMuted, fontSize = 12.sp)) { append("$timeLabel  ") }
                    }
                    if (isHostAuthor) {
                        withStyle(SpanStyle(color = LivePalette.Indigo, fontWeight = FontWeight.SemiBold)) {
                            append(authorName)
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = LivePalette.Indigo,
                                background = LivePalette.Indigo.copy(alpha = 0.12f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            )
                        ) { append(" HOST ") }
                    } else {
                        withStyle(SpanStyle(color = LiveStudioMuted, fontWeight = FontWeight.Medium)) { append(authorName) }
                    }
                    append("  ")
                    withStyle(SpanStyle(color = LiveStudioInk)) { append(comment.text) }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (onReply != null || onDelete != null || onHideUser != null) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Message options for $authorName",
                        tint = LiveStudioMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onReply != null) {
                        DropdownMenuItem(
                            text = { Text("Reply") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                            onClick = { menuOpen = false; onReply() },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Remove message") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                    if (onHideUser != null) {
                        DropdownMenuItem(
                            text = { Text("Hide user on this stream", color = LiveStudioDanger) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = LiveStudioDanger) },
                            onClick = { menuOpen = false; onHideUser() },
                        )
                    }
                }
            }
        }
    }
}

/** YouTube-style chat composer: your avatar, a rounded grey "Chat…" field, send arrow and a length counter. */
@Composable
fun LiveChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    disabledPlaceholder: String = "Chat is paused",
    replyingTo: String? = null,
    onCancelReply: (() -> Unit)? = null,
) {
    val me = remember { Firebase.auth.currentUser }
    Column(modifier = modifier.fillMaxWidth()) {
        if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Replying to @$replyingTo",
                    style = MaterialTheme.typography.labelMedium,
                    color = LiveStudioAccent,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onCancelReply != null) {
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = LiveStudioMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveHostAvatar(
                name = me?.displayName.orEmpty().ifBlank { "You" },
                photoUrl = me?.photoUrl?.toString(),
                size = 30.dp,
            )
            Spacer(Modifier.width(10.dp))
            TextField(
                value = value,
                onValueChange = { onValueChange(it.take(LIVE_COMMENT_MAX_CHARS)) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                placeholder = {
                    Text(if (enabled) "Chat…" else disabledPlaceholder, color = LiveStudioMuted)
                },
                trailingIcon = {
                    IconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (enabled && value.isNotBlank()) LiveStudioAccent else LiveStudioMuted.copy(alpha = 0.5f),
                        )
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LiveChatFieldGrey,
                    unfocusedContainerColor = LiveChatFieldGrey,
                    disabledContainerColor = LiveChatFieldGrey.copy(alpha = 0.7f),
                    focusedTextColor = LiveStudioInk,
                    unfocusedTextColor = LiveStudioInk,
                    cursorColor = LiveStudioAccent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
        }
        if (value.length >= LIVE_COMMENT_COUNTER_FROM) {
            Text(
                "${value.length}/$LIVE_COMMENT_MAX_CHARS",
                style = MaterialTheme.typography.labelSmall,
                color = if (value.length >= LIVE_COMMENT_MAX_CHARS) LiveStudioDanger else LiveStudioMuted,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp, end = 8.dp),
            )
        }
    }
}

/** Round icon with a count underneath, as on the YouTube Shorts / live action rail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRailAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "railActionScale",
    )
    Column(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.42f),
            contentColor = Color.White,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(scale),
                )
            }
        }
        Text(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
            ),
            maxLines = 1,
        )
    }
}

/** Floating "jump to latest" pill shown when the reader has scrolled away from new messages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveJumpToLatestChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = LiveStudioInk,
        contentColor = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("New messages", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
