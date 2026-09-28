package com.example.volunteersApp.streams

import android.content.Context
import android.content.Intent

object LiveArchivePlayerIntent {
    fun create(
        context: Context,
        sessionId: String,
        title: String? = null,
        shareAccessToken: String? = null,
    ): Intent {
        return Intent(context, LiveArchivePlayerActivity::class.java).apply {
            putExtra(LiveArchivePlayerActivity.EXTRA_SESSION_ID, sessionId)
            title?.let { putExtra(LiveArchivePlayerActivity.EXTRA_TITLE, it) }
            shareAccessToken?.takeIf { it.isNotBlank() }?.let {
                putExtra(LiveArchivePlayerActivity.EXTRA_REPLAY_SHARE_TOKEN, it)
            }
        }
    }
}
