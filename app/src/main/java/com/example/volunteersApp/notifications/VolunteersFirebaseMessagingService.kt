package com.example.volunteersApp.notifications



import android.util.Log

import com.google.firebase.messaging.FirebaseMessagingService

import com.google.firebase.messaging.RemoteMessage

import com.example.volunteersApp.ui.main.SocialInboxNav

import com.example.volunteersApp.ui.main.MainActivity

import android.app.NotificationChannel

import android.app.NotificationManager

import android.app.PendingIntent

import android.content.Context

import android.content.Intent

import android.os.Build

import androidx.core.app.NotificationCompat

import com.example.volunteersApp.R
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.streams.LiveLaunchIntent
import com.example.volunteersApp.streams.LiveLaunchTarget
import com.example.volunteersApp.jokes.MindLoomNav
import com.example.volunteersApp.wallet.WalletNav


class VolunteersFirebaseMessagingService : FirebaseMessagingService() {



    override fun onNewToken(token: String) {

        super.onNewToken(token)

        PushTokenRegistrar.registerAsync(token)

    }



    override fun onMessageReceived(message: RemoteMessage) {

        super.onMessageReceived(message)

        val data = message.data

        when (data["type"]) {

            "incoming_call" -> IncomingCallNotifications.show(this, data)

            "call_cancelled" -> dismissIncomingCallNotification(data)

            "chat_message" -> showChatNotification(data, message)

            "liveStream" -> showLiveStreamNotification(data, message)

            "jokesPost" -> showMindLoomPostNotification(data, message)

            "afriex_transaction", "wallet_transfer" -> showWalletTransferNotification(data, message)

        }

    }



    private fun dismissIncomingCallNotification(data: Map<String, String>) {

        val chatId = data["chatId"]?.trim().orEmpty()

        if (chatId.isBlank()) return

        val callId = listOf(

            data["callId"],

            data["sessionId"],

            data["callSessionId"]

        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        IncomingCallNotifications.dismiss(this, callId, chatId)

    }



    private fun showChatNotification(data: Map<String, String>, message: RemoteMessage) {

        val chatId = data["chatId"] ?: return

        val senderId = data["senderId"] ?: data["otherUserId"] ?: return

        val senderName = data["senderName"] ?: "New message"

        val preview = data["preview"]

            ?: message.notification?.body

            ?: "Open conversation"



        val intent = Intent(this, MainActivity::class.java).apply {

            putExtra(SocialInboxNav.EXTRA_PUSH_CHAT_ID, chatId)

            putExtra(SocialInboxNav.EXTRA_PUSH_OTHER_USER_ID, senderId)

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        }



        val pendingIntent = PendingIntent.getActivity(

            this,

            chatId.hashCode(),

            intent,

            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        )



        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "chat_messages"

        ensureChannel(

            notificationManager = notificationManager,

            channelId = channelId,

            channelName = "Chat Messages",

            importance = NotificationManager.IMPORTANCE_HIGH

        )



        val notification = NotificationCompat.Builder(this, channelId)

            .setSmallIcon(R.drawable.ic_launcher_foreground)

            .setContentTitle(senderName)

            .setContentText(preview)

            .setPriority(NotificationCompat.PRIORITY_HIGH)

            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

            .setAutoCancel(true)

            .setContentIntent(pendingIntent)

            .build()



        notificationManager.notify(chatId.hashCode(), notification)

    }

    private fun showLiveStreamNotification(data: Map<String, String>, message: RemoteMessage) {

        val sessionId = sequenceOf(
            data["sessionId"],
            data["referenceId"]
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        if (sessionId.isBlank()) return

        val target = LiveLaunchTarget(
            sessionId = sessionId,
            hostId = sequenceOf(data["hostId"], data["actorId"]).firstOrNull { !it.isNullOrBlank() }?.trim(),
            shareAccessToken = data["token"]?.trim()?.ifBlank { null }
        )

        val intent = LiveLaunchIntent.loginIntent(this, target)
        val pendingIntent = PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "live_streams"
        ensureChannel(
            notificationManager = notificationManager,
            channelId = channelId,
            channelName = "Live Streams",
            importance = NotificationManager.IMPORTANCE_HIGH
        )

        val title = data["title"] ?: message.notification?.title ?: "Live now"
        val body = message.notification?.body
            ?: data["body"]
            ?: data["hostName"]?.takeIf { it.isNotBlank() }?.let { "$it started a live stream" }
            ?: "Open Live Studio"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(("live_" + sessionId).hashCode(), notification)
    }

    private fun showMindLoomPostNotification(data: Map<String, String>, message: RemoteMessage) {
        val intent = MindLoomNav.feedIntent(this).apply {
            data["authorId"]?.trim()?.takeIf { it.isNotBlank() }?.let {
                putExtra(MindLoomNav.EXTRA_POST_AUTHOR_ID, it)
            }
            data["postId"]?.trim()?.takeIf { it.isNotBlank() }?.let {
                putExtra(MindLoomNav.EXTRA_POST_ID, it)
            }
            putExtra("type", "jokesPost")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ("jokes_post_" + (data["postId"] ?: data["referenceId"] ?: "feed")).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mindloom_posts"
        ensureChannel(
            notificationManager = notificationManager,
            channelId = channelId,
            channelName = "MindLoom Posts",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )

        val title = data["title"] ?: message.notification?.title ?: "New MindLoom post"
        val body = message.notification?.body
            ?: data["body"]
            ?: data["authorName"]?.takeIf { it.isNotBlank() }?.let { "$it shared a post" }
            ?: "Open MindLoom"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(("mindloom_" + (data["postId"] ?: "feed")).hashCode(), notification)
    }




    private fun showWalletTransferNotification(data: Map<String, String>, message: RemoteMessage) {
        val payoutRequestId = data["payoutRequestId"]?.trim()?.takeIf { it.isNotBlank() }
            ?: data["payout_request_id"]?.trim()?.takeIf { it.isNotBlank() }
        val transactionId = data["transactionId"]?.trim()?.takeIf { it.isNotBlank() }
            ?: data["providerTransactionId"]?.trim()?.takeIf { it.isNotBlank() }
        val intent = WalletNav.historyIntent(
            this,
            payoutRequestId = payoutRequestId,
            transactionId = transactionId
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            ("wallet_xfer_" + (payoutRequestId ?: transactionId ?: "history")).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "wallet_transfers"
        ensureChannel(
            notificationManager = notificationManager,
            channelId = channelId,
            channelName = "Transfers",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )

        val title = data["title"] ?: message.notification?.title ?: "Transfer update"
        val body = message.notification?.body
            ?: data["body"]
            ?: data["status"]?.takeIf { it.isNotBlank() }?.let { "Status: $it" }
            ?: "Open transfer history for details"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(("wallet_" + (payoutRequestId ?: transactionId ?: "history")).hashCode(), notification)
    }

    private fun ensureChannel(

        notificationManager: NotificationManager,

        channelId: String,

        channelName: String,

        importance: Int

    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(channelId, channelName, importance)

            notificationManager.createNotificationChannel(channel)

        }

    }

}

