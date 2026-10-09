package com.example.volunteersApp.notifications

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Plays the standard in-call ringback ("ring… ring…") for the caller once the callee's device
 * reports it is ringing. Uses the voice-call stream so it stays at call volume in the earpiece
 * (or speaker on video calls) instead of the phone's loud ringtone.
 */
object OutgoingCallRingbackHelper {
    private const val TAG = "OutgoingCallRingback"
    private const val RINGBACK_VOLUME = 60
    private var toneGenerator: ToneGenerator? = null

    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context) {
        if (toneGenerator != null) return
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME).apply {
                startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to start outgoing ringback", e)
        }.getOrNull()
    }

    fun stop() {
        toneGenerator?.runCatching {
            stopTone()
            release()
        }?.onFailure { e ->
            Log.w(TAG, "Failed to stop outgoing ringback", e)
        }
        toneGenerator = null
    }
}
