package com.example.volunteersApp.chat

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Lightweight in-app tones for chat (parity with iOS ChatSoundEffects).
 */
object ChatSoundEffects {
    private const val TAG = "ChatSoundEffects"

    fun playIncomingMessageBeep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 78)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching { tone.release() }
            }, 220)
        }.onFailure {
            Log.w(TAG, "Incoming message beep failed.", it)
        }
    }
}
