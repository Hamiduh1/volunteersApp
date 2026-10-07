package com.example.volunteersApp.jokes

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.models.User

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

@Composable
fun UserProfileScreen(
    authorId: String,
    viewModel: JokesViewModel,
    onNavigateUp: () -> Unit,
    onEditJokeClicked: (Joke) -> Unit = {},
    onDeleteJokeClicked: (Joke) -> Unit = {}
) {
    val context = LocalContext.current
    val mindLoomPrefs = remember {
        context.getSharedPreferences("mindloom_prefs", android.content.Context.MODE_PRIVATE)
    }
    val profileViewKey = "mindloom_profile_view_mode"

    // View mode toggle (persisted; iOS MindLoom profile parity)
    var viewMode by remember {
        mutableStateOf(
            if (mindLoomPrefs.getString(profileViewKey, "grid") == "feed") ViewMode.FEED else ViewMode.GRID
        )
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(authorId) {
        viewModel.fetchJokesByAuthor(authorId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val profileUiState by viewModel.profileUiState.collectAsState()
    val currentUserId = viewModel.getCurrentUserId()
    val isSelf = currentUserId == authorId

    val profileAuthor = profileUiState.author
    val userJokes = profileUiState.jokes
    val headerDisplayName = buildDisplayName(profileAuthor, userJokes.firstOrNull()?.authorName)
    val headerHandle = buildUserHandle(profileAuthor, authorId)

    var selectedThreadIndex by rememberSaveable { mutableIntStateOf(0) }

    // Edit and delete state
    var editingJoke by remember { mutableStateOf<Joke?>(null) }
    var jokeToDelete by remember { mutableStateOf<Joke?>(null) }

    Scaffold(
        containerColor = MindLoomCanvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (viewMode) {
                            ViewMode.FEED -> "Thread"
                            else -> if (isSelf) "My MindLoom" else "Profile"
                        },
                        color = MindLoomTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MindLoomTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val next = if (viewMode == ViewMode.GRID) ViewMode.FEED else ViewMode.GRID
                        viewMode = next
                        mindLoomPrefs.edit()
                            .putString(profileViewKey, if (next == ViewMode.FEED) "feed" else "grid")
                            .apply()
                    }) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                            tint = MindLoomTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MindLoomTopBarFill,
                    titleContentColor = MindLoomTextPrimary,
                    navigationIconContentColor = MindLoomTextPrimary,
                    actionIconContentColor = MindLoomTextPrimary,
                )
            )
        }
    ) { padding ->
        if (profileUiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MindLoomCanvas)
                    .padding(padding)
            ) {
                when (viewMode) {
                    ViewMode.GRID -> MindLoomProfilePostsGrid(
                        author = profileAuthor,
                        displayName = headerDisplayName,
                        handle = headerHandle,
                        isSelf = isSelf,
                        isFollowing = profileUiState.isFollowing,
                        followersCount = profileUiState.followersCount,
                        followingCount = profileUiState.followingCount,
                        likesCount = profileUiState.likesCount,
                        jokes = userJokes,
                        onFollowClick = {
                            viewModel.followUser(authorId) { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                viewModel.fetchJokesByAuthor(authorId)
                            }
                        },
                        onMessageClick = {
                            if (uiState.sentInvitationUserIds.contains(authorId)) {
                                Toast.makeText(context, "Invitation already sent.", Toast.LENGTH_SHORT).show()
                            } else {
                                profileUiState.author?.let { targetUser ->
                                    viewModel.sendChatInvitation(targetUser) { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        invitationAlreadySent = uiState.sentInvitationUserIds.contains(authorId),
                        onShareClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out ${profileUiState.author?.name ?: "this user"} on MindLoom"
                                )
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                            }.onFailure {
                                Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onJokeClick = { index ->
                            selectedThreadIndex = index
                            viewMode = ViewMode.FEED
                        },
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth()
                    )
                    ViewMode.FEED -> FeedView(
                        jokes = userJokes,
                        viewModel = viewModel,
                        uiState = uiState,
                        isSelf = isSelf,
                        initialPage = selectedThreadIndex,
                        onEditClicked = { joke -> editingJoke = joke },
                        onDeleteClicked = { joke -> jokeToDelete = joke },
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth()
                    )
                }
            }
        }

        // Edit Dialog
        editingJoke?.let { joke ->
            var updatedText by remember { mutableStateOf(joke.text) }
            AlertDialog(
                onDismissRequest = { editingJoke = null },
                title = { Text("Edit Your Post") },
                text = {
                    OutlinedTextField(
                        value = updatedText,
                        onValueChange = { updatedText = it.take(MINDLOOM_POST_TEXT_MAX_LENGTH) },
                        label = { Text("Update text content") },
                        supportingText = {
                            Text("${updatedText.length}/$MINDLOOM_POST_TEXT_MAX_LENGTH")
                        },
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
                        Toast.makeText(context, "Post updated!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingJoke = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Dialog
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
                            Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { jokeToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    author: User?,
    displayName: String,
    handle: String,
    isSelf: Boolean,
    isFollowing: Boolean,
    invitationAlreadySent: Boolean = false,
    followersCount: Long,
    followingCount: Long,
    likesCount: Long,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Profile Image
        AsyncImage(
            model = author?.profileImageUrl ?: R.drawable.default_profile_image,
            contentDescription = "Profile picture",
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MindLoomSurfaceMuted),
            contentScale = ContentScale.Crop
        )

        // Name
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MindLoomTextPrimary,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Handle shown below avatar (never expose raw email here)
        Text(
            text = "@$handle",
            style = MaterialTheme.typography.labelMedium,
            color = MindLoomTextMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp
        )

        // Stats Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileStat(label = "Following", value = followingCount)
            ProfileStat(label = "Followers", value = followersCount)
            ProfileStat(label = "Likes", value = likesCount)
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isSelf) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onFollowClick,
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) MindLoomAccentContainer else MindLoomAccent,
                            contentColor = if (isFollowing) MindLoomTextPrimary else Color.White,
                        )
                    ) {
                        Icon(
                            if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isFollowing) "Following" else "Follow",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onMessageClick,
                        enabled = !invitationAlreadySent,
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MindLoomAccent),
                        border = BorderStroke(1.dp, MindLoomOutline),
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (invitationAlreadySent) "Invited" else "Message",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onShareClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MindLoomAccent),
                border = BorderStroke(1.dp, MindLoomOutline),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Profile", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun isEmailLike(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    return value.contains("@")
}

private fun sanitizeHandle(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().lowercase()
        .replace(" ", "_")
        .replace(Regex("[^a-z0-9._]"), "")
        .trim('_')
    return cleaned.ifBlank { null }
}

private fun buildUserHandle(author: User?, authorId: String): String {
    val fromUsername = sanitizeHandle(author?.username)
    if (!fromUsername.isNullOrBlank()) return fromUsername

    val fromName = if (!isEmailLike(author?.name)) sanitizeHandle(author?.name) else null
    if (!fromName.isNullOrBlank()) return fromName

    return "user_${authorId.take(6).lowercase()}"
}

private fun buildDisplayName(author: User?, fallbackAuthorName: String?): String {
    val candidate = author?.name?.takeIf { !it.isNullOrBlank() && !isEmailLike(it) }
        ?: fallbackAuthorName?.takeIf { it.isNotBlank() && !isEmailLike(it) }
    return candidate ?: "MindLoom User"
}

@Composable
private fun ProfileStat(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            color = MindLoomTextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            fontSize = 15.sp
        )
        Text(
            label,
            color = MindLoomTextMeta,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MindLoomProfilePostsGrid(
    author: User?,
    displayName: String,
    handle: String,
    isSelf: Boolean,
    isFollowing: Boolean,
    invitationAlreadySent: Boolean = false,
    followersCount: Long,
    followingCount: Long,
    likesCount: Long,
    jokes: List<Joke>,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onShareClick: () -> Unit,
    onJokeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
    ) {
        item(span = { GridItemSpan(3) }, key = "profile_header") {
            Column(Modifier.fillMaxWidth()) {
                ProfileHeader(
                    author = author,
                    displayName = displayName,
                    handle = handle,
                    isSelf = isSelf,
                    isFollowing = isFollowing,
                    invitationAlreadySent = invitationAlreadySent,
                    followersCount = followersCount,
                    followingCount = followingCount,
                    likesCount = likesCount,
                    onFollowClick = onFollowClick,
                    onMessageClick = onMessageClick,
                    onShareClick = onShareClick
                )
                HorizontalDivider(
                    color = MindLoomDivider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        if (jokes.isEmpty()) {
            item(span = { GridItemSpan(3) }, key = "empty_posts") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No posts yet.",
                        color = MindLoomTextMeta,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(
                count = jokes.size,
                key = { jokes[it].mindLoomStableRowId() }
            ) { index ->
                ProfileGridCell(
                    joke = jokes[index],
                    onClick = { onJokeClick(index) }
                )
            }
        }
    }
}

@Composable
private fun ProfileGridCell(
    joke: Joke,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .background(MindLoomSurfaceMuted)
            .clickable(onClick = onClick)
    ) {
        when (runCatching { JokeType.valueOf(joke.mediaType) }.getOrNull()) {
            JokeType.IMAGE -> AsyncImage(
                model = joke.mediaUrl,
                contentDescription = "Post media",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            JokeType.VIDEO -> {
                AsyncImage(
                    model = joke.mediaUrl,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                    shape = CircleShape,
                    color = MindLoomSurface.copy(alpha = 0.94f),
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = MindLoomAccent,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            JokeType.LIVE_SESSION -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFEFEA)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LiveTv, null, tint = com.example.volunteersApp.streams.LivePalette.OnAir, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("LIVE", color = MindLoomTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            JokeType.LIVE_REPLAY -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE7F2FA)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayCircle, null, tint = MindLoomAccent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Replay", color = MindLoomTextPrimary, fontSize = 11.sp)
                    }
                }
            }
            JokeType.DOCUMENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF0F5F8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, null, tint = MindLoomAccent, modifier = Modifier.size(28.dp))
                }
            }
            else -> {
                Text(
                    text = joke.text.take(60),
                    color = MindLoomTextPrimary,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
            shape = RoundedCornerShape(8.dp),
            color = MindLoomSurface.copy(alpha = 0.94f),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MindLoomAccent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = joke.likesCount.toString(),
                    color = MindLoomTextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedView(
    jokes: List<Joke>,
    viewModel: JokesViewModel,
    uiState: JokesUiState,
    isSelf: Boolean,
    initialPage: Int,
    onEditClicked: (Joke) -> Unit,
    onDeleteClicked: (Joke) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeInitialPage = initialPage.coerceIn(0, jokes.lastIndex.coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { jokes.size }
    )

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        key = { index -> jokes[index].mindLoomStableRowId() }
    ) { page ->
        val joke = jokes[page]
        FullScreenJokeView(
            joke = joke,
            viewModel = viewModel,
            isFollowing = uiState.followingIds.contains(joke.authorId),
            savedJokeIds = uiState.savedJokeIds,
            onProfileClicked = { /* Already on profile */ },
            onEditClicked = { if (isSelf) onEditClicked(joke) },
            onDeleteClicked = { if (isSelf) onDeleteClicked(joke) },
            showAuthorIdentity = false,
            showViewPreviousPosts = false,
            showAvatarRail = false,
            showFollowRailAction = false
        )
    }
}

enum class ViewMode {
    GRID,
    FEED
}
