package com.example.volunteersApp.notifications

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Caller-side tones until someone answers or hangs up: "Calling" plays two short beeps then a pause;
 * "Ringing" plays the standard ringback ("ring… ring…") once the callee's device reports it is ringing.
 * Uses the voice-call stream so it stays at call volume in the earpiece (or speaker on video calls)
 * instead of the phone's loud ringtone.
 */
object OutgoingCallRingbackHelper {
    private const val TAG = "OutgoingCallRingback"
    private const val RINGBACK_VOLUME = 60
    private const val CALLING_BEEP_MS = 400
    private const val CALLING_CYCLE_MS = 2_500L

    private enum class Mode { NONE, CALLING, RINGBACK }

    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var mode = Mode.NONE

    private val callingBeeps = object : Runnable {
        override fun run() {
            synchronized(this@OutgoingCallRingbackHelper) {
                if (mode != Mode.CALLING) return
                runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, CALLING_BEEP_MS) }
                    .onFailure { Log.w(TAG, "Failed to play calling tone", it) }
            }
            handler.postDelayed(this, CALLING_CYCLE_MS)
        }
    }

    /** Two short beeps, then a pause, repeated until [start] or [stop]. */
    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun startCalling(context: Context) {
        if (mode != Mode.NONE) return
        val generator = createToneGenerator() ?: return
        toneGenerator = generator
        mode = Mode.CALLING
        handler.removeCallbacks(callingBeeps)
        handler.post(callingBeeps)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun start(context: Context) {
        if (mode == Mode.RINGBACK) return
        releaseTones()
        val generator = createToneGenerator() ?: return
        toneGenerator = generator
        mode = Mode.RINGBACK
        runCatching { generator.startTone(ToneGenerator.TONE_SUP_RINGTONE) }
            .onFailure { Log.w(TAG, "Failed to start outgoing ringback", it) }
    }

    @Synchronized
    fun stop() {
        releaseTones()
    }

    private fun createToneGenerator(): ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME)
    }.onFailure { e ->
        Log.w(TAG, "Failed to create call tone generator", e)
    }.getOrNull()

    private fun releaseTones() {
        mode = Mode.NONE
        handler.removeCallbacks(callingBeeps)
        toneGenerator?.runCatching {
            stopTone()
            release()
        }?.onFailure { e ->
            Log.w(TAG, "Failed to stop outgoing call tone", e)
        }
        toneGenerator = null
    }
}
