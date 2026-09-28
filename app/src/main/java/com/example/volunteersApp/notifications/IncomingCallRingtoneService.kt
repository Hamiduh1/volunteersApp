package com.example.volunteersApp.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.volunteersApp.R
import com.example.volunteersApp.chat.CallActivity

/**
 * Plays the default ringtone while an incoming call notification is active (iOS CallKit ring parity).
 */
class IncomingCallRingtoneService : Service() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
                val chatId = intent?.getStringExtra(EXTRA_CHAT_ID).orEmpty()
                if (chatId.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildForegroundNotification(callId, chatId))
                startRinging()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun startRinging() {
        if (mediaPlayer?.isPlaying == true) return
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@IncomingCallRingtoneService, ringtoneUri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopRinging() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun buildForegroundNotification(callId: String, chatId: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming call ringtone",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the incoming call ringtone active"
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            CallActivity.newIntent(
                context = this,
                chatId = chatId,
                otherUserId = null,
                callType = com.example.volunteersApp.chat.CallType.AUDIO,
                isCaller = false,
                callId = callId.ifBlank { null },
                autoAccept = false,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.incoming_call_ringing_title))
            .setContentText(getString(R.string.incoming_call_ringing_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "incoming_call_ringtone"
        private const val NOTIFICATION_ID = 9101
        private const val ACTION_STOP = "com.example.volunteersApp.notifications.STOP_INCOMING_RING"
        private const val EXTRA_CALL_ID = "extra_call_id"
        private const val EXTRA_CHAT_ID = "extra_chat_id"

        fun start(context: Context, callId: String, chatId: String) {
            val intent = Intent(context, IncomingCallRingtoneService::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CHAT_ID, chatId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, IncomingCallRingtoneService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
