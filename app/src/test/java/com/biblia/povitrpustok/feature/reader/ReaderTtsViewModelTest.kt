package com.biblia.povitrpustok.feature.reader

import androidx.lifecycle.ViewModelStore
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.testutil.FakeAudioFocusController
import com.biblia.povitrpustok.testutil.FakeBookNameRepository
import com.biblia.povitrpustok.testutil.RecordingTtsEngine
import com.biblia.povitrpustok.testutil.RecordingTtsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F2-11/F2-12 at the ViewModel boundary: the Reader owns playback, shows the
 * honest missing-voice state for Romi Konkani, keeps the spoken verse
 * highlighted, stops on chapter change, and shuts the engine down when it is
 * cleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTtsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val bookId = "jakob"
    private val chapter = Chapter(id = 1L, bookId = bookId, number = 1, position = 1)
    private val nextChapter = Chapter(id = 2L, bookId = bookId, number = 2, position = 2)
    private val verseId = 1_005L

    private val engine = RecordingTtsEngine()
    private val factory = RecordingTtsFactory(engine)
    private val focus = FakeAudioFocusController()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository() = object : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook =
            BibleCatalog.byId(bookId) ?: error("unknown book")

        override suspend fun getChapters(bookId: String): List<Chapter> =
            listOf(chapter, nextChapter)

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent {
            val loaded = if (chapterPosition == nextChapter.position) nextChapter else chapter
            return ChapterContent(
                chapter = loaded,
                headings = listOf(
                    Heading(
                        id = loaded.id * 100,
                        chapterId = loaded.id,
                        position = 1,
                        beforeVerse = 0,
                        html = "<h3>Head</h3>"
                    )
                ),
                verses = listOf(
                    Verse(
                        id = loaded.id * 1_000 + 5,
                        chapterId = loaded.id,
                        chapterNumber = loaded.number,
                        verseStart = 5,
                        verseEnd = 5,
                        html = "Apolleam bhaxen",
                        plainText = "Apolleam bhaxen"
                    )
                )
            )
        }

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> =
            emptyList()

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? =
            null

        override suspend fun getVersesCovering(
            bookId: String,
            chapterId: Long,
            verse: Int
        ): List<Verse> = emptyList()
    }

    private fun viewModel(): Pair<ReaderViewModel, ReaderSpeechController> {
        val controller = ReaderSpeechController(factory, focus)
        val model = ReaderViewModel(
            repository = repository(),
            bookmarkRepository = FakeBookmarkRepository(),
            bookNames = FakeBookNameRepository(),
            speech = controller
        )
        return model to controller
    }

    @Test
    fun reading_a_konkani_verse_shows_the_missing_voice_state_and_speaks_nothing() =
        runTest(dispatcher.scheduler) {
            val (model, _) = viewModel()
            model.load(bookId, 1)
            runCurrent()

            model.readVerse(verseId)
            runCurrent()

            val speech = model.speechState.value
            assertEquals(ReaderSpeechPhase.Unavailable, speech.phase)
            assertEquals(ReaderSpeechIssue.NoKonkaniVoice, speech.issue)
            assertEquals("the English engine is never created", 0, factory.createCalls)
            assertTrue(engine.spoken.isEmpty())
            assertEquals("no focus is taken for a silent request", 0, focus.requestCalls)
        }

    @Test
    fun reading_a_verse_scrolls_to_it() = runTest(dispatcher.scheduler) {
        val (model, _) = viewModel()
        model.load(bookId, 1)
        runCurrent()

        model.readVerse(verseId)
        runCurrent()

        assertEquals(
            "the spoken verse becomes the one-shot scroll target",
            verseId,
            model.uiState.value.targetVerseId
        )
    }

    @Test
    fun the_spoken_verse_keeps_its_highlight_until_playback_ends() =
        runTest(dispatcher.scheduler) {
            val (model, controller) = viewModel()
            model.load(bookId, 1)
            runCurrent()

            // Drive the collaborator directly: the English path becomes
            // reachable in the app only when an English edition ships (1.4),
            // but the ViewModel's highlight sync must already work.
            controller.speak(verseId, "In the beginning", "en")
            runCurrent()
            factory.completeInit()
            runCurrent()

            assertEquals(ReaderSpeechPhase.Speaking, model.speechState.value.phase)
            assertEquals(verseId, model.uiState.value.highlightedVerseId)

            model.clearHighlight()
            runCurrent()
            assertEquals(
                "the two-second target fade must not dim the spoken verse",
                verseId,
                model.uiState.value.highlightedVerseId
            )

            model.stopSpeech()
            runCurrent()
            assertNull(model.uiState.value.highlightedVerseId)
        }

    @Test
    fun changing_chapter_stops_playback() = runTest(dispatcher.scheduler) {
        val (model, controller) = viewModel()
        model.load(bookId, 1)
        runCurrent()
        controller.speak(verseId, "In the beginning", "en")
        runCurrent()
        factory.completeInit()
        runCurrent()
        assertEquals(ReaderSpeechPhase.Speaking, model.speechState.value.phase)

        model.load(bookId, nextChapter.position)
        runCurrent()

        assertEquals(ReaderSpeechPhase.Idle, model.speechState.value.phase)
        assertEquals("the utterance is stopped with the chapter", 1, engine.stopCalls)
        assertNull(model.uiState.value.highlightedVerseId)
    }

    @Test
    fun pause_and_resume_are_exposed_to_the_screen() = runTest(dispatcher.scheduler) {
        val (model, controller) = viewModel()
        model.load(bookId, 1)
        runCurrent()
        controller.speak(verseId, "In the beginning", "en")
        runCurrent()
        factory.completeInit()
        runCurrent()

        model.toggleSpeechPause()
        runCurrent()
        assertEquals(ReaderSpeechPhase.Paused, model.speechState.value.phase)

        model.toggleSpeechPause()
        runCurrent()
        assertEquals(ReaderSpeechPhase.Speaking, model.speechState.value.phase)
    }

    @Test
    fun dismissing_the_notice_returns_to_idle() = runTest(dispatcher.scheduler) {
        val (model, _) = viewModel()
        model.load(bookId, 1)
        runCurrent()
        model.readVerse(verseId)
        runCurrent()

        model.dismissSpeechIssue()
        runCurrent()

        assertEquals(ReaderSpeechPhase.Idle, model.speechState.value.phase)
        assertNull(model.speechState.value.issue)
    }

    @Test
    fun clearing_the_view_model_shuts_the_engine_down() = runTest(dispatcher.scheduler) {
        val (model, controller) = viewModel()
        model.load(bookId, 1)
        runCurrent()
        controller.speak(verseId, "In the beginning", "en")
        runCurrent()
        factory.completeInit()
        runCurrent()

        val store = ViewModelStore()
        store.put("reader", model)
        store.clear()

        assertEquals(1, engine.shutdownCalls)
        assertEquals("focus is released when the screen goes away", 1, focus.abandonCalls)
    }
}
