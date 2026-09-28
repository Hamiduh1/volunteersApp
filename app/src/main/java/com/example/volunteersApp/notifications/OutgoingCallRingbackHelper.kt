package com.example.volunteersApp.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log

/**
 * Plays a looping ringback tone for the caller while waiting for the callee to answer.
 */
object OutgoingCallRingbackHelper {
    private const val TAG = "OutgoingCallRingback"
    private var mediaPlayer: MediaPlayer? = null

    fun start(context: Context) {
        if (mediaPlayer?.isPlaying == true) return
        stop()
        val appContext = context.applicationContext
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(appContext, ringtoneUri)
            isLooping = true
            setVolume(0.55f, 0.55f)
            prepare()
            start()
        }
    }

    fun stop() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }?.onFailure { e ->
            Log.w(TAG, "Failed to stop outgoing ringback", e)
        }
        mediaPlayer = null
    }
}
