package com.example.volunteersApp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.volunteersApp.chat.declineIncomingCallSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IncomingCallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != IncomingCallNotifications.ACTION_DECLINE) return

        val callId = intent.getStringExtra(IncomingCallNotifications.EXTRA_CALL_ID).orEmpty()
        val chatId = intent.getStringExtra(IncomingCallNotifications.EXTRA_CHAT_ID).orEmpty()
        if (chatId.isBlank()) return

        IncomingCallNotifications.dismiss(context, callId, chatId)

        val pendingResult = goAsync()
        scope.launch {
            try {
                if (callId.isNotBlank()) {
                    declineIncomingCallSession(callId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process incoming call decline action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "IncomingCallActionRx"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
