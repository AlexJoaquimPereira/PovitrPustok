package com.biblia.povitrpustok.testutil

import com.biblia.povitrpustok.core.tts.AudioFocusController
import com.biblia.povitrpustok.core.tts.TextToSpeechEngine
import com.biblia.povitrpustok.core.tts.TextToSpeechEngineFactory
import com.biblia.povitrpustok.core.tts.TtsLanguageAvailability
import com.biblia.povitrpustok.core.tts.TtsResult
import com.biblia.povitrpustok.core.tts.TtsUtteranceEvent
import java.util.Locale

/**
 * Recording text-to-speech engine for reader playback tests (F2-11).
 *
 * Reports language availability, records what was spoken, and exposes
 * [emit] so a test can drive `onStart`/`onRangeStart`/`onDone` without a
 * device. It implements the optional utterance listener, so the range path is
 * exercised; [ignoresUtteranceListener] simulates an engine without
 * `onRangeStart`.
 */
class RecordingTtsEngine(
    var ignoresUtteranceListener: Boolean = false
) : TextToSpeechEngine {

    data class Spoken(val text: String, val utteranceId: String)

    var availability: Int = TtsLanguageAvailability.AVAILABLE
    var setLanguageResult: Int = TtsLanguageAvailability.AVAILABLE
    var speakResult: Int = TtsResult.SUCCESS

    val languageQueries = mutableListOf<Locale>()
    val selectedLanguages = mutableListOf<Locale>()
    val spoken = mutableListOf<Spoken>()
    var stopCalls = 0
    var shutdownCalls = 0

    private var listener: ((TtsUtteranceEvent) -> Unit)? = null

    override fun isLanguageAvailable(locale: Locale): Int {
        languageQueries += locale
        return availability
    }

    override fun setLanguage(locale: Locale): Int {
        selectedLanguages += locale
        return setLanguageResult
    }

    override fun speak(text: String, utteranceId: String): Int {
        spoken += Spoken(text, utteranceId)
        return speakResult
    }

    override fun stop(): Int {
        stopCalls++
        return TtsResult.SUCCESS
    }

    override fun shutdown() {
        shutdownCalls++
    }

    override fun setUtteranceListener(listener: (TtsUtteranceEvent) -> Unit) {
        if (ignoresUtteranceListener) return
        this.listener = listener
    }

    /** Delivers an event as the platform engine would. */
    fun emit(event: TtsUtteranceEvent) {
        listener?.invoke(event)
    }
}

/** Factory that hands out one [RecordingTtsEngine] and controls init timing. */
class RecordingTtsFactory(
    private val engine: TextToSpeechEngine = RecordingTtsEngine(),
    private val callbackDuringCreate: Boolean = false
) : TextToSpeechEngineFactory {

    var createCalls = 0
        private set

    private var onInit: ((Int) -> Unit)? = null

    override fun create(onInit: (Int) -> Unit): TextToSpeechEngine {
        createCalls++
        this.onInit = onInit
        if (callbackDuringCreate) onInit(TtsResult.SUCCESS)
        return engine
    }

    /** Completes the pending initialization, as the platform callback would. */
    fun completeInit(result: Int = TtsResult.SUCCESS) {
        onInit?.invoke(result)
    }
}

/**
 * Audio focus double. [granted] decides whether focus is handed out;
 * [loseFocus] simulates another app (a call, TalkBack) taking it.
 */
class FakeAudioFocusController(var granted: Boolean = true) : AudioFocusController {

    var requestCalls = 0
        private set
    var abandonCalls = 0
        private set

    private var onLost: (() -> Unit)? = null

    override fun requestFocus(onFocusLost: () -> Unit): Boolean {
        requestCalls++
        if (!granted) return false
        onLost = onFocusLost
        return true
    }

    override fun abandonFocus() {
        abandonCalls++
        onLost = null
    }

    fun loseFocus() {
        onLost?.invoke()
    }
}
