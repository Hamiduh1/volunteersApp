package com.example.volunteersApp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.volunteersApp.R
import com.example.volunteersApp.chat.CallActivity
import com.example.volunteersApp.chat.CallType

/**
 * Persistent "return to call" notification while an in-app call stays active
 * after the user minimizes to Picture-in-Picture or leaves CallActivity in the stack.
 */
object OngoingCallNotifications {
    const val CHANNEL_ID = "ongoing_calls"
    private const val CONTENT_CODE = 41

    fun notificationId(callId: String, chatId: String): Int {
        val key = callId.ifBlank { chatId }
        return ("ongoing_call_$key").hashCode()
    }

    fun show(
        context: Context,
        chatId: String,
        callId: String?,
        otherUserId: String?,
        otherUserName: String,
        callType: CallType,
        isCaller: Boolean,
        statusLabel: String,
    ) {
        if (chatId.isBlank()) return
        val appContext = context.applicationContext
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val contentIntent = CallActivity.newIntent(
            context = appContext,
            chatId = chatId,
            otherUserId = otherUserId,
            callType = callType,
            isCaller = isCaller,
            callId = callId,
            autoAccept = false,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            requestCode(callId.orEmpty(), chatId, CONTENT_CODE),
            contentIntent,
            pendingIntentFlags(),
        )

        val title = if (callType == CallType.VIDEO) {
            "Video call with $otherUserName"
        } else {
            "Voice call with $otherUserName"
        }
        val text = statusLabel.ifBlank { "Tap to return to the call" }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(notificationId(callId.orEmpty(), chatId), notification)
    }

    fun dismiss(context: Context, callId: String?, chatId: String) {
        if (chatId.isBlank() && callId.isNullOrBlank()) return
        val notificationManager =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId(callId.orEmpty(), chatId))
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ongoing calls",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Keeps an active call reachable while you use the rest of the app"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun requestCode(callId: String, chatId: String, salt: Int): Int {
        return (callId.ifBlank { chatId }.hashCode() xor salt)
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
