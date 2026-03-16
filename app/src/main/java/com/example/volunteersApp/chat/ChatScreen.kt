package com.example.volunteersApp.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernized Chat Screen.
 * Features a high-end Material 3 design, smooth auto-scrolling,
 * and interactive message bubbles with timestamps and read receipts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUserId: String,
    onNavigateUp: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory(chatId))
) {
    val currentUserId = Firebase.auth.currentUser?.uid
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Header State
    var otherUserName by remember { mutableStateOf("Chat") }
    var otherUserProfileUrl by remember { mutableStateOf<String?>(null) }

    // Fetch other user info
    LaunchedEffect(otherUserId) {
        try {
            val userDoc = Firebase.firestore.collection("users").document(otherUserId).get().await()
            otherUserName = userDoc.getString("name") ?: userDoc.getString("username") ?: "Chat"
            otherUserProfileUrl = userDoc.getString("profilePictureUrl")
        } catch (e: Exception) {
            // Fallback handled by AsyncImage's error parameter
        }
    }

    // Smooth auto-scroll to bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                // Scroll past the last item to ensure it's fully visible
                listState.animateScrollToItem(uiState.messages.size)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = otherUserProfileUrl,
                            contentDescription = "$otherUserName's profile picture",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.default_profile_image)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(otherUserName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            // In a real app, this status would come from the ViewModel
                            Text("Online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        // FIX: Restructure the layout to correctly handle keyboard insets.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding() // This pushes the content up when the keyboard appears.
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f) // The list now takes up all available space.
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    val isMe = message.senderId == currentUserId
                    MessageBubble(message = message, isMe = isMe)
                }
            }

            // The input area is now at the bottom of this column.
            ChatInputArea(
                value = messageText,
                onValueChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                }
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    val bubbleAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = bubbleAlignment
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp), // Max width for bubbles
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.messageText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val time = message.timestamp?.let {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
                    } ?: ""
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    // **UI Modernization**: Add read receipt icon for messages sent by the user
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Read",
                            modifier = Modifier.size(16.dp),
                            tint = if (message.isRead) MaterialTheme.colorScheme.secondary else textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // **UI Modernization**: Use OutlinedTextField for a more modern look
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                maxLines = 5,
                shape = RoundedCornerShape(28.dp)
            )

            Spacer(Modifier.width(8.dp))

            // **UI Modernization**: Use a smaller FAB that fits the text field height
            FloatingActionButton(
                onClick = onSend,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
