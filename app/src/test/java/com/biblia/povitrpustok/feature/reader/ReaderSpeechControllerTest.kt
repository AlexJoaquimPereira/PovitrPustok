package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.core.tts.TtsLanguageAvailability
import com.biblia.povitrpustok.core.tts.TtsResult
import com.biblia.povitrpustok.core.tts.TtsUtteranceEvent
import com.biblia.povitrpustok.testutil.FakeAudioFocusController
import com.biblia.povitrpustok.testutil.RecordingTtsEngine
import com.biblia.povitrpustok.testutil.RecordingTtsFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2-11/F2-12: the reader playback state machine, driven by a recording
 * engine and a fake audio-focus handle — no device, no real speech.
 *
 * The Konkani cases are the point of F2-12: a non-English edition must never
 * reach the English voice, must not even create an engine, and must not take
 * audio focus.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSpeechControllerTest {

    private val verse = "In the beginning was the Word"
    private val konkaniVerse = "Apolleam bhaxen Devan ulo kelo"
    private val konkaniTag = "kok-Latn"

    /** One controller with its engine, factory, and focus double. */
    private class Playback(
        val engine: RecordingTtsEngine,
        val factory: RecordingTtsFactory,
        val focus: FakeAudioFocusController,
        val controller: ReaderSpeechController
    ) {
        /** Requests a verse and completes the pending engine init. */
        fun start(verseId: Long, text: String, languageTag: String = "en"): Playback {
            controller.speak(verseId, text, languageTag)
            factory.completeInit()
            return this
        }

        fun utteranceId(verseId: Long): String =
            ReaderSpeechController.UTTERANCE_PREFIX + verseId
    }

    private fun TestScope.playback(
        engine: RecordingTtsEngine = RecordingTtsEngine(),
        granted: Boolean = true
    ): Playback {
        val factory = RecordingTtsFactory(engine)
        val focus = FakeAudioFocusController(granted)
        val controller = ReaderSpeechController(factory, focus)
        controller.attach(backgroundScope)
        return Playback(engine, factory, focus, controller)
    }

    @Test
    fun konkani_text_is_never_sent_to_the_english_voice() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()

        playback.controller.speak(7L, konkaniVerse, konkaniTag)

        val state = playback.controller.state.value
        assertEquals(ReaderSpeechPhase.Unavailable, state.phase)
        assertEquals(ReaderSpeechIssue.NoKonkaniVoice, state.issue)
        assertEquals(7L, state.verseId)
        assertEquals("the engine must not even be created", 0, playback.factory.createCalls)
        assertTrue("no Konkani text may be spoken", playback.engine.spoken.isEmpty())
        assertTrue(playback.engine.languageQueries.isEmpty())
        assertEquals(
            "no audio focus for a request that cannot be spoken",
            0,
            playback.focus.requestCalls
        )
    }

    @Test
    fun konkani_request_silences_a_previous_english_verse() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = playback()
            playback.start(1L, verse)
            assertEquals(ReaderSpeechPhase.Speaking, playback.controller.state.value.phase)

            playback.controller.speak(2L, konkaniVerse, konkaniTag)

            assertEquals(ReaderSpeechIssue.NoKonkaniVoice, playback.controller.state.value.issue)
            assertEquals("the English verse must be stopped", 1, playback.engine.stopCalls)
            assertEquals(
                "focus is released when nothing is spoken",
                1,
                playback.focus.abandonCalls
            )
        }

    @Test
    fun english_verse_speaks_and_takes_transient_focus() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()

        playback.controller.speak(1L, verse, "en")
        assertEquals(ReaderSpeechPhase.Preparing, playback.controller.state.value.phase)

        playback.factory.completeInit()

        assertEquals(ReaderSpeechPhase.Speaking, playback.controller.state.value.phase)
        assertEquals(1, playback.factory.createCalls)
        assertEquals(1, playback.focus.requestCalls)
        assertEquals(listOf(verse), playback.engine.spoken.map { it.text })
        assertEquals(
            playback.utteranceId(1L),
            playback.engine.spoken.single().utteranceId
        )
    }

    @Test
    fun english_language_tags_are_matched_conservatively() {
        assertTrue(ReaderSpeechController.isEnglish("en"))
        assertTrue(ReaderSpeechController.isEnglish("en-US"))
        assertTrue(ReaderSpeechController.isEnglish("EN-gb"))
        assertFalse(ReaderSpeechController.isEnglish("kok-Latn"))
        assertFalse(ReaderSpeechController.isEnglish(""))
        assertFalse("a longer tag is not English", ReaderSpeechController.isEnglish("enochian"))
    }

    @Test
    fun pause_stops_the_engine_and_gives_focus_back() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()
        playback.start(1L, verse)

        playback.controller.pause()

        assertEquals(ReaderSpeechPhase.Paused, playback.controller.state.value.phase)
        assertEquals(1, playback.engine.stopCalls)
        assertEquals("paused speech must not hold focus", 1, playback.focus.abandonCalls)
    }

    @Test
    fun resume_speaks_the_remainder_from_the_last_reported_range() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = playback()
            playback.start(1L, verse)
            playback.engine.emit(
                TtsUtteranceEvent.RangeStarted(playback.utteranceId(1L), 7, 11)
            )

            playback.controller.pause()
            playback.controller.resume()

            assertEquals(ReaderSpeechPhase.Speaking, playback.controller.state.value.phase)
            assertEquals(2, playback.engine.spoken.size)
            assertEquals(verse.substring(7), playback.engine.spoken.last().text)
            assertEquals(2, playback.focus.requestCalls)
        }

    @Test
    fun range_events_expose_the_spoken_word_and_progress() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = playback()
            playback.start(1L, verse)

            playback.engine.emit(TtsUtteranceEvent.RangeStarted(playback.utteranceId(1L), 3, 6))

            val state = playback.controller.state.value
            assertEquals(ReaderSpeechPhase.Speaking, state.phase)
            assertEquals("the", state.spokenSegment)
            assertEquals(3f / verse.length, state.progress!!, 0.001f)
        }

    @Test
    fun finishing_the_verse_closes_the_bar_and_gives_focus_back() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = playback()
            playback.start(1L, verse)

            playback.engine.emit(TtsUtteranceEvent.Finished(playback.utteranceId(1L)))

            assertEquals(ReaderSpeechPhase.Idle, playback.controller.state.value.phase)
            assertNull(playback.controller.state.value.verseId)
            assertEquals(1, playback.focus.abandonCalls)
        }

    @Test
    fun losing_focus_pauses_instead_of_talking_over_the_other_app() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = playback()
            playback.start(1L, verse)

            playback.focus.loseFocus()

            assertEquals(ReaderSpeechPhase.Paused, playback.controller.state.value.phase)
            assertEquals("speech must stop when focus is taken", 1, playback.engine.stopCalls)
        }

    @Test
    fun stop_returns_to_idle_and_releases_focus() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()
        playback.start(1L, verse)

        playback.controller.stop()

        assertEquals(ReaderSpeechPhase.Idle, playback.controller.state.value.phase)
        assertNull(playback.controller.state.value.verseId)
        assertEquals(1, playback.focus.abandonCalls)
    }

    @Test
    fun missing_english_voice_data_is_reported_without_speaking() =
        runTest(UnconfinedTestDispatcher()) {
            val engine = RecordingTtsEngine().apply {
                availability = TtsLanguageAvailability.MISSING_DATA
            }
            val playback = playback(engine)

            playback.start(1L, verse)

            assertEquals(ReaderSpeechPhase.Unavailable, playback.controller.state.value.phase)
            assertEquals(
                ReaderSpeechIssue.EnglishVoiceDataMissing,
                playback.controller.state.value.issue
            )
            assertTrue(playback.engine.spoken.isEmpty())
            assertEquals(0, playback.focus.requestCalls)
        }

    @Test
    fun unsupported_english_voice_is_reported() = runTest(UnconfinedTestDispatcher()) {
        val engine = RecordingTtsEngine().apply {
            availability = TtsLanguageAvailability.NOT_SUPPORTED
        }
        val playback = playback(engine)

        playback.start(1L, verse)

        assertEquals(
            ReaderSpeechIssue.EnglishVoiceNotSupported,
            playback.controller.state.value.issue
        )
        assertTrue(playback.engine.spoken.isEmpty())
    }

    @Test
    fun an_engine_that_cannot_initialize_is_reported() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()

        playback.controller.speak(1L, verse, "en")
        playback.factory.completeInit(TtsResult.ERROR)

        assertEquals(ReaderSpeechPhase.Unavailable, playback.controller.state.value.phase)
        assertEquals(ReaderSpeechIssue.EngineFailed, playback.controller.state.value.issue)
        assertTrue(playback.engine.spoken.isEmpty())
    }

    @Test
    fun denied_audio_focus_keeps_the_reader_quiet() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback(granted = false)

        playback.start(1L, verse)

        assertEquals(ReaderSpeechPhase.Unavailable, playback.controller.state.value.phase)
        assertEquals(ReaderSpeechIssue.AudioBusy, playback.controller.state.value.issue)
        assertTrue("nothing may be spoken without focus", playback.engine.spoken.isEmpty())
        assertEquals(0, playback.focus.abandonCalls)
    }

    @Test
    fun an_engine_without_range_reports_still_speaks_and_restarts_on_resume() =
        runTest(UnconfinedTestDispatcher()) {
            val engine = RecordingTtsEngine(ignoresUtteranceListener = true)
            val playback = playback(engine)

            playback.start(1L, verse)

            assertEquals(ReaderSpeechPhase.Speaking, playback.controller.state.value.phase)
            assertNull("no range, no word highlight", playback.controller.state.value.spokenSegment)
            assertNull(playback.controller.state.value.progress)

            playback.controller.pause()
            playback.controller.resume()

            // Without a reported range the verse restarts rather than resuming
            // mid-word; that is the documented fallback.
            assertEquals(listOf(verse, verse), playback.engine.spoken.map { it.text })
        }

    @Test
    fun dismissing_a_notice_returns_to_idle() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()
        playback.controller.speak(1L, konkaniVerse, konkaniTag)

        playback.controller.dismissIssue()

        assertEquals(ReaderSpeechPhase.Idle, playback.controller.state.value.phase)
        assertNull(playback.controller.state.value.issue)
    }

    @Test
    fun shutdown_releases_the_engine_and_focus() = runTest(UnconfinedTestDispatcher()) {
        val playback = playback()
        playback.start(1L, verse)

        playback.controller.shutdown()

        assertEquals(ReaderSpeechPhase.Idle, playback.controller.state.value.phase)
        assertEquals(1, playback.engine.shutdownCalls)
        assertEquals(1, playback.focus.abandonCalls)

        // Single-use: a later request must not resurrect the engine.
        playback.controller.speak(1L, verse, "en")
        assertEquals(ReaderSpeechPhase.Idle, playback.controller.state.value.phase)
        assertEquals(1, playback.engine.spoken.size)
    }
}
