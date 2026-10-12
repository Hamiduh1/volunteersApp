package com.example.volunteersApp.ui.main

import android.content.Intent
import java.util.Locale

/**
 * Deep-link / notification extras for Dating Hub (parity with iOS pending Blind Date destination).
 * FCM or widgets can set [EXTRA_OPEN_BLIND_DATE] and navigate route `date_eva`; [MainViewModel] queues blind flow.
 */
object DateHubNav {
    const val EXTRA_OPEN_BLIND_DATE = "date_hub_open_blind_date"
    const val EXTRA_OPEN_DATING_LOOP = "date_hub_open_dating_loop"

    /** Push `type` values sent by the dating push functions (iOS DatingPushRoute parity). */
    val DATING_LOOP_PUSH_TYPES = setOf("dating_like", "dating_super_like", "dating_match", "dating_comment")
    val BLIND_DATE_PUSH_TYPES = setOf("blind_date_invite", "blind_date_match")

    private fun pushType(intent: Intent?): String =
        intent?.getStringExtra("type")?.trim()?.lowercase(Locale.ROOT).orEmpty()

    fun isDatingPushType(type: String?): Boolean {
        val normalized = type?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return normalized in DATING_LOOP_PUSH_TYPES || normalized in BLIND_DATE_PUSH_TYPES
    }

    fun shouldOpenBlindDateFromIntent(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_BLIND_DATE, false) == true ||
            pushType(intent) in BLIND_DATE_PUSH_TYPES

    fun shouldOpenDatingLoopFromIntent(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_DATING_LOOP, false) == true ||
            pushType(intent) in DATING_LOOP_PUSH_TYPES
}
