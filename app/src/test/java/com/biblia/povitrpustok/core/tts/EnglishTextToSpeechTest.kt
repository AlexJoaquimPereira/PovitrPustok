package com.biblia.povitrpustok.core.tts

import android.speech.tts.TextToSpeech
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2-10: the wrapper is testable without a device or Robolectric because the
 * platform engine sits behind [TextToSpeechEngine]. These tests cover init
 * success/failure, both language-availability branches (`-1` missing data and
 * `-2` unsupported), English selection, speak/stop, and shutdown.
 */
class EnglishTextToSpeechTest {

    private val engine = FakeEngine()
    private val factory = FakeFactory(engine)
    private val tts = EnglishTextToSpeech(factory)

    @Test
    fun idle_before_initialize() {
        assertEquals(TtsStatus.Idle, tts.status.value)
        assertFalse(tts.isReady)
    }

    @Test
    fun initialization_is_pending_until_the_engine_calls_back() {
        tts.initialize()

        assertEquals(TtsStatus.Initializing, tts.status.value)
        assertTrue(engine.languageQueries.isEmpty())

        factory.completeInit()

        assertEquals(TtsStatus.Ready, tts.status.value)
    }

    @Test
    fun successful_init_checks_and_selects_english_only() {
        tts.initialize()
        factory.completeInit()

        assertEquals(listOf(Locale.ENGLISH), engine.languageQueries)
        assertEquals(listOf(Locale.ENGLISH), engine.selectedLanguages)
        assertEquals(TtsStatus.Ready, tts.status.value)
    }

    @Test
    fun init_failure_exposes_the_platform_error_code() {
        tts.initialize()
        factory.completeInit(TtsResult.ERROR)

        assertEquals(TtsStatus.InitializationFailed(TtsResult.ERROR), tts.status.value)
        assertTrue(engine.languageQueries.isEmpty())
    }

    @Test
    fun a_factory_that_throws_reports_initialization_failure() {
        val failing = EnglishTextToSpeech(
            TextToSpeechEngineFactory { error("engine unavailable") }
        )

        failing.initialize()

        assertEquals(TtsStatus.InitializationFailed(TtsResult.ERROR), failing.status.value)
    }

    @Test
    fun missing_language_data_is_reported_as_minus_one() {
        engine.availability = TtsLanguageAvailability.MISSING_DATA

        tts.initialize()
        factory.completeInit()

        assertEquals(TtsStatus.MissingLanguageData, tts.status.value)
        assertTrue(engine.selectedLanguages.isEmpty())
    }

    @Test
    fun unsupported_language_is_reported_as_minus_two() {
        engine.availability = TtsLanguageAvailability.NOT_SUPPORTED

        tts.initialize()
        factory.completeInit()

        assertEquals(TtsStatus.LanguageNotSupported, tts.status.value)
        assertTrue(engine.selectedLanguages.isEmpty())
    }

    @Test
    fun set_language_missing_data_branch_is_reported() {
        engine.setLanguageResult = TtsLanguageAvailability.MISSING_DATA

        tts.initialize()
        factory.completeInit()

        assertEquals(TtsStatus.MissingLanguageData, tts.status.value)
    }

    @Test
    fun set_language_unsupported_branch_is_reported() {
        engine.setLanguageResult = TtsLanguageAvailability.NOT_SUPPORTED

        tts.initialize()
        factory.completeInit()

        assertEquals(TtsStatus.LanguageNotSupported, tts.status.value)
    }

    @Test
    fun synchronous_init_callback_is_supported() {
        val synchronous = EnglishTextToSpeech(
            FakeFactory(engine, callbackDuringCreate = true)
        )

        synchronous.initialize()

        assertEquals(TtsStatus.Ready, synchronous.status.value)
    }

    @Test
    fun initialize_twice_creates_only_one_engine() {
        tts.initialize()
        tts.initialize()
        factory.completeInit()
        tts.initialize()

        assertEquals(1, factory.createCalls)
    }

    @Test
    fun speak_forwards_text_and_utterance_id_when_ready() {
        ready()

        val accepted = tts.speak("In the beginning", "john-1-1")

        assertTrue(accepted)
        assertEquals(listOf(Spoken("In the beginning", "john-1-1")), engine.spoken)
    }

    @Test
    fun speak_uses_the_default_utterance_id() {
        ready()

        assertTrue(tts.speak("In the beginning"))

        assertEquals(EnglishTextToSpeech.DEFAULT_UTTERANCE_ID, engine.spoken.single().utteranceId)
    }

    @Test
    fun speak_is_rejected_until_ready() {
        tts.initialize()

        assertFalse(tts.speak("In the beginning"))

        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun blank_text_is_not_sent_to_the_engine() {
        ready()

        assertFalse(tts.speak("   "))

        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun engine_speak_error_returns_false() {
        engine.speakResult = TtsResult.ERROR
        ready()

        assertFalse(tts.speak("In the beginning"))
        assertEquals(1, engine.spoken.size)
    }

    @Test
    fun stop_delegates_to_the_engine() {
        ready()

        tts.stop()

        assertEquals(1, engine.stopCalls)
    }

    @Test
    fun stop_is_safe_before_initialize() {
        tts.stop()

        assertEquals(0, engine.stopCalls)
    }

    @Test
    fun shutdown_releases_the_engine_once_and_is_idempotent() {
        ready()

        tts.shutdown()
        tts.shutdown()

        assertEquals(1, engine.shutdownCalls)
        assertEquals(TtsStatus.ShutDown, tts.status.value)
    }

    @Test
    fun shutdown_before_initialize_does_not_touch_the_engine() {
        tts.shutdown()

        assertEquals(0, engine.shutdownCalls)
        assertEquals(TtsStatus.ShutDown, tts.status.value)
    }

    @Test
    fun close_is_shutdown() {
        ready()

        tts.close()

        assertEquals(1, engine.shutdownCalls)
        assertEquals(TtsStatus.ShutDown, tts.status.value)
    }

    @Test
    fun speak_after_shutdown_is_rejected() {
        ready()
        tts.shutdown()

        assertFalse(tts.speak("In the beginning"))
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun initialize_after_shutdown_is_ignored() {
        ready()
        tts.shutdown()

        tts.initialize()

        assertEquals(1, factory.createCalls)
        assertEquals(TtsStatus.ShutDown, tts.status.value)
    }

    @Test
    fun language_codes_mirror_the_platform_constants() {
        assertEquals(TextToSpeech.LANG_AVAILABLE, TtsLanguageAvailability.AVAILABLE)
        assertEquals(TextToSpeech.LANG_MISSING_DATA, TtsLanguageAvailability.MISSING_DATA)
        assertEquals(TextToSpeech.LANG_NOT_SUPPORTED, TtsLanguageAvailability.NOT_SUPPORTED)
        assertEquals(TextToSpeech.SUCCESS, TtsResult.SUCCESS)
        assertEquals(TextToSpeech.ERROR, TtsResult.ERROR)
    }

    @Test
    fun pending_init_result_is_discarded_by_shutdown() {
        tts.initialize()
        tts.shutdown()

        factory.completeInit()

        assertEquals(TtsStatus.ShutDown, tts.status.value)
        assertTrue(engine.languageQueries.isEmpty())
        assertNull(engine.lastSpokenOrNull())
    }

    private fun ready() {
        tts.initialize()
        factory.completeInit()
        assertEquals(TtsStatus.Ready, tts.status.value)
    }
}

private data class Spoken(val text: String, val utteranceId: String)

private class FakeEngine : TextToSpeechEngine {

    var availability: Int = TtsLanguageAvailability.AVAILABLE
    var setLanguageResult: Int = TtsLanguageAvailability.AVAILABLE
    var speakResult: Int = TtsResult.SUCCESS

    val languageQueries = mutableListOf<Locale>()
    val selectedLanguages = mutableListOf<Locale>()
    val spoken = mutableListOf<Spoken>()
    var stopCalls = 0
    var shutdownCalls = 0

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

    fun lastSpokenOrNull(): Spoken? = spoken.lastOrNull()
}

private class FakeFactory(
    private val engine: FakeEngine,
    private val callbackDuringCreate: Boolean = false
) : TextToSpeechEngineFactory {

    var createCalls = 0
    private var onInit: ((Int) -> Unit)? = null

    override fun create(onInit: (Int) -> Unit): TextToSpeechEngine {
        createCalls++
        this.onInit = onInit
        if (callbackDuringCreate) onInit(TtsResult.SUCCESS)
        return engine
    }

    fun completeInit(result: Int = TtsResult.SUCCESS) {
        onInit?.invoke(result)
    }
}
