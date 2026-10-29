package com.biblia.povitrpustok.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
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
}
