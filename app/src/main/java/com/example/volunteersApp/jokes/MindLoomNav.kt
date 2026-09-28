package com.example.volunteersApp.jokes

import android.content.Intent

/**
 * Intent extras and route helpers for MindLoom push / deep-link entry (iOS parity).
 */
object MindLoomNav {
    const val EXTRA_OPEN_MINDLOOM = "mindloom_open_feed"
    const val EXTRA_POST_AUTHOR_ID = "mindloom_post_author_id"
    const val EXTRA_POST_ID = "mindloom_post_id"

    const val ROUTE = "jokes"

    fun pendingRouteFromLaunchIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_OPEN_MINDLOOM, false)) {
            return ROUTE
        }
        val type = intent.getStringExtra("type")?.trim().orEmpty()
        if (type.equals("jokesPost", ignoreCase = true)) {
            return ROUTE
        }
        return null
    }

    fun feedIntent(context: android.content.Context): Intent =
        Intent(context, com.example.volunteersApp.ui.main.MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_MINDLOOM, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
