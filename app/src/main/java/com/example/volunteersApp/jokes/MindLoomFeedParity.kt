package com.example.volunteersApp.jokes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.filled.Favorite
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.volunteersApp.streams.LiveLaunchTarget
import com.example.volunteersApp.streams.LiveRepository
import com.example.volunteersApp.streams.LiveShareRouter
import kotlinx.coroutines.launch

internal val MindLoomAccent = Color(0xFF006EAF) // Accessible blue that does not rely on red/green contrast.
private val MindLoomLiked = Color(0xFF111111) // filled heart — shape + fill, not red-only
internal val MindLoomAccentContainer = Color(0xFFD9F0FF)
internal val MindLoomTopBarFill = Color(0xFFFFFFFF)
internal val MindLoomSurface = Color(0xFFFFFFFF)
internal val MindLoomSurfaceMuted = Color(0xFFEFF4F7)
internal val MindLoomCanvas = Color(0xFFF7FAFC)
internal val MindLoomTextPrimary = Color(0xFF0A0A0A)
internal val MindLoomTextSecondary = Color(0xFF262626)
internal val MindLoomTextMeta = Color(0xFF404040)
internal val MindLoomOutline = Color(0xFFC9D4DC)
internal val MindLoomDivider = Color(0xFFC9D4DC)

/** Back-compat aliases used by older MindLoom composables in this file. */
private val MindLoomAccentPink = MindLoomAccent
private val MindLoomAccentOrange = MindLoomTextMeta

private val MindLoomMediaAspectRatio = 4f / 5f

data class MindLoomCreatorRing(
    val authorId: String,
    val name: String,
    val profileUrl: String?,
)

@Composable
fun MindLoomStatusBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFE8F5E9),
        border = BorderStroke(3.dp, Color(0xFF1B5E20))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = Color(0xFF0A0A0A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MindLoomTodaySection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "MindLoom",
            color = MindLoomTextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Creators, posts, and live moments in one feed.",
            color = MindLoomTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun MindLoomStoriesRow(
    creators: List<MindLoomCreatorRing>,
    onCreateTap: () -> Unit,
    onCreatorTap: (MindLoomCreatorRing) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MindLoomSurface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable(onClick = onCreateTap)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(2.dp, Color.Black, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MindLoomSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create",
                        tint = MindLoomTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create",
                    color = MindLoomTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        items(creators, key = { it.authorId }) { c ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onCreatorTap(c) }
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(2.dp, Color.Black, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MindLoomSurfaceMuted)
                ) {
                    AsyncImage(
                        model = c.profileUrl ?: R.drawable.default_profile_image,
                        contentDescription = c.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = c.name,
                    color = MindLoomTextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MindLoomScopeSegmented(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MindLoomSurface)
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("For You" to 0, "Following" to 1).forEach { (label, index) ->
                val selected = selectedTab == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        color = if (selected) MindLoomTextPrimary else MindLoomTextMeta,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) MindLoomTextPrimary else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MindLoomDivider)
    }
}

@Composable
fun MindLoomComposerCta(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MindLoomSurface,
        border = BorderStroke(1.dp, MindLoomOutline)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MindLoomTextPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Start a post", color = MindLoomTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Photo, video, PDF, or text - same flow as Create.",
                    color = MindLoomTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MindLoomSystemTopBar(
    onBack: (() -> Unit)?,
    onCreate: () -> Unit,
    onSearch: () -> Unit,
    onLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MindLoomTopBarFill,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, MindLoomDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MindLoomTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = "MindLoom",
                modifier = Modifier.weight(1f),
                color = MindLoomTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCreate) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create",
                    tint = MindLoomTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MindLoomTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onLive) {
                Icon(
                    Icons.Filled.LiveTv,
                    contentDescription = "Go live",
                    tint = MindLoomTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun MindLoomFeedPostCard(
    joke: Joke,
    currentUserId: String?,
    savedJokeIds: Set<String>,
    viewModel: JokesViewModel,
    isFollowing: Boolean = false,
    invitationAlreadySent: Boolean = false,
    shouldAutoPlayVideo: Boolean = false,
    onProfileClick: () -> Unit,
    onOpenMediaImmersive: () -> Unit,
    onOpenComments: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var likeInFlight by remember(joke.mindLoomStableRowId()) { mutableStateOf(false) }
    var isLiked by remember(joke.mindLoomStableRowId()) { mutableStateOf(currentUserId != null && joke.likes.contains(currentUserId)) }
    var likesCount by remember(joke.mindLoomStableRowId()) { mutableStateOf(joke.likesCount) }
    var isSaved by remember(joke.mindLoomStableRowId()) { mutableStateOf(savedJokeIds.contains(joke.id)) }
    var optimisticFollowing by remember(joke.mindLoomStableRowId()) { mutableStateOf(isFollowing) }
    var inviteSent by remember(joke.mindLoomStableRowId()) { mutableStateOf(invitationAlreadySent) }
    var ownerMenuExpanded by remember { mutableStateOf(false) }
    val isOwner = currentUserId != null && currentUserId == joke.authorId.trim()
    val timestampText = joke.timestamp?.let(::formatTimestamp) ?: "Just now"
    val mediaType = joke.mindLoomMediaType()
    val hasMedia = !joke.mediaUrl.isNullOrBlank()
    val canOpenFullscreen = hasMedia ||
        mediaType == JokeType.LIVE_SESSION ||
        mediaType == JokeType.LIVE_REPLAY
    val context = LocalContext.current
    val handle = joke.mindLoomDerivedHandle()
    val displayName = joke.authorName.ifBlank { "Creator" }

    LaunchedEffect(joke, currentUserId) {
        isLiked = currentUserId != null && joke.likes.contains(currentUserId)
        likesCount = joke.likesCount
    }
    LaunchedEffect(savedJokeIds, joke.mindLoomStableRowId()) {
        isSaved = savedJokeIds.contains(joke.id)
    }
    LaunchedEffect(isFollowing, joke.mindLoomStableRowId()) {
        optimisticFollowing = isFollowing
    }
    LaunchedEffect(invitationAlreadySent, joke.mindLoomStableRowId()) {
        inviteSent = invitationAlreadySent
    }

    fun launchReplay(
        sessionId: String?,
        title: String = joke.authorName,
        shareAccessToken: String? = null,
    ) {
        val resolvedId = sessionId?.trim().orEmpty()
        if (resolvedId.isBlank()) {
            Toast.makeText(context, "Replay is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, com.example.volunteersApp.streams.LiveArchivePlayerActivity::class.java).apply {
            putExtra(com.example.volunteersApp.streams.LiveArchivePlayerActivity.EXTRA_SESSION_ID, resolvedId)
            putExtra(com.example.volunteersApp.streams.LiveArchivePlayerActivity.EXTRA_TITLE, title)
            shareAccessToken?.takeIf { it.isNotBlank() }?.let {
                putExtra(com.example.volunteersApp.streams.LiveArchivePlayerActivity.EXTRA_REPLAY_SHARE_TOKEN, it)
            }
        }
        context.startActivity(intent)
    }

    fun launchLive(target: LiveLaunchTarget?) {
        if (target == null) {
            Toast.makeText(context, "This live session link is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            runCatching { LiveShareRouter.launch(context, target) }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.localizedMessage ?: "Could not open live session.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    fun runLikeToggle() {
        if (currentUserId == null) {
            Toast.makeText(context, "Sign in to like posts.", Toast.LENGTH_SHORT).show()
            return
        }
        if (likeInFlight) return
        likeInFlight = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val wasLiked = isLiked
        isLiked = !wasLiked
        likesCount = (likesCount + if (wasLiked) -1 else 1).coerceAtLeast(0)
        viewModel.likeJoke(joke) { success ->
            likeInFlight = false
            if (!success) {
                isLiked = wasLiked
                likesCount = joke.likesCount
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MindLoomSurface,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MindLoomOutline.copy(alpha = 0.45f)),
    ) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header: avatar + username + handle · time + Follow / ···
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = joke.authorProfileUrl ?: R.drawable.default_profile_image,
                contentDescription = "$displayName profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, MindLoomOutline, CircleShape)
                    .clickable(onClick = onProfileClick)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onProfileClick)
            ) {
                Text(
                    displayName,
                    color = MindLoomTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Text(
                    "@$handle · $timestampText",
                    color = MindLoomTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isOwner) {
                Box {
                    Text(
                        text = "···",
                        color = MindLoomTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable { ownerMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = ownerMenuExpanded,
                        onDismissRequest = { ownerMenuExpanded = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Edit", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                ownerMenuExpanded = false
                                onEdit()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFC62828), fontWeight = FontWeight.Bold) },
                            onClick = {
                                ownerMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            } else if (currentUserId != null) {
                MindLoomFollowPill(
                    following = optimisticFollowing,
                    onClick = {
                        val previous = optimisticFollowing
                        optimisticFollowing = !optimisticFollowing
                        viewModel.followUser(joke.authorId.trim()) { success, message ->
                            if (!success) {
                                optimisticFollowing = previous
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        when (mediaType) {
            JokeType.LIVE_SESSION -> {
                MindLoomLiveSessionPromoCard(
                    authorName = joke.authorName,
                    onWatchLive = { launchLive(joke.mindLoomLiveLaunchTarget()) }
                )
            }

            JokeType.LIVE_REPLAY -> {
                MindLoomLiveReplayCard(
                    sessionId = joke.mindLoomLiveSessionId().orEmpty(),
                    shareAccessToken = joke.mindLoomReplayShareToken(),
                    onOpenFullScreen = {
                        launchReplay(
                            joke.mindLoomLiveSessionId(),
                            joke.authorName,
                            joke.mindLoomReplayShareToken(),
                        )
                    }
                )
            }

            else -> if (hasMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .aspectRatio(MindLoomMediaAspectRatio)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(joke.mindLoomStableRowId()) {
                            detectTapGestures(
                                onDoubleTap = { runLikeToggle() },
                                onTap = { onOpenMediaImmersive() }
                            )
                        }
                ) {
                    MindLoomFeedPostCardMedia(
                        joke = joke,
                        shouldAutoPlayVideo = shouldAutoPlayVideo
                    )
                }
            }
        }

        // Actions: Like / Comment / Share (left) · Save (right)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { runLikeToggle() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MindLoomLiked else MindLoomTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onOpenComments, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = MindLoomTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, joke.mindLoomShareText())
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Share post"))
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Share",
                        tint = MindLoomTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (!isOwner && currentUserId != null) {
                    Text(
                        text = if (inviteSent) "Invited" else "Invite",
                        color = if (inviteSent) MindLoomTextMeta else MindLoomAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                if (inviteSent) {
                                    Toast.makeText(context, "Invitation already sent.", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                val target = com.example.volunteersApp.models.User(
                                    uid = joke.authorId.trim(),
                                    name = joke.authorName,
                                    profileImageUrl = joke.authorProfileUrl
                                )
                                viewModel.sendChatInvitation(target) { success, message ->
                                    if (success) inviteSent = true
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (Firebase.auth.currentUser == null) {
                        Toast.makeText(context, "Sign in to save posts.", Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    viewModel.toggleSaveJoke(joke) { _, savedNow ->
                        isSaved = savedNow
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isSaved) "Unsave" else "Save",
                    tint = MindLoomTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Likes count
        Text(
            text = if (likesCount == 1) "1 like" else "$likesCount likes",
            color = MindLoomTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Caption: bold username + body
        if (joke.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MindLoomTextPrimary,
                            fontSize = 14.sp
                        )
                    ) {
                        append(displayName)
                    }
                    append("  ")
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Medium,
                            color = MindLoomTextSecondary,
                            fontSize = 14.sp
                        )
                    ) {
                        append(joke.text)
                    }
                },
                modifier = Modifier.padding(horizontal = 14.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
        }

        if (joke.commentsCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (joke.commentsCount == 1) {
                    "View all 1 comment"
                } else {
                    "View all ${joke.commentsCount} comments"
                },
                color = MindLoomTextMeta,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .clickable(onClick = onOpenComments)
            )
        }

        if (canOpenFullscreen) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (mediaType) {
                    JokeType.LIVE_SESSION -> "Open live"
                    JokeType.LIVE_REPLAY -> "Open full screen"
                    JokeType.DOCUMENT -> "Open document"
                    else -> "Open full screen"
                },
                color = MindLoomAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .clickable {
                        when (mediaType) {
                            JokeType.LIVE_SESSION -> launchLive(joke.mindLoomLiveLaunchTarget())
                            JokeType.LIVE_REPLAY -> launchReplay(joke.mindLoomLiveSessionId(), joke.authorName)
                            JokeType.DOCUMENT -> {
                                val url = joke.mediaUrl
                                if (!url.isNullOrBlank()) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }.onFailure {
                                        onOpenMediaImmersive()
                                    }
                                } else {
                                    onOpenMediaImmersive()
                                }
                            }
                            else -> onOpenMediaImmersive()
                        }
                    }
            )
        }

        Spacer(Modifier.height(12.dp))
    }
    }
}

@Composable
private fun MindLoomFollowPill(following: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        color = if (following) Color.Transparent else MindLoomAccent,
        border = BorderStroke(
            1.5.dp,
            if (following) MindLoomTextPrimary else MindLoomAccent
        ),
        shape = shape
    ) {
        Text(
            text = if (following) "Following" else "Follow",
            color = if (following) MindLoomTextPrimary else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun MindLoomFeedPostCardMedia(joke: Joke, shouldAutoPlayVideo: Boolean) {
    val url = joke.mediaUrl
    if (url.isNullOrBlank()) {
        Box(Modifier.fillMaxSize().background(MindLoomSurfaceMuted))
        return
    }
    when (joke.mindLoomMediaType()) {
        JokeType.IMAGE -> MediaContent(
            mediaType = joke.mediaType,
            url = url,
            modifier = Modifier.fillMaxSize(),
            isFullScreen = false
        )

        JokeType.VIDEO -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A)),
                contentAlignment = Alignment.Center
            ) {
                // Only one in-feed decoder at a time — inactive rows stay placeholder-only
                // to avoid MediaCodec / ExoPlayer OOM when many videos are in the LazyColumn.
                if (shouldAutoPlayVideo) {
                    VideoPlayer(
                        modifier = Modifier.fillMaxSize(),
                        uri = Uri.parse(url),
                        isFullScreen = false,
                        fillContainer = true,
                        autoPlay = true,
                        showControls = false,
                        muted = true
                    )
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = "Video preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }
        }

        JokeType.DOCUMENT -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MindLoomSurfaceMuted)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = "Document",
                    tint = MindLoomTextPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "PDF document",
                    color = MindLoomTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MindLoomSurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Post",
                    color = MindLoomTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MindLoomLiveSessionPromoCard(
    authorName: String,
    onWatchLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(MindLoomMediaAspectRatio),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF0A0A0A),
        border = BorderStroke(0.dp, Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.5.dp, Color.White)
            ) {
                Text(
                    text = "LIVE",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${authorName.ifBlank { "A creator" }} is live on MindLoom",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onWatchLive,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Watch Live", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MindLoomLiveReplayCard(
    sessionId: String,
    shareAccessToken: String? = null,
    onOpenFullScreen: () -> Unit = {},
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    autoPlay: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    var playbackUrl by remember(sessionId, shareAccessToken) { mutableStateOf<String?>(null) }
    var isLoading by remember(sessionId, shareAccessToken) { mutableStateOf(sessionId.isNotBlank()) }
    var errorMessage by remember(sessionId, shareAccessToken) { mutableStateOf<String?>(null) }

    fun resolvePlayback(force: Boolean = false) {
        if (sessionId.isBlank()) {
            errorMessage = "Replay unavailable."
            isLoading = false
            return
        }
        if (!force && playbackUrl != null) return
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            runCatching {
                LiveRepository.createLiveReplayAccessLink(
                    sessionId = sessionId,
                    shareAccessToken = shareAccessToken,
                )
            }
                .onSuccess { url ->
                    playbackUrl = url
                    isLoading = false
                }
                .onFailure { error ->
                    errorMessage = error.localizedMessage ?: "Could not load replay."
                    isLoading = false
                }
        }
    }

    LaunchedEffect(sessionId, shareAccessToken) { resolvePlayback() }

    val surfaceModifier = if (isFullScreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(MindLoomMediaAspectRatio)
    }

    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF0A0A0A),
        border = null
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            !playbackUrl.isNullOrBlank() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isFullScreen) Modifier else Modifier.clickable(onClick = onOpenFullScreen)
                    )
            ) {
                // Feed preview: never spin up ExoPlayer — signed URL resolve stays, playback is fullscreen-only.
                if (isFullScreen || autoPlay) {
                    VideoPlayer(
                        modifier = Modifier.fillMaxSize(),
                        uri = Uri.parse(playbackUrl),
                        isFullScreen = isFullScreen,
                        fillContainer = true,
                        autoPlay = true,
                        showControls = isFullScreen,
                        muted = !isFullScreen
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Play replay",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Tap to play replay",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMessage ?: "Replay unavailable.",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { resolvePlayback(force = true) },
                    border = BorderStroke(1.5.dp, Color.White)
                ) {
                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Media-thread items only — never LIVE_SESSION / LIVE_REPLAY promo posts. */
fun Joke.mindLoomHasThreadMedia(): Boolean {
    val type = mindLoomMediaType()
    return !mediaUrl.isNullOrBlank() &&
        type in setOf(JokeType.IMAGE, JokeType.VIDEO, JokeType.DOCUMENT)
}

fun buildMindLoomCreatorsFromFeed(jokes: List<Joke>): List<MindLoomCreatorRing> {
    val seen = LinkedHashSet<String>()
    val out = ArrayList<MindLoomCreatorRing>()
    for (j in jokes) {
        val id = j.authorId.trim()
        if (id.isEmpty() || !seen.add(id)) continue
        out.add(
            MindLoomCreatorRing(
                authorId = id,
                name = j.authorName.trim().ifBlank { "User" },
                profileUrl = j.authorProfileUrl
            )
        )
    }
    return out
}
