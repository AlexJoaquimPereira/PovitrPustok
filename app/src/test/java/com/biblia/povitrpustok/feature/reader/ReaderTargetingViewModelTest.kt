package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleChapterNotFoundException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reader verse-targeting state tests (Phase 8 §19) with a fake repository:
 * target located by Verse.id only, one-shot consumption, range verse stays
 * one record, invalid target never crashes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTargetingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepository(
        private val books: Map<String, BibleBook> = BibleCatalog.books.associateBy { it.id },
        private val contents: Map<String, Map<Int, ChapterContent>> = emptyMap()
    ) : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook =
            books[bookId] ?: throw BibleBookNotFoundException(bookId)

        override suspend fun getChapters(bookId: String): List<Chapter> = emptyList()

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent =
            contents[bookId]?.get(chapterPosition)
                ?: throw BibleChapterNotFoundException(bookId, chapterPosition)

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> = emptyList()

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? = null

        override suspend fun getVersesCovering(bookId: String, chapterId: Long, verse: Int): List<Verse> =
            listOfNotNull(getVerse(bookId, chapterId, verse))
    }

    /** Repository whose getChapter can be held in flight per position. */
    private class GatedRepository(
        private val contents: Map<String, Map<Int, ChapterContent>>,
        private val gates: Map<Int, CompletableDeferred<Unit>>
    ) : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook =
            BibleCatalog.byId(bookId) ?: throw BibleBookNotFoundException(bookId)

        override suspend fun getChapters(bookId: String): List<Chapter> = emptyList()

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent {
            gates[chapterPosition]?.await()
            return contents[bookId]?.get(chapterPosition)
                ?: throw BibleChapterNotFoundException(bookId, chapterPosition)
        }

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> = emptyList()

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? = null

        override suspend fun getVersesCovering(bookId: String, chapterId: Long, verse: Int): List<Verse> =
            listOfNotNull(getVerse(bookId, chapterId, verse))
    }

    private fun content(verses: List<Verse>) = ChapterContent(
        chapter = Chapter(id = 1, bookId = "jakob", number = 1, position = 1),
        headings = emptyList(),
        verses = verses
    )

    private fun contentAt(position: Int, verses: List<Verse>) = ChapterContent(
        chapter = Chapter(id = position.toLong(), bookId = "jakob", number = position, position = position),
        headings = emptyList(),
        verses = verses
    )

    private fun verse(id: Long, start: Int, end: Int) = Verse(
        id = id, chapterId = 1, chapterNumber = 1,
        verseStart = start, verseEnd = end, html = "v$id", plainText = "v$id"
    )

    private val chapter1 = content(
        listOf(verse(100, 1, 1), verse(101, 2, 2), verse(102, 3, 3))
    )

    @Test
    fun no_target_means_no_target_state() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(contents = mapOf("jakob" to mapOf(1 to chapter1))), FakeBookmarkRepository())
        viewModel.load("jakob", 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.targetVerseId)
        assertNull(state.highlightedVerseId)
        assertFalse(state.targetMissing)
    }

    @Test
    fun valid_target_is_located_by_verse_id() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(contents = mapOf("jakob" to mapOf(1 to chapter1))), FakeBookmarkRepository())
        viewModel.load("jakob", 1, targetVerseId = 101L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(101L, state.targetVerseId)
        assertFalse(state.targetMissing)
    }

    @Test
    fun invalid_target_sets_missing_without_crash() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(contents = mapOf("jakob" to mapOf(1 to chapter1))), FakeBookmarkRepository())
        viewModel.load("jakob", 1, targetVerseId = 9999L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("missing target must be flagged", state.targetMissing)
        assertNull(state.targetVerseId)
        assertTrue("chapter must still render", state.verses.isNotEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun target_is_consumed_once_and_becomes_highlight() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(contents = mapOf("jakob" to mapOf(1 to chapter1))), FakeBookmarkRepository())
        viewModel.load("jakob", 1, targetVerseId = 102L)
        advanceUntilIdle()
        assertEquals(102L, viewModel.uiState.value.targetVerseId)

        viewModel.onTargetApplied()
        val state = viewModel.uiState.value
        assertNull("target must be consumed (one-shot)", state.targetVerseId)
        assertEquals("highlight must be the target only", 102L, state.highlightedVerseId)

        viewModel.clearHighlight()
        assertNull(viewModel.uiState.value.highlightedVerseId)
    }

    @Test
    fun source_range_verse_stays_one_target() = runTest(dispatcher.scheduler) {
        val withRange = content(
            listOf(verse(1, 1, 1), verse(777, 11, 12), verse(778, 13, 13))
        )
        val viewModel = ReaderViewModel(FakeRepository(contents = mapOf("jakob" to mapOf(1 to withRange))), FakeBookmarkRepository())
        viewModel.load("jakob", 1, targetVerseId = 777L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(777L, state.targetVerseId)
        val target = state.verses.first { it.id == state.targetVerseId }
        assertEquals(11, target.verseStart)
        assertEquals(12, target.verseEnd)
        assertEquals("one source record", 1, state.verses.count { it.verseStart == 11 && it.verseEnd == 12 })
    }

    @Test
    fun newest_load_wins_when_previous_is_still_in_flight() = runTest(dispatcher.scheduler) {
        val gate1 = CompletableDeferred<Unit>()
        val chapter2 = contentAt(2, listOf(verse(200, 1, 1)))
        val repository = GatedRepository(
            contents = mapOf("jakob" to mapOf(1 to chapter1, 2 to chapter2)),
            gates = mapOf(1 to gate1)
        )
        val bookmarks = FakeBookmarkRepository()
        val viewModel = ReaderViewModel(repository, bookmarks)

        // first request stays in flight (gated)
        viewModel.load("jakob", 1)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        // second request supersedes the first
        viewModel.load("jakob", 2)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.chapter!!.position)
        assertFalse(viewModel.uiState.value.isLoading)

        // the stale first request completes late: it must not overwrite state
        gate1.complete(Unit)
        advanceUntilIdle()
        assertEquals("stale chapter must not win", 2, viewModel.uiState.value.chapter!!.position)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(200L, viewModel.uiState.value.verses.first().id)

        // reading state must end at the newest chapter
        val reading = bookmarks.readingStateValue()
        assertEquals(2, reading.chapterPosition)
    }
}
