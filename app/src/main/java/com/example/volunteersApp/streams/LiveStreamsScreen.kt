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



        if (filteredSessions.isNotEmpty() || !uiState.isSearching) {

            item(key = "studio_filters") {

                LiveStudioFilterRow(

                    selectedFilter = uiState.studioFilter,

                    selectedFeed = uiState.studioFeed,

                    onFilterSelected = viewModel::onStudioFilterChange,

                    onFeedSelected = viewModel::onStudioFeedChange,

                )

            }

        }



        if (filteredSessions.isEmpty()) {

            item(key = "studio_empty") {

                EmptyStreamsPlaceholder(isSearching = uiState.isSearching)

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

    val cardGradient = when {

        session.isLive -> listOf(Color(0xFFB42318), Color(0xFFE53935))

        session.isArchiveReady -> listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))

        else -> listOf(Color(0xFF475569), Color(0xFF64748B))

    }



    Surface(

        onClick = {

            when {

                session.isLive && !isRequestPending && canOpenLive -> onOpenFullScreen()

                session.isArchiveReady && canWatchReplay -> onWatchReplay?.invoke()

            }

        },

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(18.dp),

        color = LiveStudioSurface,

        shadowElevation = 1.dp,

        tonalElevation = 0.dp,

    ) {

        Column {

            Box(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(6.dp)

                    .background(Brush.horizontalGradient(cardGradient))

            )

            Column(modifier = Modifier.padding(14.dp)) {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically,

                ) {

                    LiveStatusPill(

                        label = when {

                            session.isLive -> "LIVE"

                            session.isArchiveReady -> "REPLAY"

                            else -> "ENDED"

                        },

                        isLive = session.isLive,

                    )

                    LiveStatusPill(

                        label = session.viewAccessMode.labelShort,

                        isLive = false,

                    )

                }



                Spacer(Modifier.height(12.dp))



                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(

                        modifier = Modifier

                            .size(42.dp)

                            .clip(CircleShape)

                            .background(LiveStudioAccentSoft),

                        contentAlignment = Alignment.Center,

                    ) {

                        Text(

                            text = session.hostName.firstOrNull()?.uppercase() ?: "?",

                            style = MaterialTheme.typography.titleMedium,

                            fontWeight = FontWeight.Bold,

                            color = LiveStudioAccent,

                        )

                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {

                        Text(

                            text = session.title.ifBlank { "Live Session" },

                            style = MaterialTheme.typography.titleMedium,

                            fontWeight = FontWeight.SemiBold,

                            maxLines = 1,

                            overflow = TextOverflow.Ellipsis,

                        )

                        Text(

                            text = session.hostName.ifBlank { "Host" },

                            style = MaterialTheme.typography.bodySmall,

                            color = LiveStudioMuted,

                            maxLines = 1,

                            overflow = TextOverflow.Ellipsis,

                        )

                    }

                    if (session.isLive) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Icon(

                                Icons.Default.Visibility,

                                contentDescription = null,

                                modifier = Modifier.size(14.dp),

                                tint = LiveStudioMuted,

                            )

                            Spacer(Modifier.width(4.dp))

                            Text(

                                text = "${session.viewerCount}",

                                style = MaterialTheme.typography.labelMedium,

                                color = LiveStudioMuted,

                                fontWeight = FontWeight.Medium,

                            )

                        }

                    }

                }



                if (session.description.isNotBlank()) {

                    Spacer(Modifier.height(8.dp))

                    Text(

                        text = session.description,

                        style = MaterialTheme.typography.bodySmall,

                        color = LiveStudioMuted,

                        maxLines = 2,

                        overflow = TextOverflow.Ellipsis,

                    )

                }



                Spacer(Modifier.height(14.dp))



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



    ElevatedButton(

        onClick = {

            when {

                session.isLive && canOpenLive -> onOpenFullScreen()

                session.isArchiveReady && canWatchReplay -> onWatchReplay?.invoke()

            }

        },

        enabled = (session.isLive && canOpenLive) ||

            (session.isArchiveReady && !isReplayLoading && canWatchReplay),

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(12.dp),

        colors = ButtonDefaults.elevatedButtonColors(

            containerColor = LiveStudioAccent,

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

                    .background(LiveStudioAccentSoft),

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


