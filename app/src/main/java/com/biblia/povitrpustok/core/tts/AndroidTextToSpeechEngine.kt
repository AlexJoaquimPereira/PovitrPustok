package com.biblia.povitrpustok.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Production adapter over `android.speech.tts.TextToSpeech`.
 *
 * Holds no static/global state. The factory keeps only the application
 * context, so it is safe to provide from Hilt; the engine itself is created
 * per [EnglishTextToSpeech] instance and released by its owner.
 */
class AndroidTextToSpeechEngineFactory(
    context: Context
) : TextToSpeechEngineFactory {

    private val appContext: Context = context.applicationContext

    override fun create(onInit: (Int) -> Unit): TextToSpeechEngine {
        val engine = TextToSpeech(appContext) { status -> onInit(status) }
        return AndroidTextToSpeechEngine(engine)
    }
}

/** Thin delegation; all policy lives in [EnglishTextToSpeech]. */
internal class AndroidTextToSpeechEngine(
    private val engine: TextToSpeech
) : TextToSpeechEngine {

    override fun isLanguageAvailable(locale: Locale): Int =
        engine.isLanguageAvailable(locale)

    override fun setLanguage(locale: Locale): Int = engine.setLanguage(locale)

    override fun speak(text: String, utteranceId: String): Int =
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

    override fun stop(): Int = engine.stop()

    override fun shutdown() {
        engine.stop()
        engine.shutdown()
    }

    /**
     * Maps the platform listener onto [TtsUtteranceEvent]. `onRangeStart` is
     * only delivered by engines that support it; a missing range is normal,
     * not an error.
     *
     * Platform callbacks arrive on a TTS binder thread, so the sink is invoked
     * there; [EnglishTextToSpeech] only forwards to a thread-safe sink.
     */
    override fun setUtteranceListener(listener: (TtsUtteranceEvent) -> Unit) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { listener(TtsUtteranceEvent.Started(it)) }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                utteranceId?.let { listener(TtsUtteranceEvent.RangeStarted(it, start, end)) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { listener(TtsUtteranceEvent.Finished(it)) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { listener(TtsUtteranceEvent.Failed(it)) }
            }

            // Older engines call the deprecated single-argument variant; keep
            // it wired to the same event instead of dropping the failure.
            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { listener(TtsUtteranceEvent.Failed(it)) }
            }
        })
    }
}
