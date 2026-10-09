package com.example.volunteersApp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.volunteersApp.R
import com.example.volunteersApp.chat.CallActivity
import com.example.volunteersApp.chat.CallSession
import com.example.volunteersApp.chat.CallType

object IncomingCallNotifications {
    const val CHANNEL_ID = "incoming_calls"
    const val ACTION_ANSWER = "com.example.volunteersApp.notifications.INCOMING_CALL_ANSWER"
    const val ACTION_DECLINE = "com.example.volunteersApp.notifications.INCOMING_CALL_DECLINE"

    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_CALLER_ID = "extra_caller_id"
    const val EXTRA_CALL_TYPE = "extra_call_type"
    const val EXTRA_IS_GROUP = "extra_is_group"
    const val EXTRA_CALLER_NAME = "extra_caller_name"

    private const val ACTION_ANSWER_CODE = 1
    private const val ACTION_DECLINE_CODE = 2
    private const val CONTENT_CODE = 3
    private const val FULL_SCREEN_CODE = 4

    fun notificationId(callId: String, chatId: String): Int {
        val key = callId.ifBlank { chatId }
        return ("incoming_call_$key").hashCode()
    }

    fun show(context: Context, data: Map<String, String>) {
        val chatId = data["chatId"]?.trim().orEmpty()
        if (chatId.isBlank()) return

        val callerId = data["callerId"]?.trim().orEmpty()
        val callType = data["callType"]?.trim().orEmpty().ifBlank { "audio" }
        val isGroupCall = data["isGroup"]?.equals("true", ignoreCase = true) == true
        val callId = listOf(
            data["callId"],
            data["sessionId"],
            data["callSessionId"]
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        val callerName = data["callerName"]?.trim().orEmpty().ifBlank { "Incoming call" }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val notificationId = notificationId(callId, chatId)
        val resolvedCallType = if (callType.equals("video", ignoreCase = true)) {
            CallType.VIDEO
        } else {
            CallType.AUDIO
        }

        val contentIntent = ringingCallIntent(
            context = context,
            chatId = chatId,
            callerId = callerId,
            callType = resolvedCallType,
            isGroupCall = isGroupCall,
            callId = callId,
            autoAccept = false
        )
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCode(callId, chatId, CONTENT_CODE),
            contentIntent,
            pendingIntentFlags()
        )
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            requestCode(callId, chatId, FULL_SCREEN_CODE),
            contentIntent,
            pendingIntentFlags()
        )

        val answerIntent = ringingCallIntent(
            context = context,
            chatId = chatId,
            callerId = callerId,
            callType = resolvedCallType,
            isGroupCall = isGroupCall,
            callId = callId,
            autoAccept = true
        )
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            requestCode(callId, chatId, ACTION_ANSWER_CODE),
            answerIntent,
            pendingIntentFlags()
        )

        val declineIntent = Intent(context, IncomingCallActionReceiver::class.java).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_IS_GROUP, isGroupCall)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(callId, chatId, ACTION_DECLINE_CODE),
            declineIntent,
            pendingIntentFlags()
        )

        val title = when {
            isGroupCall && callType.equals("video", ignoreCase = true) -> "Incoming group video call"
            isGroupCall -> "Incoming group voice call"
            callType.equals("video", ignoreCase = true) -> "Incoming video call"
            else -> "Incoming voice call"
        }

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent,
                ),
            )
            .addPerson(caller)

        if (canUseFullScreenIntent(notificationManager)) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            Log.w(
                "IncomingCallNotif",
                "Full-screen call alerts are disabled by system settings; showing a heads-up call notification instead.",
            )
        }

        val notification = notificationBuilder.build()

        notificationManager.notify(notificationId, notification)
        CallRingingReceipt.mark(callId)
        runCatching {
            IncomingCallRingtoneService.start(context, callId, chatId)
        }.onFailure { error ->
            // The high-priority call notification remains visible and audible if the OS rejects FGS startup.
            Log.w("IncomingCallNotif", "Unable to start the incoming-call ringtone service", error)
        }
    }

    fun showFromSession(context: Context, session: CallSession) {
        if (session.chatId.isBlank()) return
        show(
            context = context,
            data = mapOf(
                "type" to "incoming_call",
                "chatId" to session.chatId,
                "callerId" to session.callerId,
                "callType" to session.callType,
                "isGroup" to session.isGroupCall().toString(),
                "callId" to session.id,
                "sessionId" to session.id,
                "callerName" to session.callerName.ifBlank { "Incoming call" },
            ),
        )
    }

    fun dismiss(context: Context, callId: String, chatId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId(callId, chatId))
        IncomingCallRingtoneService.stop(context)
    }

    private fun ringingCallIntent(
        context: Context,
        chatId: String,
        callerId: String,
        callType: CallType,
        isGroupCall: Boolean,
        callId: String,
        autoAccept: Boolean
    ): Intent {
        return CallActivity.newIntent(
            context = context,
            chatId = chatId,
            otherUserId = if (isGroupCall) null else callerId,
            callType = callType,
            isCaller = false,
            callId = callId.ifBlank { null },
            autoAccept = autoAccept
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming voice and video calls"
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(ringtoneUri, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun requestCode(callId: String, chatId: String, actionCode: Int): Int {
        val key = callId.ifBlank { chatId }
        return 31 * key.hashCode() + actionCode
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private fun canUseFullScreenIntent(notificationManager: NotificationManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
    }
}
