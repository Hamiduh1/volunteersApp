package com.example.volunteersApp.notifications

import android.util.Log
import com.example.volunteersApp.chat.CallSessionWriter
import java.util.Collections

/**
 * Records once per call that this device is ringing: deduplicates local ringing presentation and
 * adds this user to the session's `ringingParticipantIds` so the caller sees "Ringing".
 */
object CallRingingReceipt {
    private const val TAG = "CallRingingReceipt"
    private val markedCallIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun mark(callId: String) {
        val trimmed = callId.trim()
        if (trimmed.isBlank()) return
        if (!markedCallIds.add(trimmed)) return
        Log.d(TAG, "Incoming call presentation marked for $trimmed")
        CallSessionWriter.markRingingDetached(trimmed)
    }
}
