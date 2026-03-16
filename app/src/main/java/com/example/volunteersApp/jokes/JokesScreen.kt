package com.example.volunteersApp.jokes

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


// This is a new data class to hold the navigation state
private sealed class JokesNavigationState {
    data object List : JokesNavigationState()
    data object Post : JokesNavigationState()
    data class Profile(val authorId: String) : JokesNavigationState()
    data object Search : JokesNavigationState() // New state for searching
    data class Feed(val authorId: String) : JokesNavigationState() // New state for the feed

}

/**
 * Main feature screen for the Community Hub. Manages navigation between list, post, and profile screens.
 */
@Composable
fun JokesFeatureScreen(viewModel: JokesViewModel = viewModel()) {
    // --- MODIFIED: Use a sealed class for navigation state ---
    var navigationState by remember { mutableStateOf<JokesNavigationState>(JokesNavigationState.List) }

    var editingJoke by remember { mutableStateOf<Joke?>(null) }
    var jokeToDelete by remember { mutableStateOf<Joke?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JokesEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }

                is JokesEvent.PostSuccess -> {
                    // When a post is successful, go back to the list
                    navigationState = JokesNavigationState.List
                    Toast.makeText(context, "Posted successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- MODIFIED: Navigation logic using a when block ---
    when (val state = navigationState) {
        is JokesNavigationState.List -> {
            JokesListScreen(
                viewModel = viewModel,
                onAddJokeClicked = { navigationState = JokesNavigationState.Post },
                onEditJokeClicked = { joke -> editingJoke = joke },
                onDeleteJokeClicked = { joke -> jokeToDelete = joke },
                onSearchClicked = { navigationState = JokesNavigationState.Search },
                onProfileClicked = { authorId ->
                    navigationState = JokesNavigationState.Profile(authorId)
                }
            )
        }

        is JokesNavigationState.Post -> {
            PostJokeScreen(
                viewModel = viewModel,
                onNavigateUp = { navigationState = JokesNavigationState.List }
            )
        }

        is JokesNavigationState.Profile -> {
            JokesUserProfileScreen(
                authorId = state.authorId,
                viewModel = viewModel,
                onNavigateUp = { navigationState = JokesNavigationState.List }
            )
        }
        is JokesNavigationState.Search -> {
            SearchScreen(
                viewModel = viewModel,
                onNavigateUp = { navigationState = JokesNavigationState.List },
                onProfileClicked = { authorId ->
                    navigationState = JokesNavigationState.Feed(authorId)
                }
            )
        }
        is JokesNavigationState.Feed -> {
            JokesFeedScreen(
                authorId = state.authorId,
                viewModel = viewModel,
                onNavigateUp = { navigationState = JokesNavigationState.List }
            )
        }

    }
    // Dialog for editing a joke
    editingJoke?.let { joke ->
        var updatedText by remember { mutableStateOf(joke.text) }
        AlertDialog(
            onDismissRequest = { editingJoke = null },
            title = { Text("Edit Your Post") },
            text = {
                OutlinedTextField(
                    value = updatedText,
                    onValueChange = { updatedText = it },
                    label = { Text("Update text content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateJoke(joke.id, updatedText)
                    editingJoke = null
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { editingJoke = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog for deleting a joke
    jokeToDelete?.let { joke ->
        AlertDialog(
            onDismissRequest = { jokeToDelete = null },
            title = { Text("Delete Post?") },
            text = { Text("Are you sure you want to permanently delete this post?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteJoke(joke.id)
                        jokeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { jokeToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Screen that displays the list of all jokes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JokesListScreen(
    viewModel: JokesViewModel,
    onAddJokeClicked: () -> Unit,
    onEditJokeClicked: (Joke) -> Unit,
    onDeleteJokeClicked: (Joke) -> Unit,
    onProfileClicked: (authorId: String) -> Unit, // New callback
    onSearchClicked: () -> Unit

) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Hub", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClicked) {
                        Icon(Icons.Default.Search, "Search Users")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddJokeClicked,
                icon = { Icon(Icons.Default.Add, "Create Post") },
                text = { Text("Create Post") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading && uiState.jokes.isEmpty()) {
                LoadingPlaceholder()
            } else if (uiState.jokes.isEmpty()) {
                EmptyJokesView(onAddJokeClicked = onAddJokeClicked)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.jokes, key = { it.id }) { joke ->
                        JokeCard(
                            joke = joke,
                            viewModel = viewModel,
                            onEditClicked = { onEditJokeClicked(joke) },
                            onDeleteClicked = { onDeleteJokeClicked(joke) },
                            onProfileClicked = { onProfileClicked(joke.authorId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen for creating a new joke/post.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostJokeScreen(
    viewModel: JokesViewModel,
    onNavigateUp: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf(JokeType.TEXT) }

    val uiState by viewModel.uiState.collectAsState()
    val isPosting = uiState.isPosting

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = JokeType.IMAGE
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = JokeType.VIDEO
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Post") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isPosting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What's on your mind?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                AnimatedVisibility(mediaUri != null) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        if (mediaType == JokeType.IMAGE) {
                            AsyncImage(
                                model = mediaUri,
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (mediaType == JokeType.VIDEO) {
                            // Show a placeholder for the video
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    "Video selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                        IconButton(onClick = { mediaUri = null }) {
                            Icon(Icons.Default.Cancel, "Remove media", tint = Color.White)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, "Add Image", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { videoPicker.launch("video/*") }) {
                        Icon(Icons.Default.Videocam, "Add Video", modifier = Modifier.size(32.dp))
                    }
                }
                Button(
                    onClick = { viewModel.postJoke(text, mediaUri, mediaType) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = text.isNotBlank() && !isPosting
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Post", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * A single card representing a joke or post in the list.
 */
@Composable
fun JokeCard(
    joke: Joke,
    viewModel: JokesViewModel,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onProfileClicked: () -> Unit // New callback
) {
    var showComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    val currentUserId = viewModel.getCurrentUserId()
    val isLiked = joke.likes.contains(currentUserId)

    LaunchedEffect(showComments) {
        if (showComments) {
            viewModel.getComments(joke.id) { updatedComments -> comments = updatedComments }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // Header
            Row(Modifier
                .fillMaxWidth()
                // --- MODIFIED: Make the entire header clickable ---
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // No ripple effect
                    onClick = onProfileClicked
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = joke.authorProfileUrl ?: R.drawable.default_profile_image,
                        contentDescription = "Author's profile picture",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = joke.authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = joke.timestamp?.let { formatTimestamp(it) } ?: "Just now", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                if (currentUserId == joke.authorId) {
                    Row {
                        IconButton(onClick = onEditClicked) { Icon(Icons.Default.EditNote, "Edit") }
                        IconButton(onClick = onDeleteClicked) { Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Content
            Text(text = joke.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
            joke.mediaUrl?.let {
                Spacer(Modifier.height(16.dp))
                MediaContent(
                    mediaType = joke.mediaType,
                    url = it)
            }

            // Action Bar
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                ActionButton(
                    text = "${joke.likesCount} Likes",
                    icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    isActive = isLiked,
                    onClick = { viewModel.likeJoke(joke.id) }
                )
                // Comment Button
                ActionButton(
                    text = "${joke.commentsCount} Comments",
                    icon = if (showComments) Icons.Filled.Comment else Icons.Outlined.Comment,
                    isActive = showComments,
                    onClick = { showComments = !showComments }
                )
            }

            // Comments Section
            AnimatedVisibility(
                visible = showComments,
                enter = fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                CommentsSection(
                    comments = comments,
                    commentText = commentText,
                    onCommentChanged = { commentText = it },
                    onPostComment = {
                        if (commentText.isNotBlank()) {
                            viewModel.postComment(joke.id, commentText)
                            commentText = "" // Clear input after posting
                        }
                    }
                )
            }
        }
    }
}

/**
 * Reusable button for the action bar (Like, Comment).
 */
@Composable
private fun ActionButton(text: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

@Composable
fun VideoPlayer(modifier: Modifier = Modifier, uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(16.dp)),
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
            }
        }
    )
}

/**
 * Displays media content based on its type (Image, Document, etc.).
 */
@Composable
fun MediaContent(mediaType: String,
                 url: String,
                 modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Spacer(Modifier.height(16.dp))
    when (runCatching { JokeType.valueOf(mediaType) }.getOrNull()) {
        JokeType.IMAGE -> AsyncImage(
            model = url,
            contentDescription = "Joke image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        JokeType.VIDEO -> {
            VideoPlayer(uri = Uri.parse(url))
        }
        JokeType.DOCUMENT -> OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, "Document")
                Spacer(Modifier.width(16.dp))
                Text("Shared Document", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open")
            }
        }
        else -> Unit // For TEXT or null type, do nothing
    }
}


// --- NEW COMPOSABLE: A full-screen view for the TikTok-style feed ---
@Composable
fun FullScreenJokeView(
    joke: Joke,
    viewModel: JokesViewModel
) {
    val currentUserId = viewModel.getCurrentUserId()
    var showComments by remember { mutableStateOf(false) }

    // --- NEW: Optimistic UI State ---
    // Use the initial values from the joke object, but allow them to be updated locally.
    var isLiked by remember(joke.id) { mutableStateOf(joke.likes.contains(currentUserId)) }
    var likesCount by remember(joke.id) { mutableIntStateOf(joke.likesCount) }
    var commentsCount by remember(joke.id) { mutableIntStateOf(joke.commentsCount) }

    // When the underlying joke data changes (from Firestore listener), reset the local state.
    LaunchedEffect(joke) {
        isLiked = joke.likes.contains(currentUserId)
        likesCount = joke.likesCount
        commentsCount = joke.commentsCount
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Media content in the background, filling the screen
        joke.mediaUrl?.let {
            MediaContent(
                mediaType = joke.mediaType,
                url = it,
                modifier = Modifier.fillMaxSize() // Make media fill the screen
            )
        }

        // Overlay with text and action buttons
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom // Align content to the bottom
        ) {
            // Author and Text content
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = joke.authorProfileUrl ?: R.drawable.default_profile_image,
                    contentDescription = "Author's profile picture",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = joke.authorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = joke.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))

            // Action buttons (Like, Comment) on the side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End, // Align to the right
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        // --- MODIFIED: Optimistic Update ---
                        isLiked = !isLiked // Instantly toggle the UI
                        likesCount += if (isLiked) 1 else -1 // Instantly update the count
                        viewModel.likeJoke(joke.id) // Perform the backend action
                    }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text("${joke.likesCount}", color = Color.White)
                }
                Spacer(Modifier.width(24.dp))
                // Comment Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { showComments = !showComments }) {
                        Icon(
                            imageVector = if (showComments) Icons.Filled.Comment else Icons.Outlined.Comment,
                            contentDescription = "Comment",
                            tint = if (showComments) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text("$commentsCount", color = Color.White)
                }
            }
        }
    }

    // When showComments is true, display the bottom sheet.
    if (showComments) {
        CommentsBottomSheet(
            jokeId = joke.id,
            viewModel = viewModel,
            onDismiss = { showComments = false }, // Hide the sheet on dismiss
            onCommentPosted = {
                // Instantly increment the local comment count
                commentsCount++
            }
        )
    }
}

/**
 * The section for displaying and posting comments.
 */
@Composable
fun CommentsSection(
    comments: List<Comment>,
    commentText: String,
    onCommentChanged: (String) -> Unit,
    onPostComment: () -> Unit
) {
    Column(Modifier.padding(top = 16.dp)) {
        OutlinedTextField(
            value = commentText,
            onValueChange = onCommentChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a comment...") },
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                IconButton(onClick = onPostComment, enabled = commentText.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Post Comment")
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        if (comments.isEmpty()) {
            Text(
                "No comments yet. Be the first!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        } else {
            comments.forEach { comment ->
                CommentItem(comment)
            }
        }
    }
}

/**
 * A single comment item in the comment list.
 */
@Composable
fun CommentItem(comment: Comment) {
    Row(Modifier.padding(vertical = 8.dp)) {
        AsyncImage(
            model = comment.authorProfileUrl ?: R.drawable.default_profile_image,
            contentDescription = "Commenter's profile picture",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(comment.authorName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A modal bottom sheet to display comments for a specific post.
 * Features an "optimistic UI" for posting new comments instantly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    jokeId: String,
    viewModel: JokesViewModel,
    onDismiss: () -> Unit,
    onCommentPosted: () -> Unit // Callback to update the count on the main screen
) {
    val sheetState = rememberModalBottomSheetState()
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val currentUser = Firebase.auth.currentUser // Get current user for optimistic update

    // Fetch initial comments for the given jokeId when the sheet is first shown
    LaunchedEffect(jokeId) {
        isLoading = true
        viewModel.getComments(jokeId) { fetchedComments ->
            comments = fetchedComments
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding() // Automatically handle keyboard overlap
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Give the sheet a reasonable max height to avoid covering the whole screen
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No comments yet. Be the first!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                // List of comments
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true // Show the newest comments at the bottom
                ) {
                    items(comments.reversed(), key = { it.id }) { comment ->
                        CommentItem(comment)
                    }
                }
            }

            // Input field for new comments, sticks to the bottom
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Add a comment...") },
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank() && currentUser != null) {
                                // --- OPTIMISTIC UI UPDATE ---
                                val tempCommentText = commentText
                                val tempCommentId = UUID.randomUUID().toString() // Create a temporary unique ID

                                // 1. Create a temporary comment object to show instantly
                                val optimisticComment = Comment(
                                    id = tempCommentId,
                                    authorId = currentUser.uid,
                                    authorName = currentUser.displayName ?: "You",
                                    authorProfileUrl = currentUser.photoUrl?.toString(),
                                    text = tempCommentText,
                                    timestamp = Timestamp.now() // Use current client time for immediate display
                                )

                                // 2. Instantly add the new comment to the local list
                                comments = comments + optimisticComment

                                // 3. Notify the parent screen to update its comment count
                                onCommentPosted()

                                // 4. Clear the input field
                                commentText = ""

                                // 5. Send the actual comment to the backend in the background
                                viewModel.postComment(jokeId, tempCommentText)
                            }
                        },
                        enabled = commentText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Post Comment")
                    }
                }
            )
        }
    }
}

/**
 * Generic loading spinner placeholder.
 */
@Composable
fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Placeholder view for when no jokes are available.
 */
@Composable
fun EmptyJokesView(onAddJokeClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SentimentSatisfied,
            contentDescription = null,
            modifier = Modifier.size(128.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        Text("No Posts Yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Be the first to share something with the community.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddJokeClicked) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Create Post")
        }
    }
}

/**
 * Utility function to format a Firebase Timestamp into a relative string like "5m ago".
 */
fun formatTimestamp(timestamp: Timestamp): String {
    val diff = Date().time - timestamp.toDate().time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JokesUserProfileScreen(authorId: String, viewModel: JokesViewModel, onNavigateUp: () -> Unit) {

    LaunchedEffect(authorId) {
        viewModel.fetchJokesByAuthor(authorId)
    }

    val uiState by viewModel.profileUiState.collectAsState()
    var editingJoke by remember { mutableStateOf<Joke?>(null) }
    var jokeToDelete by remember { mutableStateOf<Joke?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.author?.name ?: "User Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingPlaceholder()
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Profile Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AsyncImage(
                            model = uiState.author?.profileImageUrl ?: R.drawable.default_profile_image,
                            contentDescription = "Author's profile picture",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = uiState.author?.name ?: "Anonymous",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // You can add more user details here, like bio, etc.
                    }
                }

                // User's Jokes
                if (uiState.jokes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("This user hasn't posted anything yet.")
                        }
                    }
                } else {
                    items(uiState.jokes, key = { it.id }) {
                        JokeCard(
                            joke = it,
                            viewModel = viewModel,
                            onEditClicked = { editingJoke = it },
                            onDeleteClicked = { jokeToDelete = it },
                            onProfileClicked = { /* Already on the profile, do nothing */ }
                        )
                    }
                }
            }
        }

        // --- Re-usable dialogs from JokesFeatureScreen ---
        editingJoke?.let { joke ->
            var updatedText by remember { mutableStateOf(joke.text) }
            AlertDialog(
                onDismissRequest = { editingJoke = null },
                title = { Text("Edit Your Post") },
                text = {
                    OutlinedTextField(
                        value = updatedText,
                        onValueChange = { updatedText = it },
                        label = { Text("Update text content") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateJoke(joke.id, updatedText)
                        editingJoke = null
                    }) { Text("Update") }
                },
                dismissButton = {
                    TextButton(onClick = { editingJoke = null }) { Text("Cancel") }
                }
            )
        }

        jokeToDelete?.let { joke ->
            AlertDialog(
                onDismissRequest = { jokeToDelete = null },
                title = { Text("Delete Post?") },
                text = { Text("Are you sure you want to permanently delete this post?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteJoke(joke.id)
                            jokeToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { jokeToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: JokesViewModel,
    onNavigateUp: () -> Unit,
    onProfileClicked: (authorId: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Users") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchUsers(it) },
                label = { Text("Search by name...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            if (uiState.isSearching) {
                LoadingPlaceholder()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.searchResults, key = { it.uid }) { user ->
                        UserSearchResultItem(user = user, onClick = { onProfileClicked(user.uid) })
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchResultItem(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = user.profileImageUrl ?: R.drawable.default_profile_image,
                contentDescription = "Profile picture of ${user.name}",
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            user.name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JokesFeedScreen(authorId: String, viewModel: JokesViewModel, onNavigateUp: () -> Unit) {
    LaunchedEffect(authorId) {
        viewModel.fetchJokesByAuthor(authorId)
    }

    val uiState by viewModel.profileUiState.collectAsState()
    val pagerState = rememberPagerState { uiState.jokes.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.author?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black // Dark background for the feed
    ) { padding ->
        if (uiState.isLoading) {
            LoadingPlaceholder()
        } else if (uiState.jokes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This user has no posts yet.", color = Color.White)
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                FullScreenJokeView(
                    joke = uiState.jokes[it],
                    viewModel = viewModel
                )
            }
        }
    }
}
