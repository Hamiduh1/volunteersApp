package com.example.volunteersApp.chat

import android.util.Log

private const val TAG = "IncomingCallRemote"

/**
 * Declines an incoming call from a notification action without opening [CallActivity].
 * Mirrors [CallSessionViewModel.declineSession] for Firestore consistency.
 */
suspend fun declineIncomingCallSession(sessionId: String): Boolean {
    val cleanSessionId = sessionId.trim()
    if (cleanSessionId.isBlank()) return false

    return try {
        CallSessionRepository.declineIncomingSession(cleanSessionId)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decline incoming call sessionId=$cleanSessionId", e)
        false
    }
}
