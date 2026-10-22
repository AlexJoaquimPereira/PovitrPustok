package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.testutil.FakeBookNameRepository

import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleChapterNotFoundException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.ReaderPassageSpec
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        var failure: Exception? = null

        override suspend fun getBook(bookId: String): BibleBook =
            books[bookId] ?: throw BibleBookNotFoundException(bookId)

        override suspend fun getChapters(bookId: String): List<Chapter> =
            chapterLists[bookId] ?: emptyList()

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent {
            gate?.await()
            failure?.let { throw it }
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
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

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
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())
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
    fun passages_load_every_chapter_and_observe_their_bookmarks() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val ch2 = chapter(bookId, number = 2, position = 2)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1, ch2)),
                contents = mapOf(
                    bookId to mapOf(1 to content(bookId, ch1), 2 to content(bookId, ch2))
                )
            )
            val bookmarks = FakeBookmarkRepository()
            bookmarks.addBookmark(
                bookId = bookId,
                chapterId = ch2.id,
                chapterPosition = ch2.position,
                chapterNumber = ch2.number,
                verseId = 2000L,
                verseStart = 1,
                verseEnd = 1
            )
            val viewModel = ReaderViewModel(repository, bookmarks, FakeBookNameRepository())

            viewModel.load(
                bookId = bookId,
                chapterPosition = 1,
                passages = listOf(
                    ReaderPassageSpec(1, 1, 1),
                    ReaderPassageSpec(2, 1, 1)
                )
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.passageChapters.size)
            assertEquals(ch1, state.chapter)
            assertTrue(
                "bookmarks from every passage chapter must be observed",
                state.bookmarkedVerseIds.contains(2000L)
            )
        }

    @Test
    fun passage_target_in_a_later_chapter_is_applied() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val ch2 = chapter(bookId, number = 2, position = 2)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1, ch2)),
                contents = mapOf(
                    bookId to mapOf(1 to content(bookId, ch1), 2 to content(bookId, ch2))
                )
            )
            val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

            viewModel.load(
                bookId = bookId,
                chapterPosition = 1,
                targetVerseId = 2000L, // content() id for chapter 2's verse
                passages = listOf(
                    ReaderPassageSpec(1, 1, 1),
                    ReaderPassageSpec(2, 1, 1)
                )
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2000L, state.targetVerseId)
            assertFalse(state.targetMissing)
            assertEquals(2, state.passageChapters.size)
        }

    @Test
    fun retry_replays_a_multi_chapter_passage() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val ch2 = chapter(bookId, number = 2, position = 2)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1, ch2)),
                contents = mapOf(
                    bookId to mapOf(1 to content(bookId, ch1), 2 to content(bookId, ch2))
                )
            )
            val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

            viewModel.load(
                bookId = bookId,
                chapterPosition = 1,
                passages = listOf(
                    ReaderPassageSpec(1, 1, 1),
                    ReaderPassageSpec(2, 1, 1)
                )
            )
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.passageChapters.size)

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.passageChapters.size)
            assertEquals(ch1, viewModel.uiState.value.chapter)
        }

    @Test
    fun long_press_toggles_a_bookmark_and_emits_one_confirmation() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1)),
                contents = mapOf(bookId to mapOf(1 to content(bookId, ch1)))
            )
            val bookmarks = FakeBookmarkRepository()
            val viewModel = ReaderViewModel(repository, bookmarks, FakeBookNameRepository())
            viewModel.load(bookId, 1)
            advanceUntilIdle()

            val events = mutableListOf<ReaderEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events += it }
            }

            val verseId = 1000L // content(): chapter.id * 1000
            viewModel.toggleBookmarkFor(verseId)
            advanceUntilIdle()

            assertEquals(1, events.size)
            val event = events.single() as ReaderEvent.BookmarkChanged
            assertTrue(event.added)
            assertEquals(bookId, event.bookId)
            assertEquals(ch1.id, event.chapterId)
            assertTrue(bookmarks.isBookmarked(bookId, ch1.id, verseId))

            // Undo reverses the same verse, without a second confirmation.
            viewModel.undoBookmarkChange(event)
            advanceUntilIdle()
            assertEquals(1, events.size)
            assertFalse(bookmarks.isBookmarked(bookId, ch1.id, verseId))
        }

    @Test
    fun undo_works_after_navigating_to_another_chapter() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val ch2 = chapter(bookId, number = 2, position = 2)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1, ch2)),
                contents = mapOf(
                    bookId to mapOf(
                        1 to content(bookId, ch1),
                        2 to content(bookId, ch2)
                    )
                )
            )
            val bookmarks = FakeBookmarkRepository()
            val viewModel = ReaderViewModel(repository, bookmarks, FakeBookNameRepository())
            viewModel.load(bookId, 1)
            advanceUntilIdle()

            val events = mutableListOf<ReaderEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events += it }
            }

            viewModel.toggleBookmarkFor(1000L)
            advanceUntilIdle()
            val event = events.single() as ReaderEvent.BookmarkChanged
            assertTrue(bookmarks.isBookmarked(bookId, ch1.id, 1000L))

            // The snackbar action can arrive after the user navigates on.
            viewModel.selectChapter(2)
            advanceUntilIdle()
            viewModel.undoBookmarkChange(event)
            advanceUntilIdle()

            assertFalse(bookmarks.isBookmarked(bookId, ch1.id, 1000L))
        }

    @Test
    fun verse_number_target_opens_the_full_chapter_without_a_passage() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1)),
                contents = mapOf(bookId to mapOf(1 to content(bookId, ch1)))
            )
            val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

            viewModel.load(bookId, 1, targetVerseNumber = 1)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull("bookmark navigation must not create a passage", state.passage)
            assertEquals(1000L, state.targetVerseId)
            assertFalse(state.targetMissing)
        }

    @Test
    fun show_full_chapter_leaves_passage_mode_and_retry_stays_full() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1)),
                contents = mapOf(bookId to mapOf(1 to content(bookId, ch1)))
            )
            val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

            viewModel.load(bookId, 1, passageStart = 1, passageEnd = 1)
            advanceUntilIdle()
            assertNotNull("expected passage mode", viewModel.uiState.value.passage)

            viewModel.showFullChapter()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.passage)

            // READER-01: retry replays the full chapter, not the old passage.
            viewModel.retry()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.passage)
            assertEquals(ch1, viewModel.uiState.value.chapter)
        }

    @Test
    fun display_name_is_resolved_and_survives_a_failed_first_load() =
        runTest(dispatcher.scheduler) {
            val bookId = "jakob"
            val ch1 = chapter(bookId, number = 1, position = 1)
            val repository = FakeRepository(
                chapterLists = mapOf(bookId to listOf(ch1)),
                contents = mapOf(bookId to mapOf(1 to content(bookId, ch1)))
            )
            val viewModel = ReaderViewModel(
                repository,
                FakeBookmarkRepository(),
                FakeBookNameRepository(mapOf("jakob" to "Jakobachem Potr"))
            )

            viewModel.load(bookId, 1)
            advanceUntilIdle()
            assertEquals("Jakobachem Potr", viewModel.uiState.value.bookDisplayName)

            // F-12: a failing first load for another book still shows a name.
            viewModel.load("Utpoti", 999)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.errorMessage)
            assertEquals("Utpoti", viewModel.uiState.value.bookDisplayName)
        }

    @Test
    fun error_state_reports_missing_chapter() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(), FakeBookmarkRepository(), FakeBookNameRepository())
        viewModel.load("jakob", 999)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull("expected an error message", state.errorMessage)
        assertNull(state.chapter)
    }

    @Test
    fun error_state_reports_unknown_book() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(FakeRepository(), FakeBookmarkRepository(), FakeBookNameRepository())
        viewModel.load("not-a-book", 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("expected an error message", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun failed_navigation_clears_stale_target_selection_and_passage() = runTest(dispatcher.scheduler) {
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val ch2 = chapter(bookId, number = 2, position = 2)
        val content1 = content(bookId, ch1)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1, ch2)),
            contents = mapOf(bookId to mapOf(1 to content1))
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

        viewModel.load(bookId, 1, passageStart = 1, passageEnd = 1)
        advanceUntilIdle()
        val verseId = content1.verses.first().id
        viewModel.onVerseSelected(verseId)
        assertEquals(verseId, viewModel.uiState.value.selectedVerseId)

        repository.failure = IllegalStateException("boom")
        viewModel.load(bookId, 2, targetVerseId = verseId, passageStart = 1, passageEnd = 2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals("the last good chapter is retained", 1, state.chapter?.position)
        assertNull("selection must not leak across a failed navigation", state.selectedVerseId)
        assertNull(state.highlightedVerseId)
        assertNull(state.targetVerseId)
        assertNull("a stale passage must not keep filtering the old content", state.passage)
        assertFalse(state.targetMissing)
    }

    @Test
    fun retry_replays_target_and_passage_after_an_error() = runTest(dispatcher.scheduler) {
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val chapterContent = content(bookId, ch1)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1)),
            contents = mapOf(bookId to mapOf(1 to chapterContent))
        ).apply { failure = IllegalStateException("boom") }
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())
        val verseId = chapterContent.verses.first().id

        viewModel.load(bookId, 1, targetVerseId = verseId, passageStart = 1, passageEnd = 1)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        repository.failure = null
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals("the retry must keep the verse target", verseId, state.targetVerseId)
        assertEquals("the retry must keep the passage", ReaderPassage(1, 1), state.passage)
    }

    @Test
    fun rapid_double_toggle_ends_unbookmarked_without_duplicates() = runTest(dispatcher.scheduler) {
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val chapterContent = content(bookId, ch1)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1)),
            contents = mapOf(bookId to mapOf(1 to chapterContent))
        )
        val bookmarks = FakeBookmarkRepository()
        val viewModel = ReaderViewModel(repository, bookmarks, FakeBookNameRepository())

        viewModel.load(bookId, 1)
        advanceUntilIdle()
        val verseId = chapterContent.verses.first().id
        viewModel.onVerseSelected(verseId)

        // Two taps before the observation flow can update the UI snapshot.
        viewModel.toggleBookmark()
        viewModel.toggleBookmark()
        advanceUntilIdle()

        val saved = bookmarks.observeBookmarksForChapter(bookId, ch1.id).first()
        assertTrue(
            "two rapid toggles must end unbookmarked, got ${saved.size} row(s)",
            saved.isEmpty()
        )
    }

    @Test
    fun superseded_load_never_publishes_an_error() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val bookId = "jakob"
        val ch1 = chapter(bookId, number = 1, position = 1)
        val ch2 = chapter(bookId, number = 2, position = 2)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1, ch2)),
            contents = mapOf(bookId to mapOf(1 to content(bookId, ch1), 2 to content(bookId, ch2))),
            gate = gate
        )
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

        viewModel.load(bookId, 1)
        runCurrent() // first load is suspended on the gate
        viewModel.load(bookId, 2) // cancels the first load
        runCurrent()

        assertNull(
            "a cancelled load must never surface an error",
            viewModel.uiState.value.errorMessage
        )

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.chapter?.position)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun unexpected_runtime_failure_is_a_controlled_error() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository().apply { failure = IllegalStateException("boom") }
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

        viewModel.load("jakob", 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("the Reader must not stay loading", state.isLoading)
        assertNotNull("unexpected failures must surface a controlled error", state.errorMessage)
        assertNull("no chapter may be published", state.chapter)
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
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())
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
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())
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
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())

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
