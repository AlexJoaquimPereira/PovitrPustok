package com.biblia.povitrpustok.core.tts

import java.util.Locale

/**
 * Minimal port over the platform text-to-speech engine (F2-10).
 *
 * [EnglishTextToSpeech] depends on this interface, never directly on
 * `android.speech.tts.TextToSpeech`, so JVM tests can drive initialization,
 * language availability, speaking, stopping, and shutdown with a fake.
 * [AndroidTextToSpeechEngineFactory] is the only production implementation.
 *
 * The port is deliberately tiny: no audio focus, no utterance progress, no
 * SSML, no voice enumeration, and no network. Those concerns belong to the
 * caller (F2-11) or are out of scope.
 */
interface TextToSpeechEngine {

    /** Mirrors Android's `isLanguageAvailable`; returns a [TtsLanguageAvailability] code. */
    fun isLanguageAvailable(locale: Locale): Int

    /** Selects [locale] for synthesis; returns a [TtsLanguageAvailability] code. */
    fun setLanguage(locale: Locale): Int

    /** Enqueues [text]; returns [TtsResult.SUCCESS] or [TtsResult.ERROR]. */
    fun speak(text: String, utteranceId: String): Int

    /** Stops the current utterance and clears the queue. */
    fun stop(): Int

    /** Releases the underlying engine. Safe to call once per created engine. */
    fun shutdown()
}

/**
 * Creates exactly one engine and forwards its asynchronous initialization
 * result to [onInit]. The callback may arrive after [create] returns.
 */
fun interface TextToSpeechEngineFactory {
    fun create(onInit: (Int) -> Unit): TextToSpeechEngine
}

/**
 * Availability codes returned by [TextToSpeechEngine.isLanguageAvailable] and
 * [TextToSpeechEngine.setLanguage]. Values mirror the platform constants
 * `TextToSpeech.LANG_AVAILABLE`, `LANG_MISSING_DATA`, and
 * `LANG_NOT_SUPPORTED`; `EnglishTextToSpeechTest` guards the mirroring.
 */
object TtsLanguageAvailability {
    const val AVAILABLE = 0
    const val MISSING_DATA = -1
    const val NOT_SUPPORTED = -2
}

/** Engine operation result codes, mirroring `TextToSpeech.SUCCESS`/`ERROR`. */
object TtsResult {
    const val SUCCESS = 0
    const val ERROR = -1
}
