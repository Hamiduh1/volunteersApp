package com.example.volunteersApp.notifications

import android.util.Log
import java.util.Collections

/**
 * Deduplicates local ringing presentation without mutating server call signaling.
 */
object CallRingingReceipt {
    private const val TAG = "CallRingingReceipt"
    private val markedCallIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun mark(callId: String) {
        val trimmed = callId.trim()
        if (trimmed.isBlank()) return
        if (!markedCallIds.add(trimmed)) return
        Log.d(TAG, "Incoming call presentation marked locally for $trimmed")
    }
}
