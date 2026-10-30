package com.biblia.povitrpustok.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Transient audio focus for one screen's speech (F2-11).
 *
 * The reader owns focus only while a verse is actually being spoken: it asks
 * before the first utterance and gives it back on pause, stop, finish, error,
 * or when another app takes it. It never keeps focus while idle, so TalkBack,
 * calls, and music are not held hostage by a paused reader.
 *
 * One instance per playback owner; it is not a singleton.
 */
interface AudioFocusController {

    /**
     * Requests transient focus for speech. [onFocusLost] runs on the main
     * thread when another app takes focus away (a call, TalkBack, another
     * media app); the caller should stop speaking rather than talk over it.
     * Returns false when focus was denied, in which case nothing was acquired.
     */
    fun requestFocus(onFocusLost: () -> Unit): Boolean

    /** Gives focus back. Idempotent and safe when nothing was requested. */
    fun abandonFocus()
}

/**
 * `AudioManager` adapter.
 *
 * Uses `AUDIOFOCUS_GAIN_TRANSIENT` because a verse is a short utterance, and
 * `CONTENT_TYPE_SPEECH` so the system treats it as speech rather than media.
 * `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` is ignored: speech must not duck itself
 * under a notification, and the caller pauses on a real loss instead.
 */
class AndroidAudioFocusController(context: Context) : AudioFocusController {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private var focusRequest: AudioFocusRequest? = null

    override fun requestFocus(onFocusLost: () -> Unit): Boolean {
        val manager = audioManager ?: return false
        // Replace any previous request so a second speak cannot leak a
        // listener that outlives its playback.
        abandonFocus()
        val changeListener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    abandonFocus()
                    onFocusLost()
                }
                // Speech does not duck: keep talking through a notification.
                else -> Unit
            }
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(changeListener, Handler(Looper.getMainLooper()))
            .build()
        return if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
            true
        } else {
            false
        }
    }

    override fun abandonFocus() {
        val manager = audioManager
        val request = focusRequest
        focusRequest = null
        if (manager != null && request != null) {
            manager.abandonAudioFocusRequest(request)
        }
    }
}
