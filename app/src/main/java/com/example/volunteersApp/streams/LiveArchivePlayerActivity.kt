package com.example.volunteersApp.streams

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.google.firebase.firestore.Query
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LiveArchivePlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Live Replay" }
        val shareAccessToken = intent.getStringExtra(EXTRA_REPLAY_SHARE_TOKEN)
            ?.trim()
            ?.ifBlank { null }
        if (sessionId.isBlank() || !LiveLaunchIntent.isValidLiveDocId(sessionId)) {
            Toast.makeText(this, "Replay is unavailable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            VolunteersAppTheme {
                LiveArchivePlayerScreen(
                    sessionId = sessionId,
                    title = title,
                    onBack = { finish() },
                    loadReplayUrl = { callback ->
                        lifecycleScope.launch {
                            runCatching {
                                LiveRepository.createLiveReplayAccessLink(
                                    sessionId = sessionId,
                                    shareAccessToken = shareAccessToken,
                                )
                            }.onSuccess(callback).onFailure {
                                Toast.makeText(
                                    this@LiveArchivePlayerActivity,
                                    "We couldn't open this replay. Refresh and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_live_archive_session_id"
        const val EXTRA_TITLE = "extra_live_archive_title"
        const val EXTRA_REPLAY_SHARE_TOKEN = "extra_live_archive_share_token"
    }
}

private const val CHAT_REPLAY_LIMIT = 300L

/** YouTube-style watch page: 16:9 player on top, details and actions underneath. */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
private fun LiveArchivePlayerScreen(
    sessionId: String,
    title: String,
    onBack: () -> Unit,
    loadReplayUrl: (onReady: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = remember { Firebase.auth.currentUser?.uid }
    var playbackUrl by remember(sessionId) { mutableStateOf<String?>(null) }
    var isLoading by remember(sessionId) { mutableStateOf(true) }
    var session by remember(sessionId) { mutableStateOf<LiveSession?>(null) }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var shareBusy by remember { mutableStateOf(false) }
    var mindLoomBusy by remember { mutableStateOf(false) }
    var chatReplay by remember(sessionId) { mutableStateOf<List<LiveRoomComment>>(emptyList()) }
    var chatReplayExpanded by remember(sessionId) { mutableStateOf(false) }
    var chatReplayUnavailable by remember(sessionId) { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        chatReplay = runCatching {
            Firebase.firestore.collection(FirestoreCollection.LIVE_SESSIONS)
                .document(sessionId)
                .collection(FirestoreSubcollection.COMMENTS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(CHAT_REPLAY_LIMIT)
                .get()
                .await()
                .documents
                .map { doc ->
                    LiveRoomComment(
                        id = doc.id,
                        authorId = doc.getString("authorId").orEmpty(),
                        authorName = doc.getString("authorName").orEmpty().ifBlank { "Anonymous" },
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt"),
                        replyToCommentId = doc.getString("replyToCommentId")?.trim()?.ifBlank { null },
                        replyToAuthorName = doc.getString("replyToAuthorName")?.trim()?.ifBlank { null },
                        replyToText = doc.getString("replyToText")?.trim()?.ifBlank { null },
                        authorPhotoUrl = doc.getString("authorPhotoUrl")?.trim()?.ifBlank { null },
                    )
                }
        }.onSuccess { chatReplayUnavailable = false }
            // Chat after a stream follows replay visibility; shared-link viewers are denied by rules.
            .onFailure { chatReplayUnavailable = true }
            .getOrElse { emptyList() }
    }

    LaunchedEffect(sessionId) {
        isLoading = true
        loadReplayUrl { url ->
            playbackUrl = url
            isLoading = false
        }
    }
    LaunchedEffect(sessionId) {
        session = runCatching {
            Firebase.firestore.collection(FirestoreCollection.LIVE_SESSIONS)
                .document(sessionId)
                .get()
                .await()
                .toLiveSession()
        }.getOrNull()
    }

    val details = session
    val isHost = details != null && !currentUid.isNullOrBlank() && currentUid == details.hostId
    val publicReplayUrl = details?.takeIf { it.replayVisibility == LiveReplayVisibility.PUBLIC }?.let {
        "${LiveShareConstants.WEB_SHARE_HOST}${LiveShareConstants.WEB_LIVE_PATH}" +
            "?sessionId=${Uri.encode(it.sessionId)}&hostId=${Uri.encode(it.hostId)}"
    }

    val onShare: () -> Unit = {
        if (!shareBusy) {
            scope.launch {
                shareBusy = true
                runCatching {
                    when {
                        isHost -> LiveRepository.createLiveReplayAccessLink(sessionId, purpose = "share")
                        publicReplayUrl != null -> publicReplayUrl
                        else -> throw IllegalStateException("Only the host can share this replay.")
                    }
                }.onSuccess { shareUrl = it }.onFailure { error ->
                    Toast.makeText(context, error.localizedMessage ?: "Could not create a share link.", Toast.LENGTH_LONG).show()
                }
                shareBusy = false
            }
        }
    }

    val onPostToMindLoom: () -> Unit = {
        val current = details
        if (current != null && !mindLoomBusy) {
            scope.launch {
                mindLoomBusy = true
                runCatching {
                    if (current.replayVisibility == LiveReplayVisibility.OWNER_ONLY) {
                        throw IllegalStateException("Change replay visibility before posting it to MindLoom.")
                    }
                    val replayUrl = LiveRepository.createLiveReplayAccessLink(
                        sessionId = current.sessionId,
                        purpose = if (current.replayVisibility == LiveReplayVisibility.SHARED_LINK) "share" else null,
                    )
                    MindLoomLivePostWriter.postLiveReplay(current, replayUrl).getOrThrow()
                }.onSuccess {
                    Toast.makeText(context, "Replay posted to MindLoom.", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, error.localizedMessage ?: "Could not post to MindLoom.", Toast.LENGTH_LONG).show()
                }
                mindLoomBusy = false
            }
        }
    }

    LiveStudioTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LiveStudioBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text("Loading replay…", color = Color.White.copy(alpha = 0.85f))
                    }
                    playbackUrl.isNullOrBlank() -> Text(
                        "Replay unavailable",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    else -> LiveArchiveExoPlayer(playbackUrl = playbackUrl!!)
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = details?.title?.ifBlank { null } ?: title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LiveStudioInk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                details?.let { replaySession ->
                    val meta = buildList {
                        if (replaySession.peakViewerCount > 0L) {
                            add("${formatCompactCount(replaySession.peakViewerCount)} peak viewers")
                        }
                        formatRelativeAgo((replaySession.endedAt ?: replaySession.startTime)?.time)
                            ?.let { add("Streamed $it") }
                        replaySession.broadcastDurationMs?.let { add(formatLiveDuration(it)) }
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = LiveStudioMuted)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isHost || publicReplayUrl != null) {
                        ReplayActionPill(
                            icon = Icons.Default.Share,
                            label = if (shareBusy) "Preparing…" else "Share",
                            onClick = onShare,
                        )
                    }
                    if (isHost) {
                        ReplayActionPill(
                            icon = Icons.Default.AutoAwesome,
                            label = if (mindLoomBusy) "Posting…" else "Post to MindLoom",
                            onClick = onPostToMindLoom,
                        )
                    }
                }

                details?.let { replaySession ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveHostAvatar(
                            name = replaySession.hostName,
                            photoUrl = replaySession.hostProfilePicUrl,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                replaySession.hostName.ifBlank { "Host" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LiveStudioInk,
                            )
                            Text(
                                if (isHost) "Your broadcast" else "Host",
                                style = MaterialTheme.typography.bodySmall,
                                color = LiveStudioMuted,
                            )
                        }
                    }
                    if (replaySession.description.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F1F1),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                replaySession.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LiveStudioInk,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                if (chatReplayUnavailable) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F1F1),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Chat replay isn't available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LiveStudioMuted,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (chatReplay.isNotEmpty()) {
                    val streamStartMs = (details?.startTime ?: details?.createdAt)?.time
                    Surface(
                        onClick = { chatReplayExpanded = !chatReplayExpanded },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F1F1),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Live chat replay",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LiveStudioInk,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    formatCompactCount(chatReplay.size.toLong()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LiveStudioMuted,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (chatReplayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (chatReplayExpanded) "Hide chat replay" else "Show chat replay",
                                    tint = LiveStudioMuted,
                                )
                            }
                            if (!chatReplayExpanded) {
                                chatReplay.last().let { latest ->
                                    Spacer(Modifier.height(8.dp))
                                    LiveChatMessageRow(
                                        comment = latest,
                                        isHostAuthor = latest.authorId == details?.hostId,
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(6.dp))
                                chatReplay.forEach { comment ->
                                    val offsetMs = comment.createdAt?.toDate()?.time?.let { sentAt ->
                                        streamStartMs?.let { start -> (sentAt - start).coerceAtLeast(0L) }
                                    }
                                    LiveChatMessageRow(
                                        comment = comment,
                                        isHostAuthor = comment.authorId == details?.hostId,
                                        timeLabel = offsetMs?.let(::formatLiveDuration),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        val activeShareUrl = shareUrl
        if (activeShareUrl != null) {
            LiveShareSheet(
                title = details?.title?.ifBlank { null } ?: title,
                url = activeShareUrl,
                isReplay = true,
                onDismiss = { shareUrl = null },
                onPostToMindLoom = if (isHost) onPostToMindLoom else null,
                mindLoomInProgress = mindLoomBusy,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplayActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xFFF1F1F1),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = LiveStudioInk, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = LiveStudioInk)
        }
    }
}

@UnstableApi
@Composable
private fun LiveArchiveExoPlayer(playbackUrl: String) {
    val context = LocalContext.current
    val player = remember(playbackUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(playbackUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
