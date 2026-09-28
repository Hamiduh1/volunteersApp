package com.example.volunteersApp.ui.main

import android.content.Intent

/**
 * Deep-link / notification extras for Dating Hub (parity with iOS pending Blind Date destination).
 * FCM or widgets can set [EXTRA_OPEN_BLIND_DATE] and navigate route `date_eva`; [MainViewModel] queues blind flow.
 */
object DateHubNav {
    const val EXTRA_OPEN_BLIND_DATE = "date_hub_open_blind_date"

    fun shouldOpenBlindDateFromIntent(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_BLIND_DATE, false) == true
}
