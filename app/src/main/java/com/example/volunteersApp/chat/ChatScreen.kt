@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.volunteersApp.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    chatId: String,
    otherUserId: String,
    onNavigateUp: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory(chatId)),
) {
    MaterialTheme(colorScheme = SocialInboxA11yColorScheme) {
    val currentUserId = Firebase.auth.currentUser?.uid
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    var messageText by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<PendingChatAttachment?>(null) }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var previewTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var menuTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }

    var otherUserName by remember { mutableStateOf("Conversation") }
    var otherUserSubtitle by remember { mutableStateOf("Loading...") }
    var otherUserUsername by remember { mutableStateOf("") }
    var otherUserProfileUrl by remember { mutableStateOf<String?>(null) }
    var isGroupChat by remember { mutableStateOf(false) }
    var groupMemberCount by remember { mutableIntStateOf(0) }
    var hasExitedGroup by remember { mutableStateOf(false) }
    var callTargetUserId by remember { mutableStateOf(otherUserId) }
    val activeCallForThisChat = uiState.activeCallSession?.takeIf { session ->
        shouldShowInChatActiveCallBanner(session, currentUserId)
    }

    LaunchedEffect(chatId, callTargetUserId) {
        viewModel.refreshConversationAvailability(callTargetUserId.takeIf { it.isNotBlank() })
    }

    fun openActiveCall(session: CallSession) {
        val sessionCallType = if (session.callType.equals("video", ignoreCase = true)) {
            CallType.VIDEO
        } else {
            CallType.AUDIO
        }
        val isSessionCaller = session.callerId == currentUserId
        val sessionOtherUserId = if (session.isGroupCall()) {
            null
        } else {
            listOf(session.callerId, session.receiverId)
                .firstOrNull { it.isNotBlank() && it != currentUserId }
        }
        context.startActivity(
            CallActivity.newIntent(
                context = context,
                chatId = session.chatId,
                otherUserId = sessionOtherUserId,
                callType = sessionCallType,
                isCaller = isSessionCaller,
                callId = session.id,
                autoAccept = !isSessionCaller,
            )
        )
    }

    fun launchCall(callType: CallType) {
        // Reopen or join the current session instead of creating a competing call for this chat.
        activeCallForThisChat?.let { session ->
            openActiveCall(session)
            return
        }
        if (!uiState.canPlaceCall) {
            Toast.makeText(
                context,
                uiState.callBlockReason ?: "Calls are unavailable in this conversation right now.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        context.startActivity(
            CallActivity.newIntent(
                context,
                chatId,
                callTargetUserId.takeIf { it.isNotBlank() },
                callType
            )
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingAttachment = PendingChatAttachment(
                uri = uri,
                type = CHAT_MESSAGE_TYPE_IMAGE,
                label = context.resolveAttachmentLabel(uri, "Photo"),
                sizeBytes = context.resolveAttachmentSize(uri),
                mimeType = context.contentResolver.getType(uri)
            )
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingAttachment = PendingChatAttachment(
                uri = uri,
                type = CHAT_MESSAGE_TYPE_VIDEO,
                label = context.resolveAttachmentLabel(uri, "Video"),
                sizeBytes = context.resolveAttachmentSize(uri),
                mimeType = context.contentResolver.getType(uri)
            )
        }
    }

    DisposableEffect(chatId, otherUserId, currentUserId) {
        var userListener: ListenerRegistration? = null
        val chatListener = Firebase.firestore.collection(FirestoreCollection.CHATS)
            .document(chatId)
            .addSnapshotListener { chatDoc, _ ->
                if (chatDoc == null || !chatDoc.exists()) return@addSnapshotListener

                val participants = (chatDoc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val exited = (chatDoc.get("exitedParticipantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val activeParticipants = participants.filterNot { it in exited }
                hasExitedGroup = !currentUserId.isNullOrBlank() && currentUserId in exited
                val hasGroupShape = chatDoc.getString("chatType") == "group" || activeParticipants.size > 2
                isGroupChat = hasGroupShape
                groupMemberCount = activeParticipants.size

                if (hasGroupShape) {
                    userListener?.remove()
                    userListener = null
                    otherUserName = chatDoc.getString("groupName").orEmpty().ifBlank { "Group chat" }
                    otherUserSubtitle = if (groupMemberCount > 0) {
                        "$groupMemberCount members"
                    } else {
                        "Group chat"
                    }
                    otherUserProfileUrl = chatDoc.getString("groupPhotoUrl")
                    otherUserUsername = ""
                    callTargetUserId = activeParticipants.firstOrNull { it != currentUserId }.orEmpty()
                    return@addSnapshotListener
                }

                val resolvedOtherId = activeParticipants.firstOrNull { it != currentUserId } ?: otherUserId
                callTargetUserId = resolvedOtherId
                userListener?.remove()
                userListener = Firebase.firestore.collection(FirestoreCollection.USERS)
                    .document(resolvedOtherId)
                    .addSnapshotListener { userDoc, _ ->
                        if (userDoc == null || !userDoc.exists()) return@addSnapshotListener
                        val fetchedName = userDoc.getString("name").orEmpty().trim()
                        val fetchedUsername = userDoc.getString("username").orEmpty().trim()
                        val fetchedEmail = userDoc.getString("email").orEmpty().trim()
                        otherUserName = resolveDisplayName(
                            name = fetchedName,
                            username = fetchedUsername,
                            email = fetchedEmail
                        )
                        otherUserUsername = fetchedUsername
                        otherUserSubtitle = formatPresenceSubtitle(userDoc.resolvePresence())
                        otherUserProfileUrl = userDoc.resolveProfileImageUrl()
                    }
            }

        onDispose {
            chatListener.remove()
            userListener?.remove()
        }
    }

    LaunchedEffect(uiState.messages.lastOrNull()?.id, uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(80)
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    var previousMessagesSnapshot by remember(chatId) { mutableStateOf<List<ChatMessage>?>(null) }
    LaunchedEffect(chatId, uiState.messages, uiState.isLoading) {
        val uid = currentUserId ?: return@LaunchedEffect
        val prev = previousMessagesSnapshot
        if (prev == null) {
            previousMessagesSnapshot = uiState.messages
            return@LaunchedEffect
        }
        val prevIds = prev.map { it.id }.toSet()
        val newFromOther = uiState.messages.any { m ->
            m.id !in prevIds && m.senderId != uid && !m.isDeleted
        }
        previousMessagesSnapshot = uiState.messages
        if (newFromOther && !uiState.isLoading) {
            ChatSoundEffects.playIncomingMessageBeep()
        }
    }

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Conversation") },
            text = { Text(uiState.error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("OK")
                }
            }
        )
    }

    val maxBubbleWidth = remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp >= 840 -> 460.dp
            configuration.screenWidthDp >= 600 -> 400.dp
            else -> (configuration.screenWidthDp * 0.76f).dp
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = otherUserProfileUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.default_profile_image)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = otherUserName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = WaInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = otherUserSubtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = WaMeta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { launchCall(CallType.VIDEO) },
                        enabled = uiState.canPlaceCall,
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video call")
                    }
                    IconButton(
                        onClick = { launchCall(CallType.AUDIO) },
                        enabled = uiState.canPlaceCall,
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Voice call")
                    }
                    Box {
                        IconButton(onClick = { showHeaderMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            if (!isGroupChat && otherUserUsername.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Copy username") },
                                    onClick = {
                                        showHeaderMenu = false
                                        Toast.makeText(
                                            context,
                                            "@${otherUserUsername.removePrefix("@")}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                            if (isGroupChat) {
                                DropdownMenuItem(
                                    text = { Text("Leave Group") },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.leaveGroup { success, message ->
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            if (success) onNavigateUp()
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SocialInboxSurface,
                    scrolledContainerColor = SocialInboxSurface,
                    navigationIconContentColor = WaIcon,
                    actionIconContentColor = WaIcon,
                )
            )
        },
        containerColor = WaWallpaper,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WaWallpaper)
        ) {
            activeCallForThisChat?.let { session ->
                ActiveCallBanner(
                    session = session,
                    onOpen = {
                        openActiveCall(session)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            when {
                uiState.isLoading && uiState.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.messages.isEmpty() -> {
                    EmptyConversationState(modifier = Modifier.weight(1f))
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(WaWallpaper),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { index, message ->
                                message.id.ifBlank { "${index}_${message.timestamp?.time ?: 0L}" }
                            }
                        ) { index, message ->
                            val dayLabel = message.timestamp?.let { formatMessageDay(it) }
                            val previousDay = uiState.messages.getOrNull(index - 1)?.timestamp?.let { formatMessageDay(it) }
                            if (dayLabel != null && dayLabel != previousDay) {
                                DayDivider(dayLabel)
                            }
                            ChatMessageBubble(
                                message = message,
                                isMine = message.senderId == currentUserId,
                                isGroupChat = isGroupChat,
                                currentUserId = currentUserId,
                                maxWidth = maxBubbleWidth,
                                onOpenMedia = { previewTarget = message },
                                onLongPress = { menuTarget = message }
                            )
                        }
                    }
                }
            }

            if (hasExitedGroup) {
                ExitedGroupNotice(modifier = Modifier.fillMaxWidth())
            } else if (!uiState.canSendMessages) {
                ConversationRestrictionNotice(
                    message = uiState.messagingBlockReason
                        ?: "Messaging is unavailable in this conversation right now.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                uiState.callPushWarning?.takeIf { it.isNotBlank() }?.let { warning ->
                    ConversationRestrictionNotice(
                        message = warning,
                        isError = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ConversationComposer(
                    value = messageText,
                    isSending = uiState.isSending,
                    pendingAttachment = pendingAttachment,
                    replyTarget = replyTarget,
                    onValueChange = { messageText = it },
                    onPickImage = { imagePicker.launch("image/*") },
                    onPickVideo = { videoPicker.launch("video/*") },
                    onClearAttachment = { pendingAttachment = null },
                    onClearReply = { replyTarget = null },
                    onSend = {
                        viewModel.sendMessage(
                            context = context,
                            messageText = messageText,
                            attachment = pendingAttachment,
                            replyTo = replyTarget
                        ) { success ->
                            if (success) {
                                messageText = ""
                                pendingAttachment = null
                                replyTarget = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (menuTarget != null) {
        ModalBottomSheet(
            onDismissRequest = { menuTarget = null }
        ) {
            val target = menuTarget!!
            if (!target.isDeleted) {
                SheetActionRow(
                    icon = Icons.Default.Reply,
                    label = "Reply"
                ) {
                    replyTarget = target
                    menuTarget = null
                }
                SheetActionRow(
                    icon = Icons.Default.Share,
                    label = "Share"
                ) {
                    val shareText = buildString {
                        append(resolveChatMessagePreview(target))
                        if (!target.mediaUrl.isNullOrBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(target.mediaUrl)
                        }
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            "Share message"
                        )
                    )
                    menuTarget = null
                }
            }
            if (target.senderId == currentUserId && !target.isDeleted) {
                SheetActionRow(
                    icon = Icons.Default.Delete,
                    label = "Delete Message",
                    tint = MaterialTheme.colorScheme.error
                ) {
                    viewModel.softDeleteMessage(target) { _, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                    menuTarget = null
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (previewTarget != null) {
        MediaPreviewDialog(
            message = previewTarget!!,
            isMine = previewTarget!!.senderId == currentUserId,
            onDismiss = { previewTarget = null },
            onDelete = {
                viewModel.softDeleteMessage(previewTarget!!) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) {
                        previewTarget = null
                    }
                }
            }
        )
    }
    }
}

private fun shouldShowInChatActiveCallBanner(session: CallSession, currentUserId: String?): Boolean {
    if (currentUserId.isNullOrBlank()) return false
    val participates = currentUserId == session.callerId ||
        currentUserId == session.receiverId ||
        currentUserId in session.participantIds
    if (!participates) return false
    if (currentUserId in session.endedParticipantIds || currentUserId in session.declinedParticipantIds) {
        return false
    }
    return session.status.equals("ringing", ignoreCase = true) ||
        session.status.equals("accepted", ignoreCase = true)
}

@Composable
private fun ConversationRestrictionNotice(
    message: String,
    isError: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) scheme.errorContainer else scheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            (if (isError) scheme.error else scheme.secondary).copy(alpha = 0.22f),
        ),
        tonalElevation = 1.dp
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActiveCallBanner(
    session: CallSession,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = session.callType.equals("video", ignoreCase = true)
    val statusLabel = when (session.status.lowercase(Locale.getDefault())) {
        "ringing" -> if (isVideo) "Video call ringing" else "Voice call ringing"
        "accepted" -> if (isVideo) "Video call in progress" else "Voice call in progress"
        else -> "Call active"
    }
    // WhatsApp-style green "return to call" strip.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = WaGreenDark,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = if (session.isGroupCall()) {
                        session.groupName.ifBlank { "Group conversation" }
                    } else {
                        session.callerName.ifBlank { "Open active call" }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(999.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = WaGreenDark,
                )
            ) {
                Text(if (session.status.equals("accepted", ignoreCase = true)) "Open" else "Join")
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isGroupChat: Boolean,
    currentUserId: String?,
    maxWidth: androidx.compose.ui.unit.Dp,
    onOpenMedia: () -> Unit,
    onLongPress: () -> Unit
) {
    // WhatsApp bubbles: small radius with a sharp "tail" corner at the top on the sender's side.
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    }
    val bubbleAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMine) WaOutgoingBubble else WaIncomingBubble
    val textColor = WaInk
    val metaColor = WaMeta
    val bubbleBorder = Color.Black.copy(alpha = 0.06f)

    val isRead = if (currentUserId.isNullOrBlank()) {
        message.isRead
    } else {
        message.isRead || message.readBy.any { it != currentUserId }
    }
    val isDelivered = if (currentUserId.isNullOrBlank()) {
        message.isRead
    } else {
        isRead || message.deliveredTo.any { it != currentUserId }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = bubbleAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(bubbleShape)
                .background(bubbleColor)
                .border(1.dp, bubbleBorder, bubbleShape)
                .combinedClickable(
                    onClick = {
                        if (!message.mediaUrl.isNullOrBlank()) {
                            onOpenMedia()
                        }
                    },
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                if (isGroupChat && !isMine) {
                    Text(
                        text = message.senderDisplayName.ifBlank { "Group member" },
                        style = MaterialTheme.typography.labelMedium,
                        color = WaGreenDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (!message.replyToText.isNullOrBlank()) {
                    // WhatsApp quoted reply: tinted block with a green bar on the leading edge.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isMine) WaQuoteMine else WaQuoteTheirs,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(WaQuoteBar)
                            )
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(
                                    text = message.replyToSenderName.orEmpty().ifBlank { "Reply" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WaGreenDark
                                )
                                Text(
                                    text = message.replyToText.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WaMeta,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (message.isDeleted) {
                    Text(
                        text = "Message deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.8f)
                    )
                } else {
                    when (message.messageType.uppercase(Locale.getDefault())) {
                        CHAT_MESSAGE_TYPE_IMAGE -> {
                            if (!message.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.default_profile_image)
                                )
                                if (message.messageText.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            } else {
                                Text(
                                    text = resolveChatMessagePreview(message).ifBlank { "Photo" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor.copy(alpha = 0.85f)
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        CHAT_MESSAGE_TYPE_VIDEO -> {
                            if (!message.mediaUrl.isNullOrBlank()) {
                                ChatExoVideoView(
                                    videoUrl = message.mediaUrl.orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    useController = false,
                                    repeatOne = true,
                                    mute = true
                                )
                                if (message.messageText.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            } else {
                                Text(
                                    text = resolveChatMessagePreview(message).ifBlank { "Video" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor.copy(alpha = 0.85f)
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    if (message.messageText.isNotBlank()) {
                        Text(
                            text = message.messageText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = message.timestamp?.let {
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
                        }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor
                    )
                    if (isMine) {
                        ReceiptIcon(read = isRead, delivered = isDelivered, tint = WaMeta)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitedGroupNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 1.dp
    ) {
        Text(
            text = "You left this group. You can read past messages, but sending and attachments are disabled.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = SocialInboxMutedInk,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConversationComposer(
    value: String,
    isSending: Boolean,
    pendingAttachment: PendingChatAttachment?,
    replyTarget: ChatMessage?,
    onValueChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearReply: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sendEnabled = !isSending && (value.isNotBlank() || pendingAttachment != null)

    // WhatsApp composer floats on the wallpaper: white pill input plus a round green send button.
    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = WaWallpaper,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (replyTarget != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SocialInboxSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, WaQuoteBar.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyTarget.senderDisplayName.ifBlank { "message" }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = resolveChatMessagePreview(replyTarget),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onClearReply) {
                            Icon(Icons.Default.Close, contentDescription = "Clear reply")
                        }
                    }
                }
            }

            if (pendingAttachment != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SocialInboxSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (pendingAttachment.type == CHAT_MESSAGE_TYPE_VIDEO) {
                                Icons.Default.Videocam
                            } else {
                                Icons.Default.Image
                            },
                            contentDescription = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pendingAttachment.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (pendingAttachment.type == CHAT_MESSAGE_TYPE_VIDEO) {
                                    "Video ready to send"
                                } else {
                                    "Photo will be normalized to JPEG"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onClearAttachment) {
                            Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .heightIn(max = 140.dp),
                    placeholder = {
                        Text("Message", style = MaterialTheme.typography.bodyLarge, color = WaMeta)
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = onPickVideo, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = "Add video",
                                    tint = WaIcon,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = onPickImage, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Add photo",
                                    tint = WaIcon,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = WaInk),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (sendEnabled) {
                                onSend()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SocialInboxSurface,
                        unfocusedContainerColor = SocialInboxSurface,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = WaGreen,
                    ),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp)
                )

                if (isSending) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(WaGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            if (sendEnabled) {
                                onSend()
                            }
                        },
                        enabled = sendEnabled,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = WaGreen,
                            disabledContainerColor = WaGreen.copy(alpha = 0.45f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.9f),
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptIcon(
    read: Boolean,
    delivered: Boolean,
    tint: Color
) {
    when {
        read -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = "Read",
            modifier = Modifier.size(16.dp),
            tint = WaReadTick
        )
        delivered -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = "Delivered",
            modifier = Modifier.size(16.dp),
            tint = tint.copy(alpha = 0.82f)
        )
        else -> Icon(
            imageVector = Icons.Default.Done,
            contentDescription = "Sent",
            modifier = Modifier.size(16.dp),
            tint = tint.copy(alpha = 0.82f)
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(text = label, color = tint)
        }
    }
}

@Composable
private fun ChatExoVideoView(
    videoUrl: String,
    modifier: Modifier = Modifier,
    useController: Boolean,
    repeatOne: Boolean,
    mute: Boolean
) {
    AndroidView(
        factory = { ctx ->
            val player = ExoPlayer.Builder(ctx).build()
            PlayerView(ctx).apply {
                this.player = player
                this.useController = useController
                controllerShowTimeoutMs = if (useController) 2500 else 0
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                tag = player
            }
        },
        update = { view ->
            if (videoUrl.isBlank()) return@AndroidView
            val player = view.tag as ExoPlayer
            val newUri = Uri.parse(videoUrl)
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri != null && currentUri == newUri) return@AndroidView
            player.volume = if (mute) 0f else 1f
            player.setMediaItem(MediaItem.fromUri(newUri))
            player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.prepare()
            player.playWhenReady = true
        },
        onRelease = { view ->
            (view.tag as? ExoPlayer)?.release()
            view.tag = null
        },
        modifier = modifier
    )
}

@Composable
private fun MediaPreviewDialog(
    message: ChatMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black,
                    ) {
                        when (message.messageType.uppercase(Locale.getDefault())) {
                            CHAT_MESSAGE_TYPE_VIDEO -> {
                                val url = message.mediaUrl.orEmpty()
                                if (url.isNotBlank()) {
                                    ChatExoVideoView(
                                        videoUrl = url,
                                        modifier = Modifier.fillMaxSize(),
                                        useController = true,
                                        repeatOne = true,
                                        mute = false
                                    )
                                }
                            }

                            else -> {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(1f)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (isMine && !message.isDeleted) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete media",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = WaGreen.copy(alpha = 0.14f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = WaGreenDark,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = SocialInboxInk,
        )
        Text(
            text = "Say hello or share a photo to get the conversation started.",
            style = MaterialTheme.typography.bodyMedium,
            color = SocialInboxMutedInk,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.padding(vertical = 6.dp),
            color = SocialInboxSurface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 0.5.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = WaIcon
            )
        }
    }
}

private fun formatMessageDay(date: Date): String {
    val target = java.util.Calendar.getInstance().apply { time = date }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    fun java.util.Calendar.sameDayAs(other: java.util.Calendar) =
        get(java.util.Calendar.YEAR) == other.get(java.util.Calendar.YEAR) &&
            get(java.util.Calendar.DAY_OF_YEAR) == other.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        target.sameDayAs(today) -> "Today"
        target.sameDayAs(yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
    }
}

private fun resolveDisplayName(name: String, username: String, email: String): String {
    if (name.isNotBlank() && !name.contains("@")) return name
    if (username.isNotBlank()) return username.removePrefix("@")
    if (email.isNotBlank()) return email.substringBefore("@")
    if (name.isNotBlank()) return name
    return "Conversation"
}
