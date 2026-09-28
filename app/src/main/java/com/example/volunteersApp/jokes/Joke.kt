package com.example.volunteersApp.jokes

import android.net.Uri
import androidx.annotation.Keep
import com.example.volunteersApp.streams.LiveLaunchTarget
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

const val MINDLOOM_POST_TEXT_MAX_LENGTH = 5_000
const val MINDLOOM_COMMENT_TEXT_MAX_LENGTH = 2_000
const val MINDLOOM_COMMENTS_PAGE_SIZE = 50
const val MINDLOOM_FEED_PAGE_SIZE = 100
const val MINDLOOM_FALLBACK_POSTS_PER_AUTHOR = 25
const val MINDLOOM_FALLBACK_AUTHOR_LIMIT = 50
const val MINDLOOM_PROFILE_POSTS_PAGE_SIZE = 100

// Enum to define the types of media content.
@Keep
enum class JokeType {
    TEXT,
    IMAGE,
    VIDEO,
    DOCUMENT,
    LIVE_SESSION,
    LIVE_REPLAY,
}

// Data class for comments.
@Keep
data class Comment(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileUrl: String? = null, // Added for commenter's image
    val text: String = "",
    val isOwnerResponse: Boolean = false,
    @ServerTimestamp
    val timestamp: Timestamp? = null
)


@Keep
data class Joke(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileUrl: String? = null,
    val text: String = "",
    val mediaUrl: String? = null,
    val mediaType: String = JokeType.TEXT.name, // Stored as a String in Firestore
    /** Legacy live session id field; prefer [sourceLiveSessionId]. */
    val liveSessionId: String? = null,
    val sourceType: String? = null,
    val sourceLiveSessionId: String? = null,
    val sourceLiveHostId: String? = null,
    val sourceReplayVisibility: String? = null,
    val sourceLiveShareUrl: String? = null,
    val likes: List<String> = emptyList(),
    val commentsCount: Int = 0,
    val status: String = "ACTIVE",
    val isDeleted: Boolean = false,
    @ServerTimestamp
    val timestamp: Timestamp? = null

) {
    @get:Exclude
    val likesCount: Int get() = likes.size
}

/**
 * Stable list identity for Compose / MindLoom when [id] is missing or blank (mirrors iOS `mindLoomStableRowId`).
 */
fun Joke.mindLoomStableRowId(): String {
    val trimmed = id.trim()
    if (trimmed.isNotEmpty()) return trimmed
    val ts = timestamp?.toDate()?.time ?: 0L
    val mediaPrefix = (mediaUrl ?: "").trim().take(24).replace("/", "_")
    val aid = authorId.trim().ifEmpty { "anon" }
    return "ml_${aid}_${mediaPrefix}_$ts"
}

fun Joke.mindLoomMediaType(): JokeType? =
    runCatching { JokeType.valueOf(mediaType) }.getOrNull()

/** Resolved live session id for LIVE_SESSION / LIVE_REPLAY promo posts. */
fun Joke.mindLoomLiveSessionId(): String? =
    sequenceOf(sourceLiveSessionId, liveSessionId)
        .map { it?.trim().orEmpty() }
        .firstOrNull { it.isNotBlank() }

/** Launch target for live promo posts. Replay access is renewed from its session id. */
fun Joke.mindLoomLiveLaunchTarget(): LiveLaunchTarget? {
    val sessionId = mindLoomLiveSessionId() ?: return null
    val shareUrl = sourceLiveShareUrl?.trim()?.ifBlank { null }
    val shareToken = shareUrl?.let { url ->
        runCatching { Uri.parse(url).getQueryParameter("token")?.trim() }.getOrNull()
    }?.ifBlank { null }
    return LiveLaunchTarget(
        sessionId = sessionId,
        hostId = sourceLiveHostId?.trim()?.ifBlank { null },
        shareAccessToken = shareToken,
    )
}

/** Signed replay links are only used to renew access for the shared-link visibility mode. */
fun Joke.mindLoomReplayShareToken(): String? {
    if (mindLoomMediaType() != JokeType.LIVE_REPLAY) return null
    return runCatching { Uri.parse(mediaUrl).getQueryParameter("token")?.trim() }.getOrNull()
        ?.ifBlank { null }
}

/** Share text for MindLoom posts — includes live/replay links when available. */
fun Joke.mindLoomShareText(): String {
    val author = authorName.ifBlank { "Someone" }
    val link = when (mindLoomMediaType()) {
        JokeType.LIVE_SESSION -> sourceLiveShareUrl?.trim()?.ifBlank { null } ?: mediaUrl?.trim()?.ifBlank { null }
        JokeType.LIVE_REPLAY -> mediaUrl?.trim()?.ifBlank { null }
        else -> null
    }
    return buildString {
        append("$author on MindLoom")
        val caption = text.trim()
        if (caption.isNotBlank()) {
            append(": ")
            append(caption.take(200))
        }
        if (!link.isNullOrBlank()) {
            append("\n\n")
            append(link)
        }
    }
}

/** Derived @handle from author name snapshot (iOS MindLoom feed parity). */
fun Joke.mindLoomDerivedHandle(): String {
    val raw = authorName.trim()
    if (raw.isNotBlank() && !raw.contains("@")) {
        val cleaned = raw.lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9._]"), "")
            .trim('_')
        if (cleaned.isNotBlank()) return cleaned
    }
    val aid = authorId.trim()
    return if (aid.length >= 6) "user_${aid.take(6).lowercase()}" else "creator"
}
