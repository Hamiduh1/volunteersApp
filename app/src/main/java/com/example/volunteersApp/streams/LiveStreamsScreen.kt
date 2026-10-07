package com.example.volunteersApp.streams



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

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.LiveTv

import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.filled.Search

import androidx.compose.material.icons.filled.Visibility

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ElevatedButton

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold

import androidx.compose.material3.SnackbarDuration

import androidx.compose.material3.SnackbarHost

import androidx.compose.material3.SnackbarHostState

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import com.google.accompanist.swiperefresh.SwipeRefresh

import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

import com.google.firebase.Firebase

import com.google.firebase.auth.auth

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun LiveStreamsScreen(

    viewModel: LiveStreamsViewModel,

    onBack: () -> Unit,

    onStreamClick: (LiveSession) -> Unit

) {

    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    val filteredSessions = uiState.filteredSessions

    val context = LocalContext.current

    val currentUserId = Firebase.auth.currentUser?.uid

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)



    LaunchedEffect(uiState.replayLinkError) {

        if (uiState.replayLinkError != null) {

            scope.launch {

                snackbarHostState.showSnackbar(uiState.replayLinkError!!, duration = SnackbarDuration.Long)

                viewModel.clearReplayLinkError()

            }

        }

    }



    LaunchedEffect(uiState.requestSubmissionError) {

        if (uiState.requestSubmissionError != null) {

            scope.launch {

                snackbarHostState.showSnackbar(

                    message = uiState.requestSubmissionError!!,

                    duration = SnackbarDuration.Short

                )

                viewModel.clearRequestError()

            }

        }

    }



    val liveCount = filteredSessions.count { it.isLive }



    LiveStudioTheme {

        Scaffold(

            modifier = Modifier.fillMaxSize(),

            containerColor = LiveStudioBackground,

            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },

        ) { padding ->

            SwipeRefresh(

                state = swipeRefreshState,

                onRefresh = viewModel::onRefresh,

                modifier = Modifier.padding(padding)

            ) {

                when {

                    uiState.isLoading && filteredSessions.isEmpty() && uiState.error == null -> LoadingView()

                    else -> LiveStreamsContent(

                        uiState = uiState,

                        filteredSessions = filteredSessions,

                        liveCount = liveCount,

                        currentUserId = currentUserId,

                        onBack = onBack,

                        onStreamClick = onStreamClick,

                        viewModel = viewModel,

                        context = context,

                    )

                }

            }

        }

    }

}



@Composable

private fun LiveStreamsContent(

    uiState: LiveStreamsUiState,

    filteredSessions: List<LiveSession>,

    liveCount: Int,

    currentUserId: String?,

    onBack: () -> Unit,

    onStreamClick: (LiveSession) -> Unit,

    viewModel: LiveStreamsViewModel,

    context: android.content.Context,

) {

    LazyColumn(

        modifier = Modifier.fillMaxSize(),

        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),

        verticalArrangement = Arrangement.spacedBy(14.dp)

    ) {

        item(key = "studio_header") {

            if (uiState.isSearching) {

                LiveStudioSearchBar(

                    value = uiState.searchQuery,

                    onValueChange = viewModel::onSearchQueryChange,

                    onClear = { viewModel.onSearchQueryChange("") },

                    onClose = viewModel::toggleSearch,

                )

            } else {

                LiveStudioHeader(

                    liveCount = liveCount,

                    onBack = onBack,

                    onSearch = viewModel::toggleSearch,

                    onRefresh = viewModel::onRefresh,

                )

            }

        }



        if (!uiState.isSearching) {

            item(key = "studio_hero") {

                LiveStudioHeroCard(liveCount = liveCount)

            }

            item(key = "studio_go_live") {

                LiveGoLiveBanner(
                    onGoLive = {
                        context.startActivity(
                            android.content.Intent(context, StartStreamActivity::class.java).apply {
                                putExtra(StartStreamActivity.EXTRA_SOURCE_TYPE, "standalone")
                            }
                        )
                    },
                )

            }

        }



        val studioError = uiState.error
        if (studioError != null) {

            item(key = "studio_error") {

                LiveStudioErrorBanner(

                    message = studioError,

                    onRetry = viewModel::onRefresh,

                    onDismiss = viewModel::resetError,

                )

            }

        }



        if (filteredSessions.isNotEmpty() || !uiState.isSearching || uiState.searchQuery.isNotBlank()) {

            item(key = "studio_filters") {

                LiveStudioFilterRow(

                    selectedFilter = uiState.studioFilter,

                    selectedFeed = uiState.studioFeed,

                    onFilterSelected = viewModel::onStudioFilterChange,

                    onFeedSelected = viewModel::onStudioFeedChange,

                )

            }

        }



        if (uiState.isLoadingOlderReplays) {

            item(key = "studio_loading_replays") {

                OlderReplaysLoadingRow()

            }

        }



        if (filteredSessions.isEmpty()) {

            if (!uiState.isLoadingOlderReplays) {

                item(key = "studio_empty") {

                    EmptyStreamsPlaceholder(isSearching = uiState.isSearching)

                }

            }

        } else {

            items(filteredSessions, key = { it.sessionId }) { session ->

                ModernStreamCard(

                    session = session,

                    onOpenFullScreen = {

                        if (session.isLive) onStreamClick(session)

                    },

                    onWatchReplay = {

                        context.startActivity(

                            LiveArchivePlayerIntent.create(

                                context = context,

                                sessionId = session.sessionId,

                                title = session.title,

                            )

                        )

                    },

                    onRequestJoin = if (session.isLive && session.stageAccessMode == LiveStageAccessMode.REQUEST_TO_JOIN) {

                        { viewModel.submitJoinRequest(it) }

                    } else {

                        null

                    },

                    isRequestPending = uiState.pendingRequestStreamIds.contains(session.sessionId) ||

                        uiState.submittingRequestStreamId == session.sessionId,

                    isJoined = session.sessionId in uiState.acceptedStreamIds,

                    isReplayLoading = uiState.replayLinkLoadingId == session.sessionId,

                    canOpenLive = canOpenLiveFromFeed(

                        session = session,

                        isJoined = session.sessionId in uiState.acceptedStreamIds,

                        currentUserId = currentUserId,

                        followingHostIds = uiState.followingHostIds,

                        acceptedEventIds = uiState.acceptedEventIds,

                    ),

                    canWatchReplay = session.canWatchReplay(

                        currentUserId = currentUserId,

                        followingHostIds = uiState.followingHostIds,

                    ),

                )

            }

        }

    }

}



@Composable

fun ModernStreamCard(

    session: LiveSession,

    onOpenFullScreen: () -> Unit,

    onWatchReplay: (() -> Unit)? = null,

    onRequestJoin: ((LiveSession) -> Unit)? = null,

    isRequestPending: Boolean = false,

    isJoined: Boolean = false,

    isReplayLoading: Boolean = false,

    canOpenLive: Boolean = true,

    canWatchReplay: Boolean = true,

) {

    val currentUserId = remember { Firebase.auth.currentUser?.uid }
    var menuOpen by remember { mutableStateOf(false) }
    var shareSheetUrl by remember { mutableStateOf<String?>(null) }
    val shareUrl = remember(session.sessionId, session.status, session.archiveStatus, session.replayVisibility) {
        feedShareUrl(session)
    }



    Surface(

        onClick = {

            when {

                session.isLive && canOpenLive -> onOpenFullScreen()

                session.isArchiveReady && canWatchReplay -> onWatchReplay?.invoke()

            }

        },

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(16.dp),

        color = LiveStudioSurface,

        shadowElevation = 0.dp,

        tonalElevation = 0.dp,

    ) {

        Column {

            LiveFeedThumbnail(session = session)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                LiveHostAvatar(
                    name = session.hostName,
                    photoUrl = session.hostProfilePicUrl,
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title.ifBlank { "Live Session" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LiveStudioInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = liveFeedMetaLine(session, isOwnStream = currentUserId == session.hostId),
                        style = MaterialTheme.typography.bodySmall,
                        color = LiveStudioMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = LiveStudioInk)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (shareUrl != null) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    shareSheetUrl = shareUrl
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Sharing is limited by the host") },
                                enabled = false,
                                onClick = {},
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {



                StreamActionButton(

                    session = session,

                    onOpenFullScreen = onOpenFullScreen,

                    onWatchReplay = onWatchReplay,

                    onRequestJoin = onRequestJoin,

                    isRequestPending = isRequestPending,

                    isJoined = isJoined,

                    isReplayLoading = isReplayLoading,

                    canOpenLive = canOpenLive,

                    canWatchReplay = canWatchReplay,

                )

            }

        }

    }

    shareSheetUrl?.let { url ->
        LiveShareSheet(
            title = session.title,
            url = url,
            isReplay = !session.isLive,
            onDismiss = { shareSheetUrl = null },
        )
    }

}

/** 16:9 YouTube-style thumbnail: host art, LIVE/REPLAY badge, viewers and duration overlays. */
@Composable
private fun LiveFeedThumbnail(session: LiveSession) {
    val background = LivePalette.thumbnailPair(session.sessionId.ifBlank { session.hostId })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Brush.linearGradient(background)),
    ) {
        LiveHostAvatar(
            name = session.hostName,
            photoUrl = session.hostProfilePicUrl,
            size = 76.dp,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                session.isLive -> LiveRedBadge()
                session.isArchiveReady -> LiveThumbnailChip("REPLAY")
                session.isArchiveProcessing -> LiveThumbnailChip("PROCESSING")
                session.isScheduled -> LiveThumbnailChip("UPCOMING")
                else -> LiveThumbnailChip("ENDED")
            }
            if (session.viewAccessMode != LiveViewAccessMode.PUBLIC) {
                LiveThumbnailChip(session.viewAccessMode.labelShort)
            }
        }
        if (session.isLive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(999.dp),
                color = LivePalette.Ink.copy(alpha = 0.78f),
            ) {
                LiveViewerCountLabel(
                    count = session.viewerCount,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        val durationMs = session.broadcastDurationMs
        if (!session.isLive && durationMs != null) {
            LiveThumbnailChip(
                text = formatLiveDuration(durationMs),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun LiveThumbnailChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = LivePalette.Ink.copy(alpha = 0.78f),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun liveFeedMetaLine(session: LiveSession, isOwnStream: Boolean): String {
    val host = if (isOwnStream) "Your stream" else session.hostName.ifBlank { "Host" }
    return when {
        session.isLive -> buildList {
            add(host)
            add("${formatCompactCount(session.viewerCount)} watching")
            formatRelativeAgo((session.startTime ?: session.createdAt)?.time)?.let { add("Started $it") }
        }.joinToString(" · ")
        session.isScheduled -> "$host · Upcoming"
        else -> buildList {
            add(host)
            if (session.peakViewerCount > 0L) add("${formatCompactCount(session.peakViewerCount)} peak viewers")
            formatRelativeAgo((session.endedAt ?: session.endTime ?: session.startTime)?.time)?.let { add("Streamed $it") }
        }.joinToString(" · ")
    }
}

/**
 * Links anyone may share without a host-signed token: public live rooms and public replays.
 * Private rooms need the host's signed invite from inside the room.
 */
private fun feedShareUrl(session: LiveSession): String? {
    return when {
        session.isLive && session.viewAccessMode == LiveViewAccessMode.PUBLIC ->
            LiveShareConstants.appDeepLink(sessionId = session.sessionId, hostId = session.hostId)
        session.isArchiveReady && session.replayVisibility == LiveReplayVisibility.PUBLIC ->
            "${LiveShareConstants.WEB_SHARE_HOST}${LiveShareConstants.WEB_LIVE_PATH}" +
                "?sessionId=${android.net.Uri.encode(session.sessionId)}&hostId=${android.net.Uri.encode(session.hostId)}"
        else -> null
    }
}



@Composable

private fun StreamActionButton(

    session: LiveSession,

    onOpenFullScreen: () -> Unit,

    onWatchReplay: (() -> Unit)?,

    onRequestJoin: ((LiveSession) -> Unit)?,

    isRequestPending: Boolean,

    isJoined: Boolean,

    isReplayLoading: Boolean,

    canOpenLive: Boolean,

    canWatchReplay: Boolean,

) {

    if (onRequestJoin != null) {

        // Like YouTube, watching never depends on the stage request.
        if (session.isLive && canOpenLive) {
            ElevatedButton(
                onClick = onOpenFullScreen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = LiveStudioAccent,
                    contentColor = Color.White,
                ),
            ) {
                Text("Watch live", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }

        ElevatedButton(

            onClick = {

                if (!isRequestPending && !isJoined) onRequestJoin(session)

            },

            enabled = !isRequestPending && !isJoined,

            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(12.dp),

            colors = ButtonDefaults.elevatedButtonColors(

                containerColor = LiveStudioAccent,

                contentColor = Color.White,

            ),

        ) {

            when {

                isJoined -> Text("Joined", fontWeight = FontWeight.SemiBold)

                isRequestPending -> Text("Requesting...", fontWeight = FontWeight.SemiBold)

                else -> Text("Request to join stage", fontWeight = FontWeight.SemiBold)

            }

        }

        return

    }



    val watchEnabled = (session.isLive && canOpenLive) ||
        (session.isArchiveReady && !isReplayLoading && canWatchReplay)

    ElevatedButton(

        onClick = {

            when {

                session.isLive && canOpenLive -> onOpenFullScreen()

                session.isArchiveReady && canWatchReplay -> onWatchReplay?.invoke()

            }

        },

        enabled = watchEnabled,

        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (watchEnabled) {
                    Modifier.clip(RoundedCornerShape(12.dp)).background(LivePalette.AccentGradient, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),

        shape = RoundedCornerShape(12.dp),

        colors = ButtonDefaults.elevatedButtonColors(

            containerColor = Color.Transparent,

            contentColor = Color.White,

            disabledContainerColor = Color(0x1F767680),

            disabledContentColor = LiveStudioMuted,

        ),

    ) {

        if (isReplayLoading) {

            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)

        } else {

            Text(

                when {

                    session.isLive && !canOpenLive -> "Restricted"

                    session.isArchiveReady && !canWatchReplay -> "Private replay"

                    session.isLive -> "Watch live"

                    session.isArchiveReady -> "Watch replay"

                    else -> "Ended"

                },

                fontWeight = FontWeight.SemiBold,

            )

        }

    }

}



private fun canOpenLiveFromFeed(

    session: LiveSession,

    isJoined: Boolean,

    currentUserId: String?,

    followingHostIds: Set<String>,

    acceptedEventIds: Set<String>,

): Boolean {

    if (!session.isLive) return false

    if (!currentUserId.isNullOrBlank() && currentUserId == session.hostId) return true

    return when (session.viewAccessMode) {

        LiveViewAccessMode.PUBLIC -> true

        LiveViewAccessMode.FOLLOWERS_ONLY -> session.hostId in followingHostIds

        LiveViewAccessMode.INVITE_ONLY -> isJoined

        LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS ->

            session.linkedEventId?.trim()?.takeIf { it.isNotBlank() } in acceptedEventIds

    }

}



@Composable

private fun LoadingView() {

    Column(

        modifier = Modifier.fillMaxSize(),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center

    ) {

        CircularProgressIndicator(strokeWidth = 3.dp, color = LiveStudioAccent)

        Spacer(Modifier.height(16.dp))

        Text(

            "Tuning into streams...",

            style = MaterialTheme.typography.bodyMedium,

            color = LiveStudioMuted,

            fontWeight = FontWeight.Medium,

        )

    }

}



@Composable

private fun OlderReplaysLoadingRow() {

    Row(

        modifier = Modifier

            .fillMaxWidth()

            .padding(vertical = 4.dp),

        horizontalArrangement = Arrangement.Center,

        verticalAlignment = Alignment.CenterVertically,

    ) {

        CircularProgressIndicator(

            modifier = Modifier.size(16.dp),

            strokeWidth = 2.dp,

            color = LivePalette.Indigo,

        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(

            text = "Searching older replays…",

            style = MaterialTheme.typography.bodySmall,

            color = LiveStudioMuted,

        )

    }

}



@Composable

private fun EmptyStreamsPlaceholder(isSearching: Boolean) {

    val title = if (isSearching) "No results found" else "Quiet on the set"

    val message = if (isSearching) {

        "Try different keywords or clear your search."

    } else {

        "No live broadcasts match this filter. Pull down to refresh."

    }



    Surface(

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(18.dp),

        color = LiveStudioSurface,

    ) {

        Column(

            modifier = Modifier.padding(32.dp),

            horizontalAlignment = Alignment.CenterHorizontally,

        ) {

            Box(

                modifier = Modifier

                    .size(72.dp)

                    .clip(CircleShape)

                    .background(LivePalette.Indigo.copy(alpha = 0.14f)),

                contentAlignment = Alignment.Center,

            ) {

                Icon(

                    imageVector = if (isSearching) Icons.Default.Search else Icons.Default.LiveTv,

                    contentDescription = null,

                    modifier = Modifier.size(36.dp),

                    tint = LiveStudioAccent,

                )

            }

            Spacer(Modifier.height(16.dp))

            Text(

                text = title,

                style = MaterialTheme.typography.titleLarge,

                fontWeight = FontWeight.Bold,

                color = LiveStudioInk,

            )

            Text(

                text = message,

                style = MaterialTheme.typography.bodyMedium,

                color = LiveStudioMuted,

                textAlign = TextAlign.Center,

                modifier = Modifier.padding(top = 6.dp),

            )

        }

    }

}



private val LiveViewAccessMode.labelShort: String

    get() = when (this) {

        LiveViewAccessMode.PUBLIC -> "Public"

        LiveViewAccessMode.FOLLOWERS_ONLY -> "Followers"

        LiveViewAccessMode.INVITE_ONLY -> "Invite"

        LiveViewAccessMode.ACCEPTED_EVENT_VOLUNTEERS -> "Event"

    }


