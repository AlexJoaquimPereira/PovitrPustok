package com.biblia.povitrpustok.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * F2-11: the platform audio-focus adapter, on Robolectric's `AudioManager`
 * shadow. Covers the request shape, denial, release, and the loss callback
 * that keeps the reader from talking over another app.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAudioFocusControllerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val shadow = shadowOf(audioManager)
    private val controller = AndroidAudioFocusController(context)

    @Test
    fun requests_transient_focus_for_speech() {
        assertTrue(controller.requestFocus {})

        val request = shadow.lastAudioFocusRequest
        assertNotNull("focus must actually be requested", request)
        val attributes = request.audioFocusRequest.audioAttributes
        assertEquals(AudioAttributes.USAGE_MEDIA, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attributes.contentType)
    }

    @Test
    fun denied_focus_reports_false_and_holds_nothing() {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertFalse(controller.requestFocus {})

        // Abandoning after a denial must not touch a request we never got.
        controller.abandonFocus()
        assertNull(shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun abandoning_gives_focus_back() {
        assertTrue(controller.requestFocus {})
        val granted = shadow.lastAudioFocusRequest.audioFocusRequest

        controller.abandonFocus()

        assertEquals(granted, shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun a_second_request_replaces_the_first() {
        assertTrue(controller.requestFocus {})
        assertTrue(controller.requestFocus {})
        val second = shadow.lastAudioFocusRequest.audioFocusRequest

        controller.abandonFocus()

        assertEquals("only the latest request is held", second, shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun abandoning_twice_is_safe() {
        assertTrue(controller.requestFocus {})
        val granted = shadow.lastAudioFocusRequest.audioFocusRequest

        controller.abandonFocus()
        controller.abandonFocus()

        assertEquals(granted, shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun losing_focus_runs_the_callback_and_releases() {
        var lost = 0
        assertTrue(controller.requestFocus { lost++ })

        shadow.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the reader must learn that focus was taken", 1, lost)
        assertNotNull(shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun a_duck_request_does_not_stop_speech() {
        var lost = 0
        assertTrue(controller.requestFocus { lost++ })

        shadow.lastAudioFocusRequest.listener
            .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("speech does not duck itself", 0, lost)
    }
}
