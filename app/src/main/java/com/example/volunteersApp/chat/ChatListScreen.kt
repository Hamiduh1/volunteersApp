@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.volunteersApp.chat

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = viewModel(),
    searchQuery: String = "",
    onConversationClick: (chatId: String, otherUserId: String) -> Unit,
    onAudioCall: (chatId: String, otherUserId: String) -> Unit,
    onVideoCall: (chatId: String, otherUserId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val groupContacts by viewModel.groupContacts.collectAsState()
    val event by viewModel.event.collectAsState()
    val isCreatingGroup by viewModel.isCreatingGroup.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(event) {
        when (val currentEvent = event) {
            is ChatListEvent.ShowToast -> {
                Toast.makeText(context, currentEvent.message, Toast.LENGTH_SHORT).show()
                viewModel.clearEvent()
            }

            is ChatListEvent.NavigateToChat -> {
                showCreateGroupDialog = false
                onConversationClick(currentEvent.chatId, currentEvent.otherUserId)
                viewModel.clearEvent()
            }

            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ChatListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }

            is ChatListUiState.Error -> {
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

            is ChatListUiState.Success -> {
                val visibleConversations = remember(state.conversations, searchQuery) {
                    state.conversations.filterForSocialInboxSearch(searchQuery)
                }

                if (visibleConversations.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        GroupQuickActionsCard(
                            onCreateGroup = { showCreateGroupDialog = true },
                            contactsReady = groupContacts.size,
                            groupThreadsInPlay = state.conversations.filter { it.isGroup }
                                .sumOf { it.groupMemberCount.coerceAtLeast(2) }
                                .coerceAtLeast(1),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            EmptyChatsPlaceholder()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            GroupQuickActionsCard(
                                onCreateGroup = { showCreateGroupDialog = true },
                                contactsReady = groupContacts.size,
                                groupThreadsInPlay = state.conversations.filter { it.isGroup }
                                    .sumOf { it.groupMemberCount.coerceAtLeast(2) }
                                    .coerceAtLeast(1),
                            )
                        }
                        items(visibleConversations, key = { it.chatId }) { conversation ->
                            ModernConversationItem(
                                conversation = conversation,
                                onClick = {
                                    onConversationClick(conversation.chatId, conversation.otherParticipantId)
                                },
                                onAudioCall = {
                                    onAudioCall(conversation.chatId, conversation.otherParticipantId)
                                },
                                onVideoCall = {
                                    onVideoCall(conversation.chatId, conversation.otherParticipantId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = groupContacts,
            isCreating = isCreatingGroup,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { groupName, selectedIds ->
                viewModel.createGroupChat(groupName, selectedIds)
            }
        )
    }
}

@Composable
fun ModernConversationItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val currentUserId = Firebase.auth.currentUser?.uid
    val dateLabel = remember(conversation.lastMessageTimestamp) {
        formatConversationDate(conversation.lastMessageTimestamp)
    }
    val preview = remember(conversation) {
        val base = buildConversationPreview(conversation)
        if (conversation.isGroup) {
            "${conversation.groupMemberCount} members · $base"
        } else {
            base
        }
    }
    val showReceipt = !conversation.isGroup &&
        !currentUserId.isNullOrBlank() &&
        conversation.lastMessageSenderId == currentUserId

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                imageUrl = conversation.otherParticipantProfilePicUrl,
                isOnline = !conversation.isGroup && conversation.isOtherUserOnline,
                isGroup = conversation.isGroup,
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
                        text = conversation.otherParticipantName.ifBlank { "Conversation" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (conversation.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (dateLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (conversation.isUnread) InboxBlue else SocialInboxMutedInk,
                            fontWeight = if (conversation.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (showReceipt) {
                        ConversationReceiptIcon(
                            delivered = conversation.lastMessageDelivered || conversation.isOtherUserOnline,
                            read = conversation.lastMessageRead,
                        )
                    }
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (conversation.isUnread) SocialInboxInk else SocialInboxMutedInk,
                        fontWeight = if (conversation.isUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.isUnread) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(InboxBlue),
                        )
                    }
                }
            }

            if (!conversation.isGroup) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onAudioCall, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Voice call",
                        tint = InboxTeal,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onVideoCall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video call",
                        tint = InboxIndigo,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineStatusBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = InboxGreen.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = InboxGreen
            ) {}
            Text(
                text = "Online",
                style = MaterialTheme.typography.labelSmall,
                color = InboxGreen
            )
        }
    }
}

@Composable
private fun ConversationReceiptIcon(
    delivered: Boolean,
    read: Boolean
) {
    when {
        read -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(16.dp),
                tint = InboxBlue
            )
        }
        delivered -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(16.dp),
                tint = SocialInboxMutedInk
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sent",
                modifier = Modifier.size(16.dp),
                tint = SocialInboxMutedInk
            )
        }
    }
}

@Composable
private fun GroupQuickActionsCard(
    onCreateGroup: () -> Unit,
    contactsReady: Int = 0,
    groupThreadsInPlay: Int = 0,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = InboxBlue.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = InboxBlue,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Start a group chat",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Coordinate with multiple people at once",
                    style = MaterialTheme.typography.bodySmall,
                    color = SocialInboxMutedInk,
                )
            }
            FilledTonalButton(
                onClick = onCreateGroup,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Create")
            }
        }
    }
}

@Composable
internal fun CreateGroupDialog(
    contacts: List<GroupContact>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Set<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedUserIds = remember { mutableStateListOf<String>() }

    Dialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Create Group Chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    singleLine = true,
                    label = { Text("Group name") },
                    placeholder = { Text("Weekend Helpers") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Add friends",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (contacts.isEmpty()) {
                    Text(
                        "No contacts available yet.",
                        color = SocialInboxMutedInk
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(contacts, key = { it.userId }) { contact ->
                            val selected = contact.userId in selectedUserIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (selected) {
                                            selectedUserIds.remove(contact.userId)
                                        } else {
                                            selectedUserIds.add(contact.userId)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = contact.profilePictureUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(SocialInboxSurfaceSoftAlt),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(id = R.drawable.ic_person_black_24dp),
                                    error = painterResource(id = R.drawable.ic_person_black_24dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = contact.displayName,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (contact.userId !in selectedUserIds) {
                                                selectedUserIds.add(contact.userId)
                                            }
                                        } else {
                                            selectedUserIds.remove(contact.userId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isCreating
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(groupName, selectedUserIds.toSet()) },
                        enabled = !isCreating
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Creating")
                        } else {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsPlaceholder(modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            color = InboxBlue.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ModeComment,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = InboxBlue
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "No conversations yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Start a new conversation to connect with volunteers and organizers.",
            style = MaterialTheme.typography.bodyLarge,
            color = SocialInboxMutedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun buildConversationCallNote(conversation: ChatConversation): String? {
    val typeLabel = when (conversation.lastCallType?.trim()?.lowercase(Locale.getDefault())) {
        "video" -> "Video"
        "audio" -> "Voice"
        else -> null
    }
    val statusLabel = conversation.lastCallStatus
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    if (typeLabel == null && statusLabel == null) return null

    return buildString {
        append("Last call")
        if (typeLabel != null) append(" - $typeLabel")
        if (statusLabel != null) append(" - $statusLabel")
    }
}

private fun buildConversationPreview(conversation: ChatConversation): String {
    if (conversation.lastMessageText.equals("Message deleted", ignoreCase = true)) {
        return "Message deleted"
    }

    val explicit = conversation.lastMessageText.trim()
    if (explicit.isNotBlank()) return explicit

    val legacyPreview = conversation.lastMessage.trim()
    if (legacyPreview.isNotBlank()) return legacyPreview

    return when (conversation.lastMessageType?.trim()?.uppercase(Locale.getDefault())) {
        CHAT_MESSAGE_TYPE_IMAGE -> "Photo"
        CHAT_MESSAGE_TYPE_VIDEO -> "Video"
        else -> if (conversation.isGroup) "Group chat is ready." else "Tap to start chatting."
    }
}

private fun formatConversationDate(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }

    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "h:mm a"
        sameYear -> "MMM d"
        else -> "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMs))
}

/**
 * Renders chat inbox rows inside a parent [LazyColumn] so the whole social inbox scrolls together.
 */
fun LazyListScope.appendChatInboxItems(
    uiState: ChatListUiState,
    searchQuery: String,
    onCreateGroupClick: () -> Unit,
    onConversationClick: (String, String) -> Unit,
    onAudioCall: (String, String) -> Unit,
    onVideoCall: (String, String) -> Unit,
    onRetryLoad: () -> Unit,
    onClearSearch: () -> Unit,
    groupContactsReady: Int = 0,
    groupSeatsInPlay: Int = 1,
) {
    when (val state = uiState) {
        is ChatListUiState.Loading -> item(key = "inbox_chats_loading") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Loading your conversations...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SocialInboxMutedInk
                )
                SocialInboxSkeletonList(rowCount = 3)
            }
        }

        is ChatListUiState.Error -> item(key = "inbox_chats_error") {
            SocialInboxErrorPanel(
                message = state.message,
                onRetry = onRetryLoad,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        is ChatListUiState.Success -> {
            val visible = state.conversations.filterForSocialInboxSearch(searchQuery)
            item(key = "inbox_group_card") {
                GroupQuickActionsCard(
                    onCreateGroup = onCreateGroupClick,
                    contactsReady = groupContactsReady,
                    groupThreadsInPlay = groupSeatsInPlay,
                )
            }
            if (visible.isEmpty()) {
                if (state.conversations.isNotEmpty() && searchQuery.isNotBlank()) {
                    item(key = "inbox_chats_filtered_empty") {
                        SocialInboxFilteredEmptyPanel(
                            headline = "No chats match your search",
                            body = "Try another name or keyword, or clear the search.",
                            onClearFilter = onClearSearch,
                            clearLabel = "Clear search",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item(key = "inbox_chats_empty") {
                        EmptyChatsPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp)
                        )
                    }
                }
            } else {
                items(visible, key = { it.chatId }) { conversation ->
                    ModernConversationItem(
                        conversation = conversation,
                        onClick = {
                            onConversationClick(conversation.chatId, conversation.otherParticipantId)
                        },
                        onAudioCall = {
                            onAudioCall(conversation.chatId, conversation.otherParticipantId)
                        },
                        onVideoCall = {
                            onVideoCall(conversation.chatId, conversation.otherParticipantId)
                        }
                    )
                }
            }
        }
    }
}
