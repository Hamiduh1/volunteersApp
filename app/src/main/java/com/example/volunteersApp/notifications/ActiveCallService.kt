package com.example.volunteersApp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.volunteersApp.R
import com.example.volunteersApp.chat.CallActivity
import com.example.volunteersApp.chat.CallType

/**
 * Keeps microphone and camera access process-priority-safe while an established call is active.
 * It is started only from the visible call activity, which satisfies Android's foreground-service
 * start restrictions and prevents a locked screen from tearing down an active RTC connection.
 */
class ActiveCallService : Service() {
    private var activeCallId: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val requestedCallId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
                if (requestedCallId.isBlank() || requestedCallId == activeCallId) {
                    stopActiveService()
                }
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
                val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
                if (chatId.isBlank() || callId.isBlank()) {
                    stopActiveService()
                    return START_NOT_STICKY
                }
                activeCallId = callId
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE).orEmpty()
                val isCaller = intent.getBooleanExtra(EXTRA_IS_CALLER, true)
                val otherUserId = intent.getStringExtra(EXTRA_OTHER_USER_ID)
                startForegroundForCall(
                    chatId = chatId,
                    callId = callId,
                    callType = callType,
                    isCaller = isCaller,
                    otherUserId = otherUserId,
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startForegroundForCall(
        chatId: String,
        callId: String,
        callType: String,
        isCaller: Boolean,
        otherUserId: String?,
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val resolvedType = if (callType.equals("video", ignoreCase = true)) CallType.VIDEO else CallType.AUDIO
        val returnIntent = CallActivity.newIntent(
            context = this,
            chatId = chatId,
            otherUserId = otherUserId,
            callType = resolvedType,
            isCaller = isCaller,
            callId = callId,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId(callId, chatId),
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (resolvedType == CallType.VIDEO) "Video call in progress" else "Voice call in progress")
            .setContentText("Tap to return to your call")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            if (resolvedType == CallType.VIDEO) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId(callId, chatId), notification, serviceType)
        } else {
            startForeground(notificationId(callId, chatId), notification)
        }
    }

    private fun stopActiveService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active calls",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Keeps active calls connected when the screen is locked"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    companion object {
        private const val ACTION_START = "com.example.volunteersApp.notifications.ACTIVE_CALL_START"
        private const val ACTION_STOP = "com.example.volunteersApp.notifications.ACTIVE_CALL_STOP"
        private const val EXTRA_CHAT_ID = "extra_chat_id"
        private const val EXTRA_CALL_ID = "extra_call_id"
        private const val EXTRA_OTHER_USER_ID = "extra_other_user_id"
        private const val EXTRA_CALL_TYPE = "extra_call_type"
        private const val EXTRA_IS_CALLER = "extra_is_caller"
        private const val CHANNEL_ID = OngoingCallNotifications.CHANNEL_ID
        private const val TAG = "ActiveCallService"

        fun start(
            context: Context,
            chatId: String,
            callId: String,
            otherUserId: String?,
            callType: CallType,
            isCaller: Boolean,
        ) {
            if (chatId.isBlank() || callId.isBlank()) return
            val intent = Intent(context, ActiveCallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_OTHER_USER_ID, otherUserId)
                putExtra(EXTRA_CALL_TYPE, callType.name)
                putExtra(EXTRA_IS_CALLER, isCaller)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                // A policy or notification setting can reject the service. The visible call must
                // remain usable instead of failing because this extra protection was unavailable.
                Log.w(TAG, "Unable to start active-call foreground service", error)
            }
        }

        fun stop(context: Context, callId: String) {
            val intent = Intent(context, ActiveCallService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_CALL_ID, callId)
            }
            runCatching { context.startService(intent) }
                .onFailure { error -> Log.w(TAG, "Unable to stop active-call foreground service", error) }
        }

        private fun notificationId(callId: String, chatId: String): Int =
            OngoingCallNotifications.notificationId(callId, chatId)
    }
}
