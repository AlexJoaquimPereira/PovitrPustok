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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ReaderViewModel state and navigation tests (Phase 4 §21) with a fake
 * repository — pure JVM, deterministic. The ViewModel's Main dispatcher is
 * a StandardTestDispatcher whose scheduler is shared with runTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun chapter(bookId: String, number: Int, position: Int) =
        Chapter(id = position.toLong(), bookId = bookId, number = number, position = position)

    private fun content(bookId: String, chapter: Chapter) = ChapterContent(
        chapter = chapter,
        headings = listOf(
            Heading(
                id = chapter.id * 100, chapterId = chapter.id,
                position = 1, beforeVerse = 0, html = "<h3 align=center>Head</h3>"
            )
        ),
        verses = listOf(
            Verse(
                id = chapter.id * 1000, chapterId = chapter.id, chapterNumber = chapter.number,
                verseStart = 1, verseEnd = 1, html = "Verse one.", plainText = "Verse one."
            )
        )
    )

    private class FakeRepository(
        private val books: Map<String, BibleBook> = BibleCatalog.books.associateBy { it.id },
        private val chapterLists: Map<String, List<Chapter>> = emptyMap(),
        private val contents: Map<String, Map<Int, ChapterContent>> = emptyMap(),
        val gate: CompletableDeferred<Unit>? = null
    ) : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook =
            books[bookId] ?: throw BibleBookNotFoundException(bookId)

        override suspend fun getChapters(bookId: String): List<Chapter> =
            chapterLists[bookId] ?: emptyList()

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent {
            gate?.await()
            return contents[bookId]?.get(chapterPosition)
                ?: throw BibleChapterNotFoundException(bookId, chapterPosition)
        }

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> = emptyList()

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? = null

        override suspend fun getVersesCovering(bookId: String, chapterId: Long, verse: Int): List<Verse> =
            listOfNotNull(getVerse(bookId, chapterId, verse))
    }

    @Test
    fun loading_state_is_reported_while_request_is_pending() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1)),
            contents = mapOf(bookId to mapOf(1 to content(bookId, ch1))),
            gate = gate
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())

        viewModel.load(bookId, 1)
        runCurrent()

        val pending = viewModel.uiState.value
        assertTrue("must show loading while pending", pending.isLoading)
        assertNull(pending.errorMessage)

        gate.complete(Unit)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value
        assertFalse(loaded.isLoading)
        assertEquals(ch1, loaded.chapter)
        assertNull(loaded.errorMessage)
    }

    @Test
    fun success_state_contains_book_chapter_headings_and_verses() = runTest(dispatcher.scheduler) {
        val bookId = "jakob"
        val book = BibleCatalog.byId(bookId)!!
        val ch1 = chapter(bookId, number = 1, position = 1)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1)),
            contents = mapOf(bookId to mapOf(1 to content(bookId, ch1)))
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load(bookId, 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(book, state.book)
        assertEquals(ch1, state.chapter)
        assertEquals(1, state.headings.size)
        assertEquals(1, state.verses.size)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun error_state_reports_missing_chapter() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(), FakeBookmarkRepository())
        viewModel.load("jakob", 999)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull("expected an error message", state.errorMessage)
        assertNull(state.chapter)
    }

    @Test
    fun error_state_reports_unknown_book() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(), FakeBookmarkRepository())
        viewModel.load("not-a-book", 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("expected an error message", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun previous_and_next_follow_position_not_number() = runTest(dispatcher.scheduler) {
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val ch2 = chapter(bookId, number = 2, position = 2)
        val ch3 = chapter(bookId, number = 3, position = 3)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1, ch2, ch3)),
            contents = mapOf(
                bookId to mapOf(
                    1 to content(bookId, ch1),
                    2 to content(bookId, ch2),
                    3 to content(bookId, ch3)
                )
            )
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load(bookId, 2)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.chapter!!.position)

        viewModel.previous()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.chapter!!.position)
        assertFalse(viewModel.uiState.value.canGoPrevious)
        viewModel.previous()
        advanceUntilIdle()
        assertEquals("must stay at first position", 1, viewModel.uiState.value.chapter!!.position)

        viewModel.next()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.chapter!!.position)
        viewModel.next()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.chapter!!.position)
        assertFalse(viewModel.uiState.value.canGoNext)
        viewModel.next()
        advanceUntilIdle()
        assertEquals("must stay at last position (no cross-book)", 3, viewModel.uiState.value.chapter!!.position)
        assertEquals(bookId, viewModel.uiState.value.chapter!!.bookId)
    }

    @Test
    fun select_chapter_uses_position() = runTest(dispatcher.scheduler) {
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val ch2 = chapter(bookId, number = 2, position = 2)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1, ch2)),
            contents = mapOf(bookId to mapOf(1 to content(bookId, ch1), 2 to content(bookId, ch2)))
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load(bookId, 1)
        advanceUntilIdle()
        viewModel.selectChapter(2)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.chapter!!.position)
    }

    @Test
    fun duplicate_chapter_numbers_are_independently_addressable() = runTest(dispatcher.scheduler) {
        // Job-style duplicates: two records with number 26 at positions 26 and 27.
        val bookId = "Jobachem_Pustok"
        val duplicateA = chapter(bookId, number = 26, position = 26)
        val duplicateB = chapter(bookId, number = 26, position = 27)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(duplicateA, duplicateB)),
            contents = mapOf(
                bookId to mapOf(
                    26 to content(bookId, duplicateA),
                    27 to content(bookId, duplicateB)
                )
            )
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())

        viewModel.load(bookId, 26)
        advanceUntilIdle()
        assertEquals(26, viewModel.uiState.value.chapter!!.number)
        assertEquals(26, viewModel.uiState.value.chapter!!.position)

        viewModel.next()
        advanceUntilIdle()
        assertEquals("next must move by position to the second 26", 26, viewModel.uiState.value.chapter!!.number)
        assertEquals(27, viewModel.uiState.value.chapter!!.position)
        assertFalse("the two records must not collapse into one", viewModel.uiState.value.chapter!!.id == duplicateA.id)

        viewModel.previous()
        advanceUntilIdle()
        assertEquals(26, viewModel.uiState.value.chapter!!.position)
        assertEquals(26, viewModel.uiState.value.chapter!!.number)
    }
}
