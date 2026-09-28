package com.example.volunteersApp.streams

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.volunteersApp.general.LoginActivity

data class LiveLaunchTarget(
    val sessionId: String,
    val hostId: String? = null,
    val shareAccessToken: String? = null,
)

object LiveLaunchIntent {
    const val EXTRA_LIVE_SESSION_ID = "live_session_id"
    const val EXTRA_LIVE_HOST_ID = "live_host_id"
    const val EXTRA_LIVE_SHARE_TOKEN = "live_share_token"

    fun parse(intent: Intent?): LiveLaunchTarget? {
        if (intent == null) return null

        val extraSessionId = intent.getStringExtra(EXTRA_LIVE_SESSION_ID)?.trim().orEmpty()
        val extraHostId = intent.getStringExtra(EXTRA_LIVE_HOST_ID)?.trim().orEmpty()
        val extraToken = intent.getStringExtra(EXTRA_LIVE_SHARE_TOKEN)?.trim().orEmpty()
        if (extraSessionId.isNotBlank()) {
            return LiveLaunchTarget(
                sessionId = extraSessionId,
                hostId = extraHostId.ifBlank { null },
                shareAccessToken = extraToken.ifBlank { null },
            )
        }

        val data = intent.data ?: return null
        return parse(data)
    }

    fun parse(uri: Uri): LiveLaunchTarget? {
        val sessionId = sequenceOf(
            uri.getQueryParameter("sessionId"),
            uri.getQueryParameter("referenceId"),
            uri.lastPathSegment?.takeIf { uri.path?.startsWith("/stream/") == true },
        ).map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        val hostId = sequenceOf(
            uri.getQueryParameter("hostId"),
            uri.getQueryParameter("actorId"),
        ).map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotBlank() }

        val token = uri.getQueryParameter("token")?.trim().orEmpty().ifBlank { null }

        return LiveLaunchTarget(
            sessionId = sessionId,
            hostId = hostId,
            shareAccessToken = token,
        )
    }

    fun loginIntent(context: Context, target: LiveLaunchTarget): Intent {
        return Intent(context, LoginActivity::class.java).apply {
            putExtra(EXTRA_LIVE_SESSION_ID, target.sessionId)
            target.hostId?.let { putExtra(EXTRA_LIVE_HOST_ID, it) }
            target.shareAccessToken?.let { putExtra(EXTRA_LIVE_SHARE_TOKEN, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun liveRoomIntent(context: Context, target: LiveLaunchTarget, isHost: Boolean = false): Intent {
        return Intent(context, LiveStreamActivity::class.java).apply {
            putExtra(EXTRA_LIVE_SESSION_ID, target.sessionId)
            target.hostId?.let { putExtra(EXTRA_LIVE_HOST_ID, it) }
            target.shareAccessToken?.let { putExtra(EXTRA_LIVE_SHARE_TOKEN, it) }
            putExtra("IS_HOST", isHost)
        }
    }
}
