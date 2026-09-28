package com.example.volunteersApp.streams

import android.content.Context
import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

object LiveShareRouter {

    suspend fun launch(context: Context, target: LiveLaunchTarget) {
        val sessionId = target.sessionId.trim()
        if (sessionId.isBlank()) {
            throw IllegalStateException("Live session id is missing.")
        }

        val shareToken = target.shareAccessToken?.trim()?.ifBlank { null }
        if (!shareToken.isNullOrBlank()) {
            val resolved = LiveRepository.resolveLiveShareAccess(shareToken)
            if (!resolved.valid) {
                throw IllegalStateException("This live invite link is invalid or expired.")
            }
            val resolvedSessionId = resolved.sessionId.ifBlank { sessionId }
            val resolvedHostId = resolved.hostId.ifBlank { target.hostId.orEmpty() }
            if (target.hostId != null && resolvedHostId.isNotBlank() && target.hostId != resolvedHostId) {
                throw IllegalStateException("This live invite link does not match the host.")
            }
            if (resolved.replayReady || resolved.status.equals("ended", ignoreCase = true)) {
                context.startActivity(
                    LiveArchivePlayerIntent.create(
                        context = context,
                        sessionId = resolvedSessionId,
                        title = resolved.title.ifBlank { "Live Replay" },
                    )
                )
                return
            }
            context.startActivity(
                LiveLaunchIntent.liveRoomIntent(
                    context = context,
                    target = target.copy(
                        sessionId = resolvedSessionId,
                        hostId = resolvedHostId.ifBlank { target.hostId },
                    ),
                    isHost = false,
                )
            )
            return
        }

        val session = Firebase.firestore
            .collection(FirestoreCollection.LIVE_SESSIONS)
            .document(sessionId)
            .get()
            .await()
            .toLiveSession()

        if (session != null) {
            target.hostId?.takeIf { it.isNotBlank() }?.let { expectedHost ->
                if (session.hostId.isNotBlank() && expectedHost != session.hostId) {
                    throw IllegalStateException("This live session link does not match the host.")
                }
            }
            if (session.isEnded && session.isArchiveReady) {
                context.startActivity(
                    LiveArchivePlayerIntent.create(
                        context = context,
                        sessionId = session.sessionId,
                        title = session.title,
                    )
                )
                return
            }
        }

        context.startActivity(LiveLaunchIntent.liveRoomIntent(context, target, isHost = false))
    }
}
