package com.example.volunteersApp.streams

import android.net.Uri
import android.util.Log
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.jokes.JokeType
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

private const val TAG = "MindLoomLivePost"

object MindLoomLivePostWriter {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    suspend fun postLiveSession(
        session: LiveSession,
        shareUrl: String,
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sign in to post to MindLoom."))
        // Shared rules accept only the canonical web link as sourceLiveShareUrl; a signed invite
        // link would also hand its token to every MindLoom reader.
        @Suppress("NAME_SHADOWING")
        val shareUrl = canonicalReplayReference(session)
        return runCatching {
            val authorName = user.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: session.hostName.trim().ifBlank { "Host" }
            val text = buildString {
                append(session.title.ifBlank { "Live Session" })
                append("\n")
                append(shareUrl)
            }
            val jokeId = "live_session_${session.sessionId}"
            val payload = mutableMapOf<String, Any>(
                "authorName" to authorName,
                "authorId" to user.uid,
                "text" to text,
                "mediaUrl" to shareUrl,
                "mediaType" to JokeType.LIVE_SESSION.name,
                "liveSessionId" to session.sessionId,
                "sourceLiveSessionId" to session.sessionId,
                "sourceLiveHostId" to session.hostId,
                "sourceLiveShareUrl" to shareUrl,
                "status" to "ACTIVE",
                "likes" to emptyList<String>(),
                "commentsCount" to 0,
                "timestamp" to FieldValue.serverTimestamp(),
            )
            user.photoUrl?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
                payload["authorProfileUrl"] = url
            }
            writeLivePost(user.uid, jokeId, payload)
            Unit
        }.onFailure { error ->
            Log.e(TAG, "Failed to post live session to MindLoom", error)
        }
    }

    suspend fun postLiveReplay(
        session: LiveSession,
        replayUrl: String,
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sign in to post to MindLoom."))
        if (!session.isArchiveReady) {
            return Result.failure(IllegalStateException("Replay is not ready yet."))
        }
        if (session.replayVisibility == LiveReplayVisibility.OWNER_ONLY) {
            return Result.failure(
                IllegalStateException("Owner-only replays cannot be posted to MindLoom.")
            )
        }
        return runCatching {
            val authorName = user.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: session.hostName.trim().ifBlank { "Host" }
            val replayReference = if (session.replayVisibility == LiveReplayVisibility.SHARED_LINK) {
                replayUrl
            } else {
                canonicalReplayReference(session)
            }
            val text = buildString {
                append("Replay: ")
                append(session.title.ifBlank { "Live Session" })
                append("\n")
                append(replayReference)
            }
            val jokeId = "live_replay_${session.sessionId}"
            val payload = mutableMapOf<String, Any>(
                "authorName" to authorName,
                "authorId" to user.uid,
                "text" to text,
                "mediaUrl" to replayReference,
                "mediaType" to JokeType.LIVE_REPLAY.name,
                "liveSessionId" to session.sessionId,
                "sourceLiveSessionId" to session.sessionId,
                "sourceLiveHostId" to session.hostId,
                "sourceReplayVisibility" to session.replayVisibility.raw,
                "status" to "ACTIVE",
                "likes" to emptyList<String>(),
                "commentsCount" to 0,
                "timestamp" to FieldValue.serverTimestamp(),
            )
            user.photoUrl?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
                payload["authorProfileUrl"] = url
            }
            writeLivePost(user.uid, jokeId, payload)
            Unit
        }.onFailure { error ->
            Log.e(TAG, "Failed to post live replay to MindLoom", error)
        }
    }

    suspend fun resolveLiveShareUrl(session: LiveSession): String {
        return runCatching {
            LiveRepository.createLiveShareAccessLink(session.sessionId)
        }.getOrElse { error ->
            Log.w(TAG, "Signed live share unavailable; using app deep link", error)
            if (session.viewAccessMode == LiveViewAccessMode.PUBLIC) {
                LiveShareConstants.appDeepLink(
                    sessionId = session.sessionId,
                    hostId = session.hostId,
                )
            } else {
                throw error
            }
        }
    }

    /**
     * Re-posting updates the existing card: commentsCount is server-maintained and likes belong to
     * readers, so an overwrite with 0 / [] would be denied once anyone has engaged.
     */
    private suspend fun writeLivePost(uid: String, jokeId: String, payload: Map<String, Any>) {
        val ref = db.collection(FirestoreCollection.USERS)
            .document(uid)
            .collection(FirestoreSubcollection.JOKES)
            .document(jokeId)
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(false)
        if (exists) {
            ref.set(payload - "commentsCount" - "likes", SetOptions.merge()).await()
        } else {
            ref.set(payload).await()
        }
    }

    private fun canonicalReplayReference(session: LiveSession): String {
        val sessionId = Uri.encode(session.sessionId)
        val hostId = Uri.encode(session.hostId)
        return "${LiveShareConstants.WEB_SHARE_HOST}${LiveShareConstants.WEB_LIVE_PATH}" +
            "?sessionId=$sessionId&hostId=$hostId"
    }
}
