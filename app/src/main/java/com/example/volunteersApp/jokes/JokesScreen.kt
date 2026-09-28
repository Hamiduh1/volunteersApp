package com.example.volunteersApp.jokes

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.models.User
import com.example.volunteersApp.streams.LiveArchivePlayerActivity
import com.example.volunteersApp.streams.LiveLaunchIntent
import com.example.volunteersApp.streams.LiveShareRouter
import com.example.volunteersApp.streams.StartStreamActivity
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch


// This is a new data class to hold the navigation state
private sealed class JokesNavigationState {
    data object List : JokesNavigationState()
    data object Post : JokesNavigationState()
    data class Profile(val authorId: String) : JokesNavigationState()
    data object Search : JokesNavigationState() // New state for searching
    data class Feed(val authorId: String) : JokesNavigationState() // New state for the feed

}

/** Space from window top so feed clears the single-row MindLoom header. */
private val MindLoomFeedTopPadding = 54.dp
/** In-card top overlay (media label row) below status + slim header. */
private val MindLoomFeedChipTopPadding = 48.dp
/** Compact immersive chrome inset below status bar. */
private val MindLoomImmersiveChipTopPadding = 10.dp
/** Reserve space so the right rail clears the bottom composer strip (stable, avoids layout jump). */
private val MindLoomBottomChromeReserve = 88.dp
private val MindLoomImmersiveBottomChromeReserve = 56.dp

/**
 * Main feature screen for the Community Hub. Manages navigation between list, post, and profile screens.
 */
@Composable
fun JokesFeatureScreen(
    viewModel: JokesViewModel = viewModel(),
    onMindLoomBack: (() -> Unit)? = null,
) {
    // --- MODIFIED: Use a sealed class for navigation state ---
    var navigationState by remember { mutableStateOf<JokesNavigationState>(JokesNavigationState.List) }
    var mindLoomFeedReturnTarget by remember { mutableStateOf<JokesNavigationState?>(null) }
    var mindLoomProfileReturnTarget by remember { mutableStateOf<JokesNavigationState?>(null) }

    var editingJoke by remember { mutableStateOf<Joke?>(null) }
    var jokeToDelete by remember { mutableStateOf<Joke?>(null) }
    val context = LocalContext.current
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JokesEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }

                is JokesEvent.PostSuccess -> {
                    mindLoomFeedReturnTarget = null
                    mindLoomProfileReturnTarget = null
                    navigationState = JokesNavigationState.List
                }
            }
        }
    }

    if (!errorMessage.isNullOrBlank()) {
        val err = errorMessage.orEmpty()
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // --- MODIFIED: Navigation logic using a when block ---
    when (val state = navigationState) {
        is JokesNavigationState.List -> {
            JokesHomeScreen(
                viewModel = viewModel,
                onAddJokeClicked = {
                    mindLoomFeedReturnTarget = null
                    mindLoomProfileReturnTarget = null
                    navigationState = JokesNavigationState.Post
                },
                onEditJokeClicked = { joke -> editingJoke = joke },
                onDeleteJokeClicked = { joke -> jokeToDelete = joke },
                onSearchClicked = {
                    mindLoomFeedReturnTarget = null
                    navigationState = JokesNavigationState.Search
                },
                onProfileClicked = { authorId ->
                    mindLoomProfileReturnTarget = null
                    navigationState = JokesNavigationState.Profile(authorId)
                },
                onGoLiveClicked = {
                    val intent = Intent(context, StartStreamActivity::class.java).apply {
                        putExtra(StartStreamActivity.EXTRA_SOURCE_TYPE, "mindloom")
                    }
                    context.startActivity(intent)
                },
                onMindLoomBack = onMindLoomBack,
            )
        }

        is JokesNavigationState.Post -> {
            PostJokeScreen(
                viewModel = viewModel,
                onNavigateUp = { navigationState = JokesNavigationState.List }
            )
        }

        is JokesNavigationState.Profile -> {
            UserProfileScreen(
                authorId = state.authorId,
                viewModel = viewModel,
                onNavigateUp = {
                    navigationState = mindLoomProfileReturnTarget ?: JokesNavigationState.List
                    mindLoomProfileReturnTarget = null
                },
                onEditJokeClicked = { joke -> editingJoke = joke },
                onDeleteJokeClicked = { joke -> jokeToDelete = joke }
            )
        }
        is JokesNavigationState.Search -> {
            SearchScreen(
                viewModel = viewModel,
                onNavigateUp = { navigationState = JokesNavigationState.List },
                onProfileClicked = { authorId ->
                    mindLoomFeedReturnTarget = JokesNavigationState.Search
                    navigationState = JokesNavigationState.Feed(authorId)
                }
            )
        }
        is JokesNavigationState.Feed -> {
            JokesFeedScreen(
                authorId = state.authorId,
                viewModel = viewModel,
                onNavigateUp = {
                    navigationState = mindLoomFeedReturnTarget ?: JokesNavigationState.List
                    mindLoomFeedReturnTarget = null
                },
                onOpenAuthorProfile = {
                    mindLoomProfileReturnTarget = JokesNavigationState.Feed(state.authorId)
                    navigationState = JokesNavigationState.Profile(state.authorId)
                }
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JokesHomeScreen(
    viewModel: JokesViewModel,
    onAddJokeClicked: () -> Unit,
    onEditJokeClicked: (Joke) -> Unit,
    onDeleteJokeClicked: (Joke) -> Unit,
    onProfileClicked: (authorId: String) -> Unit,
    onSearchClicked: () -> Unit,
    onGoLiveClicked: () -> Unit,
    onMindLoomBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = viewModel.getCurrentUserId()
    val mindLoomPrefs = remember {
        context.getSharedPreferences("mindloom_prefs", android.content.Context.MODE_PRIVATE)
    }
    val feedScopeKey = "mindloom_feed_scope_tab"
    var selectedTab by rememberSaveable {
        mutableIntStateOf(mindLoomPrefs.getInt(feedScopeKey, 0).coerceIn(0, 1))
    }
    LaunchedEffect(selectedTab) {
        mindLoomPrefs.edit().putInt(feedScopeKey, selectedTab).apply()
    }

    var pullRefreshing by remember { mutableStateOf(false) }
    var immersiveThread by remember { mutableStateOf<List<Joke>?>(null) }
    var immersiveStartIndex by remember { mutableIntStateOf(0) }
    var immersiveChromeVisible by remember { mutableStateOf(true) }
    var jokeForComments by remember { mutableStateOf<Joke?>(null) }

    val feedJokes = remember(uiState.jokes, uiState.followingIds, uiState.blockedUserIds, selectedTab, currentUserId) {
        val unblocked = uiState.jokes.filter { post ->
            post.authorId.trim().isNotEmpty() && post.authorId.trim() !in uiState.blockedUserIds
        }
        when (selectedTab) {
            1 -> unblocked.filter { post ->
                val aid = post.authorId.trim()
                uiState.followingIds.contains(aid) ||
                    (currentUserId != null && aid == currentUserId)
            }
            else -> unblocked
        }
    }

    val creators = remember(feedJokes) { buildMindLoomCreatorsFromFeed(feedJokes) }
    val listState = rememberLazyListState()
    val feedHeaderItemCount = 2
    var activeInlineVideoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState, feedJokes, immersiveThread) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                // Pause feed inline video while immersive player is open (avoid dual ExoPlayers).
                if (immersiveThread != null) {
                    activeInlineVideoId = null
                    return@collect
                }
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                val videoItems = layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val jokeIndex = info.index - feedHeaderItemCount
                    if (jokeIndex < 0 || jokeIndex >= feedJokes.size) return@mapNotNull null
                    val joke = feedJokes[jokeIndex]
                    if (joke.mindLoomMediaType() != JokeType.VIDEO) return@mapNotNull null
                    val visibleStart = maxOf(info.offset, viewportStart)
                    val visibleEnd = minOf(info.offset + info.size, viewportEnd)
                    val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
                    visiblePx to joke.mindLoomStableRowId()
                }
                activeInlineVideoId = videoItems.maxByOrNull { it.first }?.second
            }
    }

    // Keep immersive thread in sync with feed (delete / block / filter) so stale posts can't stay open.
    LaunchedEffect(feedJokes, immersiveThread) {
        val thread = immersiveThread ?: return@LaunchedEffect
        val byId = feedJokes.associateBy { it.mindLoomStableRowId() }
        val synced = thread.mapNotNull { byId[it.mindLoomStableRowId()] }
        when {
            synced.isEmpty() -> {
                immersiveThread = null
            }
            synced.size != thread.size ||
                synced.zip(thread).any { (fresh, old) ->
                    fresh.text != old.text ||
                        fresh.likesCount != old.likesCount ||
                        fresh.commentsCount != old.commentsCount ||
                        fresh.mediaUrl != old.mediaUrl
                } -> {
                val currentId = thread
                    .getOrNull(immersiveStartIndex.coerceIn(0, thread.lastIndex))
                    ?.mindLoomStableRowId()
                immersiveStartIndex = synced
                    .indexOfFirst { it.mindLoomStableRowId() == currentId }
                    .coerceAtLeast(0)
                immersiveThread = synced
            }
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) pullRefreshing = false
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNullOrBlank()) return@LaunchedEffect
        delay(3200)
        viewModel.clearStatusMessage()
    }

    BackHandler(enabled = immersiveThread != null || jokeForComments != null) {
        when {
            immersiveThread != null -> immersiveThread = null
            jokeForComments != null -> jokeForComments = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MindLoomCanvas)
    ) {
        Column(Modifier.fillMaxSize()) {
            MindLoomSystemTopBar(
                onBack = onMindLoomBack,
                onCreate = onAddJokeClicked,
                onSearch = onSearchClicked,
                onLive = onGoLiveClicked,
                modifier = Modifier.statusBarsPadding()
            )

            val bannerText = statusMessage
            if (!bannerText.isNullOrBlank()) {
                MindLoomStatusBanner(bannerText)
            }

            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = pullRefreshing),
                onRefresh = {
                    pullRefreshing = true
                    viewModel.requestMindLoomRefresh {
                        pullRefreshing = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                when {
                    uiState.isLoading && feedJokes.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            MindLoomSkeletonFeedView()
                        }
                    }

                    feedJokes.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (selectedTab == 1) {
                                EmptyJokesView(
                                    title = "Nothing here yet",
                                    subtitle = "Posts from people you follow (and your own) will show up here.",
                                    hint = "Try For You for the full feed, or Search to find creators.",
                                    primaryCtaLabel = "Browse For You",
                                    onPrimaryCta = { selectedTab = 0 }
                                )
                            } else {
                                EmptyJokesView()
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item(key = "ml_stories") {
                                MindLoomStoriesRow(
                                    creators = creators,
                                    onCreateTap = onAddJokeClicked,
                                    onCreatorTap = { onProfileClicked(it.authorId) }
                                )
                            }
                            item(key = "ml_scope") {
                                MindLoomScopeSegmented(
                                    selectedTab = selectedTab,
                                    onSelect = { selectedTab = it }
                                )
                            }
                            items(
                                items = feedJokes,
                                key = { it.mindLoomStableRowId() }
                            ) { joke ->
                                MindLoomFeedPostCard(
                                    joke = joke,
                                    currentUserId = currentUserId,
                                    savedJokeIds = uiState.savedJokeIds,
                                    viewModel = viewModel,
                                    isFollowing = uiState.followingIds.contains(joke.authorId.trim()),
                                    invitationAlreadySent = uiState.sentInvitationUserIds.contains(joke.authorId.trim()),
                                    shouldAutoPlayVideo = activeInlineVideoId == joke.mindLoomStableRowId(),
                                    onProfileClick = { onProfileClicked(joke.authorId) },
                                    onOpenMediaImmersive = {
                                        when (joke.mindLoomMediaType()) {
                                            JokeType.LIVE_SESSION -> {
                                                val target = joke.mindLoomLiveLaunchTarget()
                                                if (target == null) {
                                                    Toast.makeText(
                                                        context,
                                                        "This live session link is unavailable.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    coroutineScope.launch {
                                                        runCatching {
                                                            LiveShareRouter.launch(context, target)
                                                        }.onFailure { error ->
                                                            Toast.makeText(
                                                                context,
                                                                error.localizedMessage
                                                                    ?: "Could not open live session.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                }
                                                return@MindLoomFeedPostCard
                                            }

                                            JokeType.LIVE_REPLAY -> {
                                                val sessionId = joke.mindLoomLiveSessionId()
                                                if (sessionId.isNullOrBlank()) {
                                                    Toast.makeText(
                                                        context,
                                                        "Replay is unavailable.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    val intent = Intent(context, LiveArchivePlayerActivity::class.java).apply {
                                                        putExtra(LiveArchivePlayerActivity.EXTRA_SESSION_ID, sessionId)
                                                        putExtra(LiveArchivePlayerActivity.EXTRA_TITLE, joke.authorName)
                                                        joke.mindLoomReplayShareToken()?.let { token ->
                                                            putExtra(LiveArchivePlayerActivity.EXTRA_REPLAY_SHARE_TOKEN, token)
                                                        }
                                                    }
                                                    context.startActivity(intent)
                                                }
                                                return@MindLoomFeedPostCard
                                            }

                                            else -> Unit
                                        }
                                        jokeForComments = null
                                        val mediaThread = feedJokes.filter { it.mindLoomHasThreadMedia() }
                                        val list =
                                            if (mediaThread.isEmpty()) listOf(joke) else mediaThread
                                        immersiveStartIndex =
                                            list.indexOfFirst { it.mindLoomStableRowId() == joke.mindLoomStableRowId() }
                                                .coerceAtLeast(0)
                                        immersiveChromeVisible = true
                                        immersiveThread = list
                                    },
                                    onOpenComments = { jokeForComments = joke },
                                    onEdit = { onEditJokeClicked(joke) },
                                    onDelete = { onDeleteJokeClicked(joke) }
                                )
                            }
                        }
                    }
                }
            }
        }

        immersiveThread?.let { thread ->
            key(thread.joinToString { it.mindLoomStableRowId() }) {
                val safeStart =
                    immersiveStartIndex.coerceIn(0, (thread.size - 1).coerceAtLeast(0))
                val pagerState = rememberPagerState(
                    initialPage = safeStart,
                    pageCount = { thread.size }
                )
                LaunchedEffect(thread) {
                    immersiveChromeVisible = true
                }
                Dialog(
                    onDismissRequest = { immersiveThread = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnClickOutside = false,
                        dismissOnBackPress = true,
                        decorFitsSystemWindows = false,
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            key = { thread[it].mindLoomStableRowId() }
                        ) { page ->
                            val joke = thread[page]
                            val isCurrentPage = page == pagerState.currentPage
                            FullScreenJokeView(
                                joke = joke,
                                viewModel = viewModel,
                                isFollowing = uiState.followingIds.contains(joke.authorId.trim()),
                                savedJokeIds = uiState.savedJokeIds,
                                onProfileClicked = {
                                    immersiveThread = null
                                    onProfileClicked(joke.authorId)
                                },
                                onEditClicked = {
                                    immersiveThread = null
                                    onEditJokeClicked(joke)
                                },
                                onDeleteClicked = {
                                    immersiveThread = null
                                    onDeleteJokeClicked(joke)
                                },
                                feedIndex = page + 1,
                                feedCount = thread.size,
                                showTopMetadata = false,
                                showAvatarRail = true,
                                chromeVisible = immersiveChromeVisible,
                                onChromeIdleBump = { },
                                onRevealChrome = { immersiveChromeVisible = true },
                                onHideChrome = { immersiveChromeVisible = false },
                                compactImmersive = false,
                                autoPlayMedia = isCurrentPage,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .align(Alignment.TopCenter)
                                .zIndex(40f)
                        ) {
                            if (thread.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    repeat(thread.size) { index ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(2.5.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (index == pagerState.currentPage) {
                                                        Color.White
                                                    } else {
                                                        Color.White.copy(alpha = 0.28f)
                                                    }
                                                )
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { immersiveThread = null },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        jokeForComments?.let { jc ->
            key(jc.mindLoomStableRowId()) {
                CommentsBottomSheet(
                    joke = jc,
                    viewModel = viewModel,
                    onDismiss = { jokeForComments = null },
                    onCommentPosted = {}
                )
            }
        }

    }
}

/**
 * Screen for creating a new joke/post — sized for small phones (scroll + sticky CTA).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val canPost = (text.trim().isNotEmpty() || mediaUri != null) && !isPosting
    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 360 || config.screenHeightDp < 640
    val horizontalPad = if (isCompact) 12.dp else 16.dp
    val ink = Color(0xFF0A0A0A)
    val muted = Color(0xFF737373)
    val accent = Color(0xFF0095F6)
    val surface = Color.White
    val canvas = Color(0xFFFAFAFA)

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
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = JokeType.DOCUMENT
        }
    }

    Scaffold(
        containerColor = canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New post",
                        color = ink,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isPosting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface,
                    titleContentColor = ink,
                    navigationIconContentColor = ink,
                )
            )
        },
        bottomBar = {
            Surface(
                color = surface,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = horizontalPad, vertical = 10.dp)
                ) {
                    Button(
                        onClick = { viewModel.postJoke(text, mediaUri, mediaType) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = canPost,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFEFEFEF),
                            disabledContentColor = muted,
                        )
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val previewMaxHeight = (maxHeight * 0.28f).coerceIn(120.dp, 200.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPad, vertical = if (isCompact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Share a photo, video, PDF, or note with MindLoom.",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MINDLOOM_POST_TEXT_MAX_LENGTH) },
                    placeholder = { Text("What's on your mind?") },
                    supportingText = {
                        Text("${text.length}/$MINDLOOM_POST_TEXT_MAX_LENGTH")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isCompact) 96.dp else 120.dp, max = 180.dp),
                    minLines = if (isCompact) 3 else 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ink,
                        unfocusedTextColor = ink,
                        focusedContainerColor = surface,
                        unfocusedContainerColor = surface,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0xFFDBDBDB),
                        cursorColor = accent,
                        focusedPlaceholderColor = muted,
                        unfocusedPlaceholderColor = muted,
                    )
                )

                AnimatedVisibility(mediaUri != null) {
                    mediaUri?.let { selectedUri ->
                        MindLoomAttachmentPreview(
                            mediaUri = selectedUri,
                            mediaType = mediaType,
                            maxPreviewHeight = previewMaxHeight,
                            onRemove = {
                                mediaUri = null
                                mediaType = JokeType.TEXT
                            }
                        )
                    }
                }

                Text(
                    text = "Attach",
                    color = ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MindLoomComposerAttachmentButton(
                        label = "Photo",
                        icon = Icons.Default.Image,
                        selected = mediaType == JokeType.IMAGE && mediaUri != null,
                        compact = isCompact,
                    ) { imagePicker.launch("image/*") }
                    MindLoomComposerAttachmentButton(
                        label = "Video",
                        icon = Icons.Default.Videocam,
                        selected = mediaType == JokeType.VIDEO && mediaUri != null,
                        compact = isCompact,
                    ) { videoPicker.launch("video/*") }
                    MindLoomComposerAttachmentButton(
                        label = "PDF",
                        icon = Icons.Default.Description,
                        selected = mediaType == JokeType.DOCUMENT && mediaUri != null,
                        compact = isCompact,
                    ) { documentPicker.launch("application/pdf") }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MindLoomComposerAttachmentButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = Color(0xFF0095F6)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(if (compact) 40.dp else 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent else Color(0xFFDBDBDB)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) accent.copy(alpha = 0.10f) else Color.White,
            contentColor = if (selected) accent else Color(0xFF0A0A0A)
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 14.dp, vertical = 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = if (compact) 12.sp else 13.sp
        )
    }
}

@Composable
private fun MindLoomAttachmentPreview(
    mediaUri: Uri,
    mediaType: JokeType,
    maxPreviewHeight: Dp = 180.dp,
    onRemove: () -> Unit
) {
    val title = when (mediaType) {
        JokeType.IMAGE -> "Photo attached"
        JokeType.VIDEO -> "Video attached"
        JokeType.DOCUMENT -> "PDF attached"
        JokeType.TEXT -> "Attachment"
        JokeType.LIVE_SESSION -> "Live session attached"
        JokeType.LIVE_REPLAY -> "Replay attached"
    }
    val icon = when (mediaType) {
        JokeType.IMAGE -> Icons.Default.Image
        JokeType.VIDEO -> Icons.Default.Videocam
        JokeType.DOCUMENT -> Icons.Default.Description
        JokeType.TEXT -> Icons.Default.Description
        JokeType.LIVE_SESSION -> Icons.Default.LiveTv
        JokeType.LIVE_REPLAY -> Icons.Default.LiveTv
    }
    val ink = Color(0xFF0A0A0A)
    val muted = Color(0xFF737373)
    val accent = Color(0xFF0095F6)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF0F0F0))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(14.dp))
    ) {
        if (mediaType == JokeType.IMAGE) {
            AsyncImage(
                model = mediaUri,
                contentDescription = "Selected image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxPreviewHeight),
                contentScale = ContentScale.Crop
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mediaUri.lastPathSegment ?: "Ready to upload",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            Icon(Icons.Default.Cancel, "Remove media", tint = Color.White, modifier = Modifier.size(18.dp))
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
            viewModel.getComments(joke) { updatedComments -> comments = updatedComments }
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
                    onClick = { viewModel.likeJoke(joke) }
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
                    onCommentChanged = { commentText = it.take(MINDLOOM_COMMENT_TEXT_MAX_LENGTH) },
                    onPostComment = {
                        val trimmed = commentText.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.postComment(
                                joke = joke,
                                text = trimmed,
                                onFailure = { commentText = trimmed },
                            )
                            commentText = ""
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

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    uri: Uri,
    isFullScreen: Boolean = false,
    /** When true, video fills the parent [Box] (e.g. MindLoom media card). */
    fillContainer: Boolean = false,
    autoPlay: Boolean = isFullScreen,
    showControls: Boolean = isFullScreen,
    muted: Boolean = false
) {
    val context = LocalContext.current
    // Key only on uri — do not rebuild the decoder when muted/autoPlay toggles.
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 1_500,
                        /* maxBufferMs = */ 5_000,
                        /* bufferForPlaybackMs = */ 750,
                        /* bufferForPlaybackAfterRebufferMs = */ 1_500
                    )
                    .build()
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
                playWhenReady = autoPlay
                prepare()
            }
    }

    LaunchedEffect(autoPlay) {
        exoPlayer.playWhenReady = autoPlay
        if (autoPlay) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }
    LaunchedEffect(muted) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    DisposableEffect(uri) {
        onDispose {
            runCatching {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            }
        }
    }

    val viewModifier = when {
        isFullScreen || fillContainer -> modifier
        else ->
            modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp))
    }

    // Feed uses texture_view via layout — avoids "Usage not a subset" EGL errors in LazyColumn.
    AndroidView(
        modifier = viewModifier,
        factory = { ctx ->
            val playerView = if (!isFullScreen) {
                LayoutInflater.from(ctx).inflate(R.layout.view_mindloom_feed_player, null, false) as PlayerView
            } else {
                PlayerView(ctx)
            }
            playerView.apply {
                player = exoPlayer
                useController = showControls
                resizeMode = if (fillContainer || isFullScreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
            playerView.useController = showControls
            playerView.resizeMode = if (fillContainer || isFullScreen) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                 modifier: Modifier = Modifier,
                 isFullScreen: Boolean = false,
                 contentScale: ContentScale = ContentScale.Crop,
                 autoPlay: Boolean = true,
                 onMediaClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    if (!isFullScreen) {
        Spacer(Modifier.height(16.dp))
    }

    val clickModifier = if (onMediaClick != null) {
        Modifier.clickable(onClick = onMediaClick)
    } else {
        Modifier
    }

    when (runCatching { JokeType.valueOf(mediaType) }.getOrNull()) {
        JokeType.IMAGE -> AsyncImage(
            model = url,
            contentDescription = "Joke image",
            modifier = (if (isFullScreen) modifier else Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp))
                .clip(RoundedCornerShape(if (isFullScreen) 0.dp else 16.dp))
                .then(clickModifier),
            contentScale = contentScale
        )
        JokeType.VIDEO -> {
            if (isFullScreen) {
                VideoPlayer(
                    modifier = modifier.then(clickModifier),
                    uri = Uri.parse(url),
                    isFullScreen = true,
                    autoPlay = autoPlay,
                    showControls = false,
                    muted = false
                )
            } else {
                // Avoid spawning inactive ExoPlayers in feed / list previews.
                Box(
                    modifier = (modifier.fillMaxWidth().heightIn(max = 400.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A0A0A))
                        .then(clickModifier),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Video preview",
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }
        JokeType.DOCUMENT -> OutlinedCard(
            modifier = (if (isFullScreen) modifier else Modifier.fillMaxWidth())
                .clickable {
                    if (onMediaClick != null) {
                        onMediaClick()
                    } else {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
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


@Composable
private fun MindLoomTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    useGlassChrome: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "mindloom_top_action_scale"
    )
    val buttonSize = when {
        compact && useGlassChrome -> 26.dp
        compact -> 30.dp
        else -> 32.dp
    }
    val iconSize = when {
        compact && useGlassChrome -> 14.dp
        compact -> 16.dp
        else -> 18.dp
    }
    val fill = when {
        useGlassChrome -> Color.White.copy(alpha = 0.1f)
        compact -> Color.White.copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    Box(
        modifier = Modifier
            .padding(horizontal = if (compact && !useGlassChrome) 1.dp else if (!compact) 4.dp else 0.dp)
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (useGlassChrome) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun MindLoomTopActionChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MindLoomFeedChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (selected) Color.White else Color.White.copy(alpha = 0.12f)
    val contentColor = if (selected) Color.Black else Color.White
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 24.dp, max = 30.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.24f))
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 7.sp,
            lineHeight = 8.sp
        )
    }
}

@Composable
private fun MindLoomInfoChip(
    text: String,
    tint: Color = Color.White
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 8.sp,
            lineHeight = 9.sp
        )
    }
}

@Composable
private fun MindLoomMetric(
    label: String,
    value: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun MindLoomRailAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            active -> 1.05f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 130),
        label = "mindloom_rail_scale"
    )
    val tint = if (active) activeTint else Color.White
    val bg = if (active) activeTint.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
    val glow = if (active) activeTint.copy(alpha = 0.28f) else Color.Transparent
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(36.dp)
            .scale(scale)
            .shadow(if (active) 6.dp else 1.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(glow, bg)
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.22f else 0.14f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MindLoomMessageInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val messageTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        textAlign = TextAlign.Start,
        lineHeight = 20.sp
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp, max = 52.dp),
            singleLine = true,
            textStyle = messageTextStyle,
            placeholder = {
                Text(
                    text = "Message MindLoom...",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1E242E),
                unfocusedContainerColor = Color(0xFF181D26),
                disabledContainerColor = Color(0xFF141820),
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.28f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                disabledBorderColor = Color.White.copy(alpha = 0.1f),
                focusedPlaceholderColor = Color.White.copy(alpha = 0.72f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.62f)
            )
        )

        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSend()
            },
            enabled = value.isNotBlank(),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (value.isBlank()) {
                        Color.White.copy(alpha = 0.12f)
                    } else {
                        Color(0xFFFFC04D)
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = if (value.isBlank()) Color.White.copy(alpha = 0.55f) else Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MindLoomSkeletonFeedView() {
    val shimmer = rememberInfiniteTransition(label = "mindloom_shimmer")
    val shift by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mindloom_shimmer_shift"
    )
    val base = Color.White.copy(alpha = 0.08f)
    val highlight = Color.White.copy(alpha = 0.18f)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(800f + (800f * shift), 360f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = MindLoomFeedTopPadding, start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            )
        }
    }
}

private fun compactCount(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000f)
        else -> value.toString()
    }
}

private fun resolveMediaLabel(mediaType: String): String {
    return when (runCatching { JokeType.valueOf(mediaType) }.getOrNull()) {
        JokeType.IMAGE -> "Photo"
        JokeType.VIDEO -> "Video"
        JokeType.DOCUMENT -> "Document"
        JokeType.TEXT -> "Text"
        JokeType.LIVE_SESSION -> "Live"
        JokeType.LIVE_REPLAY -> "Replay"
        null -> "Post"
    }
}

// Full-screen feed card with immersive overlay controls.
@Composable
fun FullScreenJokeView(
    joke: Joke,
    viewModel: JokesViewModel,
    isFollowing: Boolean,
    savedJokeIds: Set<String>,
    onProfileClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    feedIndex: Int? = null,
    feedCount: Int = 0,
    showAuthorIdentity: Boolean = true,
    showViewPreviousPosts: Boolean = true,
    showAvatarRail: Boolean = false,
    showFollowRailAction: Boolean = true,
    chromeVisible: Boolean = true,
    onChromeIdleBump: () -> Unit = {},
    onRevealChrome: () -> Unit = {},
    onHideChrome: () -> Unit = {},
    /** When false, top metadata chips are hidden (reels-style fullscreen). */
    showTopMetadata: Boolean = true,
    /** Instagram-style dialog: tighter chrome and Fit media inside a shorter stage. */
    compactImmersive: Boolean = false,
    /** When false, video pages stay paused (off-screen pager neighbors). */
    autoPlayMedia: Boolean = true,
) {
    val currentUserId = viewModel.getCurrentUserId()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val homeUiState by viewModel.uiState.collectAsState()
    val invitationAlreadySent = homeUiState.sentInvitationUserIds.contains(joke.authorId.trim())
    var showComments by remember { mutableStateOf(false) }
    var quickComment by remember(joke.mindLoomStableRowId()) { mutableStateOf("") }
    var isLiked by remember(joke.mindLoomStableRowId()) { mutableStateOf(joke.likes.contains(currentUserId)) }
    var likesCount by remember(joke.mindLoomStableRowId()) { mutableIntStateOf(joke.likesCount) }
    var commentsCount by remember(joke.mindLoomStableRowId()) { mutableIntStateOf(joke.commentsCount) }
    var isSaved by remember(joke.mindLoomStableRowId()) { mutableStateOf(savedJokeIds.contains(joke.id)) }
    var optimisticFollowing by remember(joke.mindLoomStableRowId()) { mutableStateOf(isFollowing) }
    var likeInFlight by remember(joke.mindLoomStableRowId()) { mutableStateOf(false) }
    val isOwner = currentUserId == joke.authorId
    val timestampText = joke.timestamp?.let(::formatTimestamp) ?: "Just now"
    val mediaLabel = resolveMediaLabel(joke.mediaType)
    val chipTopPadding = if (compactImmersive) MindLoomImmersiveChipTopPadding else MindLoomFeedChipTopPadding
    val bottomChromeReserve =
        if (compactImmersive) MindLoomImmersiveBottomChromeReserve else MindLoomBottomChromeReserve
    val railTopPadding = if (compactImmersive) 28.dp else 52.dp
    val railBottomExtra = if (compactImmersive) 12.dp else 32.dp
    val isLiveReplay = joke.mindLoomMediaType() == JokeType.LIVE_REPLAY
    val isDocument = joke.mindLoomMediaType() == JokeType.DOCUMENT

    LaunchedEffect(joke) {
        isLiked = joke.likes.contains(currentUserId)
        likesCount = joke.likesCount
        commentsCount = joke.commentsCount
    }
    LaunchedEffect(savedJokeIds, joke.mindLoomStableRowId()) {
        isSaved = savedJokeIds.contains(joke.id)
    }
    LaunchedEffect(isFollowing, joke.mindLoomStableRowId()) {
        optimisticFollowing = isFollowing
    }

    fun submitQuickComment() {
        val trimmedComment = quickComment.trim()
        if (trimmedComment.isEmpty()) return
        if (viewModel.getCurrentUserId() == null) {
            Toast.makeText(context, "Sign in to comment.", Toast.LENGTH_SHORT).show()
            return
        }
        onChromeIdleBump()
        quickComment = ""
        viewModel.postComment(
            joke,
            trimmedComment,
            onComplete = { commentsCount++ },
            onFailure = { quickComment = trimmedComment }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (joke.mindLoomMediaType()) {
            JokeType.LIVE_SESSION -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MindLoomCanvas),
                    contentAlignment = Alignment.Center
                ) {
                    MindLoomLiveSessionPromoCard(
                        authorName = joke.authorName,
                        onWatchLive = {
                            val target = joke.mindLoomLiveLaunchTarget()
                            if (target == null) {
                                Toast.makeText(context, "This live session link is unavailable.", Toast.LENGTH_SHORT).show()
                            } else {
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
                        },
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            JokeType.LIVE_REPLAY -> {
                MindLoomLiveReplayCard(
                    sessionId = joke.mindLoomLiveSessionId().orEmpty(),
                    shareAccessToken = joke.mindLoomReplayShareToken(),
                    onOpenFullScreen = { onRevealChrome() },
                    modifier = Modifier.fillMaxSize(),
                    isFullScreen = true,
                    autoPlay = autoPlayMedia
                )
            }

            else -> if (!joke.mediaUrl.isNullOrBlank()) {
                MediaContent(
                    mediaType = joke.mediaType,
                    url = joke.mediaUrl,
                    modifier = Modifier.fillMaxSize(),
                    isFullScreen = true,
                    contentScale = if (compactImmersive) ContentScale.Fit else ContentScale.Crop,
                    autoPlay = autoPlayMedia,
                    onMediaClick = {
                        if (!chromeVisible) {
                            onRevealChrome()
                            return@MediaContent
                        }
                        // Documents: open URI when chrome is visible (Instagram-like "tap to open").
                        if (isDocument) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(joke.mediaUrl)))
                            }.onFailure {
                                Toast.makeText(context, "No app available to open this file.", Toast.LENGTH_SHORT).show()
                            }
                            return@MediaContent
                        }
                        onHideChrome()
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MindLoomSurface,
                                    MindLoomSurfaceMuted
                                )
                            )
                        )
                        .then(
                            if (!chromeVisible) {
                                Modifier.clickable { onRevealChrome() }
                            } else {
                                Modifier.clickable { onHideChrome() }
                            }
                        )
                )
            }
        }

        if (chromeVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MindLoomTextPrimary.copy(alpha = 0.22f),
                                MindLoomTextPrimary.copy(alpha = 0.64f)
                            )
                        )
                    )
            )
        }

        // Only intercept taps when chrome is hidden (reveal), or for non-control media.
        // When chrome is visible over LIVE_REPLAY / documents, leave the player/doc hittable.
        if (!chromeVisible || (!isLiveReplay && !isDocument)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (chromeVisible) onHideChrome() else onRevealChrome()
                    }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(280)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            Box(Modifier.fillMaxSize()) {
        if (showTopMetadata) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(if (compactImmersive) Modifier else Modifier.statusBarsPadding())
                .padding(start = 10.dp, top = chipTopPadding, end = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MindLoomInfoChip(
                text = mediaLabel,
                tint = Color.White
            )
            MindLoomInfoChip(
                text = timestampText,
                tint = Color(0xFF8BD0FF)
            )
            if (feedCount > 0 && feedIndex != null) {
                MindLoomInfoChip(
                    text = "$feedIndex/$feedCount",
                    tint = Color(0xFF8BD0FF)
                )
            }
        }

        if (isOwner) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (compactImmersive) Modifier else Modifier.statusBarsPadding())
                    .padding(top = chipTopPadding, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MindLoomTopActionButton(
                    icon = Icons.Default.EditNote,
                    contentDescription = "Edit post",
                    onClick = {
                        onChromeIdleBump()
                        onEditClicked()
                    }
                )
                MindLoomTopActionButton(
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete post",
                    onClick = {
                        onChromeIdleBump()
                        onDeleteClicked()
                    }
                )
            }
        }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp, top = railTopPadding, bottom = bottomChromeReserve + railBottomExtra),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compactImmersive) 4.dp else 5.dp)
        ) {
            if (showAvatarRail) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MindLoomTextPrimary.copy(alpha = 0.34f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable(onClick = {
                            onChromeIdleBump()
                            onProfileClicked()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = joke.authorProfileUrl ?: R.drawable.default_profile_image,
                        contentDescription = "Profile avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(CircleShape)
                    )
                }
            }
            if (!isOwner && showFollowRailAction) {
                MindLoomRailAction(
                    icon = if (optimisticFollowing) Icons.Default.Person else Icons.Default.PersonAdd,
                    label = if (optimisticFollowing) "Following" else "Follow",
                    active = optimisticFollowing,
                    activeTint = MindLoomAccent,
                    onClick = {
                        onChromeIdleBump()
                        val previousState = optimisticFollowing
                        optimisticFollowing = !optimisticFollowing
                        viewModel.followUser(joke.authorId.trim()) { success, message ->
                            if (!success) {
                                optimisticFollowing = previousState
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            MindLoomRailAction(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = compactCount(likesCount),
                active = isLiked,
                activeTint = MindLoomAccent,
                onClick = {
                    onChromeIdleBump()
                    if (likeInFlight) return@MindLoomRailAction
                    val previousLiked = isLiked
                    val previousLikesCount = likesCount
                    likeInFlight = true
                    isLiked = !isLiked
                    likesCount = (likesCount + if (isLiked) 1 else -1).coerceAtLeast(0)
                    viewModel.likeJoke(joke) { success ->
                        likeInFlight = false
                        if (!success) {
                            isLiked = previousLiked
                            likesCount = previousLikesCount.coerceAtLeast(0)
                        }
                    }
                }
            )
            MindLoomRailAction(
                icon = if (showComments) Icons.Filled.Comment else Icons.Outlined.Comment,
                label = compactCount(commentsCount),
                active = showComments,
                activeTint = MindLoomAccent,
                onClick = {
                    onChromeIdleBump()
                    showComments = true
                }
            )
            MindLoomRailAction(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) "Saved" else "Save",
                active = isSaved,
                activeTint = MindLoomAccent,
                onClick = {
                    onChromeIdleBump()
                    val previous = isSaved
                    isSaved = !isSaved
                    viewModel.toggleSaveJoke(joke) { success, isSavedNow ->
                        if (!success) {
                            isSaved = previous
                        } else {
                            isSaved = isSavedNow
                        }
                    }
                }
            )
            MindLoomRailAction(
                icon = Icons.AutoMirrored.Filled.Send,
                label = "Share",
                active = false,
                activeTint = MindLoomAccent,
                onClick = {
                    onChromeIdleBump()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${joke.authorName}: ${joke.text}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                }
            )
            if (!isOwner) {
                MindLoomRailAction(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = if (invitationAlreadySent) "Invited" else "Chat",
                    active = invitationAlreadySent,
                    activeTint = MindLoomAccent,
                    onClick = {
                        onChromeIdleBump()
                        if (invitationAlreadySent) {
                            Toast.makeText(context, "Invitation already sent.", Toast.LENGTH_SHORT).show()
                            return@MindLoomRailAction
                        }
                        val targetUser = User(
                            uid = joke.authorId,
                            name = joke.authorName,
                            profileImageUrl = joke.authorProfileUrl
                        )
                        viewModel.sendChatInvitation(targetUser) { _, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (compactImmersive) Modifier else Modifier.navigationBarsPadding())
                .padding(horizontal = if (compactImmersive) 4.dp else 6.dp)
                .zIndex(20f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = if (compactImmersive) {
                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                },
                color = MindLoomTextPrimary.copy(alpha = if (compactImmersive) 0.42f else 0.12f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (compactImmersive) 0.08f else 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (showAuthorIdentity) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = {
                                    onChromeIdleBump()
                                    onProfileClicked()
                                }),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = joke.authorName.ifBlank { "Creator" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${joke.authorId.take(8)}",
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    lineHeight = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (joke.text.isNotBlank()) {
                        Text(
                            text = joke.text,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = if (showAuthorIdentity) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onChromeIdleBump()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showComments = true
                            },
                            modifier = Modifier.height(22.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Comment,
                                contentDescription = "Open comments",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = compactCount(commentsCount),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            MindLoomMessageInputRow(
                value = quickComment,
                onValueChange = {
                    onChromeIdleBump()
                    quickComment = it
                },
                onSend = {
                    onChromeIdleBump()
                    submitQuickComment()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            )
        }
            }
        }
    }

    if (showComments) {
        CommentsBottomSheet(
            joke = joke,
            viewModel = viewModel,
            onDismiss = { showComments = false },
            onCommentPosted = {
                commentsCount++
            },
            onCommentPostFailed = {
                commentsCount = (commentsCount - 1).coerceAtLeast(0)
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
            supportingText = {
                Text("${commentText.length}/$MINDLOOM_COMMENT_TEXT_MAX_LENGTH")
            },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (comment.isOwnerResponse) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Creator",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
    joke: Joke,
    viewModel: JokesViewModel,
    onDismiss: () -> Unit,
    onCommentPosted: () -> Unit, // Callback to update the count on the main screen
    onCommentPostFailed: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    val jokeKey = joke.mindLoomStableRowId()
    var comments by remember(jokeKey) { mutableStateOf<List<Comment>>(emptyList()) }
    var nextCommentsCursor by remember(jokeKey) { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }
    var hasMoreComments by remember(jokeKey) { mutableStateOf(false) }
    var commentText by remember(jokeKey) { mutableStateOf("") }
    var isLoading by remember(jokeKey) { mutableStateOf(true) }
    var isLoadingMore by remember(jokeKey) { mutableStateOf(false) }
    var commentsLoadError by remember(jokeKey) { mutableStateOf<String?>(null) }
    val currentUser = Firebase.auth.currentUser

    fun loadComments(reset: Boolean) {
        if (isLoadingMore || (!reset && !hasMoreComments)) return

        val cursor = if (reset) null else nextCommentsCursor
        if (!reset && cursor == null) return

        if (reset) {
            isLoading = true
            comments = emptyList()
            nextCommentsCursor = null
            hasMoreComments = false
        }
        isLoadingMore = true
        commentsLoadError = null
        viewModel.getCommentsPage(
            joke = joke,
            cursor = cursor,
            onResult = { page ->
                val loadedComments = page.comments.reversed()
                comments = if (reset) loadedComments else loadedComments + comments
                nextCommentsCursor = page.nextCursor
                hasMoreComments = page.hasMore
                isLoading = false
                isLoadingMore = false
            },
            onFailure = {
                isLoading = false
                isLoadingMore = false
                commentsLoadError = "Couldn't load comments. Try again."
            },
        )
    }

    LaunchedEffect(jokeKey) {
        loadComments(reset = true)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Comments (${if (hasMoreComments) "${comments.size}+" else comments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { loadComments(reset = true) }, enabled = !isLoadingMore) {
                    Text("Refresh", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (commentsLoadError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(commentsLoadError.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { loadComments(reset = true) }) {
                            Text("Try again")
                        }
                    }
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true,
                ) {
                    items(comments.reversed(), key = { it.id }) { comment ->
                        CommentItem(comment)
                    }
                    if (hasMoreComments) {
                        item(key = "load_earlier_comments") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(
                                    onClick = { loadComments(reset = false) },
                                    enabled = !isLoadingMore,
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text("Load earlier comments")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input field for new comments, sticks to the bottom
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it.take(MINDLOOM_COMMENT_TEXT_MAX_LENGTH) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Add a comment...") },
                supportingText = {
                    Text("${commentText.length}/$MINDLOOM_COMMENT_TEXT_MAX_LENGTH")
                },
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val trimmed = commentText.trim()
                            if (trimmed.isEmpty() || currentUser == null) return@IconButton
                            // --- OPTIMISTIC UI UPDATE ---
                            val tempCommentId = UUID.randomUUID().toString()

                            val optimisticComment = Comment(
                                id = tempCommentId,
                                authorId = currentUser.uid,
                                authorName = currentUser.displayName ?: "You",
                                authorProfileUrl = currentUser.photoUrl?.toString(),
                                text = trimmed,
                                isOwnerResponse = currentUser.uid == joke.authorId.trim(),
                                timestamp = Timestamp.now()
                            )

                            comments = comments + optimisticComment
                            onCommentPosted()
                            commentText = ""
                            viewModel.postComment(
                                joke,
                                trimmed,
                                onComplete = {
                                    loadComments(reset = true)
                                },
                                onFailure = {
                                    comments = comments.filter { it.id != tempCommentId }
                                    commentText = trimmed
                                    onCommentPostFailed()
                                }
                            )
                        },
                        enabled = commentText.isNotBlank() && currentUser != null
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
fun EmptyJokesView(
    title: String = "No Posts Yet",
    subtitle: String = "Be the first to share something with the community.",
    hint: String = "Use the Create button to post.",
    primaryCtaLabel: String? = null,
    onPrimaryCta: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SentimentSatisfied,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = Color(0xFF0A0A0A)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF0A0A0A),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color(0xFF262626),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF404040),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (primaryCtaLabel != null && onPrimaryCta != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPrimaryCta,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0A0A0A),
                    contentColor = Color.White
                )
            ) {
                Text(primaryCtaLabel, fontWeight = FontWeight.Bold)
            }
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
fun JokesUserProfileScreen(
    authorId: String,
    viewModel: JokesViewModel,
    onNavigateUp: () -> Unit,
    onOpenFeed: (String) -> Unit,
    onGoLiveClicked: () -> Unit
) {

    LaunchedEffect(authorId) {
        viewModel.fetchJokesByAuthor(authorId)
    }

    val uiState by viewModel.profileUiState.collectAsState()
    val homeState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var editingJoke by remember { mutableStateOf<Joke?>(null) }
    var jokeToDelete by remember { mutableStateOf<Joke?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.author?.name ?: "User Profile", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        if (uiState.isLoading) {
            LoadingPlaceholder()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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
                            .size(96.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = uiState.author?.name ?: "Anonymous",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStat(label = "Following", value = uiState.followingCount)
                        ProfileStat(label = "Followers", value = uiState.followersCount)
                        ProfileStat(label = "Likes", value = uiState.likesCount)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!uiState.isSelf) {
                            Button(
                                onClick = {
                                    viewModel.followUser(authorId) { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        viewModel.fetchJokesByAuthor(authorId)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (uiState.isFollowing) "Following" else "Follow")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (homeState.sentInvitationUserIds.contains(authorId)) {
                                        Toast.makeText(context, "Invitation already sent.", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    val targetUser = uiState.author ?: return@OutlinedButton
                                    viewModel.sendChatInvitation(targetUser) { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !homeState.sentInvitationUserIds.contains(authorId),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (homeState.sentInvitationUserIds.contains(authorId)) "Invited" else "Message"
                                )
                            }
                        } else {
                            Button(
                                onClick = { onOpenFeed(authorId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View My Feed")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Check out ${uiState.author?.name ?: "this"} on MindLoom")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Share")
                        }
                    }
                    if (uiState.isSelf) {
                        Button(
                            onClick = onGoLiveClicked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LiveTv, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Go Live")
                        }
                    }
                }

                if (uiState.jokes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("This user hasn't posted anything yet.", color = Color.White)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(uiState.jokes, key = { it.id }) { joke ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray)
                                    .clickable { onOpenFeed(authorId) }
                            ) {
                                when (runCatching { JokeType.valueOf(joke.mediaType) }.getOrNull()) {
                                    JokeType.IMAGE -> AsyncImage(
                                        model = joke.mediaUrl,
                                        contentDescription = "Post media",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    JokeType.VIDEO -> {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(32.dp)
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = joke.text.take(40),
                                            color = Color.White,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
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

@Composable
private fun ProfileStat(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
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
    val context = LocalContext.current

    Scaffold(
        containerColor = MindLoomCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Search MindLoom", color = MindLoomTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MindLoomTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MindLoomTopBarFill,
                    titleContentColor = MindLoomTextPrimary,
                    navigationIconContentColor = MindLoomTextPrimary,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MindLoomSurface, MindLoomCanvas, MindLoomSurfaceMuted)
                    )
                )
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchUsers(it) },
                placeholder = { Text("Search creators by name or username...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MindLoomTextPrimary,
                    unfocusedTextColor = MindLoomTextPrimary,
                    focusedContainerColor = MindLoomSurface,
                    unfocusedContainerColor = MindLoomSurface,
                    focusedBorderColor = MindLoomAccent,
                    unfocusedBorderColor = MindLoomOutline,
                    focusedLeadingIconColor = MindLoomAccent,
                    unfocusedLeadingIconColor = MindLoomTextMeta,
                    focusedPlaceholderColor = MindLoomTextMeta,
                    unfocusedPlaceholderColor = MindLoomTextMeta,
                    cursorColor = MindLoomAccent,
                )
            )
            Spacer(Modifier.height(16.dp))
            if (uiState.isSearching) {
                LoadingPlaceholder()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.searchResults, key = { it.uid }) { user ->
                        UserSearchResultItem(
                            user = user,
                            invitationAlreadySent = uiState.sentInvitationUserIds.contains(user.uid),
                            onClick = { onProfileClicked(user.uid) },
                            onInviteClick = {
                                if (uiState.sentInvitationUserIds.contains(user.uid)) {
                                    Toast.makeText(context, "Invitation already sent.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.sendChatInvitation(user) { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchResultItem(
    user: User,
    invitationAlreadySent: Boolean = false,
    onClick: () -> Unit,
    onInviteClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MindLoomSurface),
        border = BorderStroke(1.dp, MindLoomOutline)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = user.profileImageUrl ?: R.drawable.default_profile_image,
                contentDescription = "Profile picture of ${user.name}",
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name ?: "MindLoom User",
                    style = MaterialTheme.typography.titleMedium,
                    color = MindLoomTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username?.takeIf { it.isNotBlank() } ?: user.uid.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MindLoomTextMeta,
                )
            }
            TextButton(
                onClick = {
                    onInviteClick()
                },
                enabled = !invitationAlreadySent
            ) {
                Text(
                    if (invitationAlreadySent) "Invited" else "Invite",
                    color = if (invitationAlreadySent) MindLoomTextMeta else MindLoomAccent,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JokesFeedScreen(
    authorId: String,
    viewModel: JokesViewModel,
    onNavigateUp: () -> Unit,
    onOpenAuthorProfile: () -> Unit
) {
    LaunchedEffect(authorId) {
        viewModel.fetchJokesByAuthor(authorId)
    }

    val uiState by viewModel.profileUiState.collectAsState()
    val homeState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState { uiState.jokes.size }
    var chromeVisible by remember { mutableStateOf(true) }

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
                    containerColor = MindLoomTopBarFill,
                    titleContentColor = MindLoomTextPrimary,
                    navigationIconContentColor = MindLoomTextPrimary,
                )
            )
        },
        containerColor = MindLoomCanvas,
    ) { padding ->
        if (uiState.isLoading) {
            LoadingPlaceholder()
        } else if (uiState.jokes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This user has no posts yet.", color = MindLoomTextPrimary)
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding),
                key = { uiState.jokes[it].mindLoomStableRowId() }
            ) {
                FullScreenJokeView(
                    joke = uiState.jokes[it],
                    viewModel = viewModel,
                    isFollowing = homeState.followingIds.contains(authorId),
                    savedJokeIds = homeState.savedJokeIds,
                    onProfileClicked = onOpenAuthorProfile,
                    onEditClicked = {},
                    onDeleteClicked = {},
                    chromeVisible = chromeVisible,
                    onRevealChrome = { chromeVisible = true },
                    onHideChrome = { chromeVisible = false }
                )
            }
        }
    }
}
