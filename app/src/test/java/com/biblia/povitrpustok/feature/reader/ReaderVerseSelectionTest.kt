package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleChapterNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Verse selection + bookmark flow (corrective phase Issue 6): tapping a
 * verse selects it, the top-bar bookmark action applies to that exact
 * verse, multiple verses bookmark independently, and a new chapter resets
 * the selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderVerseSelectionTest {

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
        headings = emptyList(),
        verses = (1..10).map { n ->
            Verse(
                id = chapter.id * 1000 + n,
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                verseStart = n,
                verseEnd = n,
                html = "Verse $n.",
                plainText = "Verse $n."
            )
        }
    )

    private class FakeRepository(
        private val books: Map<String, BibleBook> = BibleCatalog.books.associateBy { it.id },
        private val chapterLists: Map<String, List<Chapter>> = emptyMap(),
        private val contents: Map<String, Map<Int, ChapterContent>> = emptyMap()
    ) : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook =
            books[bookId] ?: throw BibleBookNotFoundException(bookId)

        override suspend fun getChapters(bookId: String): List<Chapter> =
            chapterLists[bookId] ?: emptyList()

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent =
            contents[bookId]?.get(chapterPosition)
                ?: throw BibleChapterNotFoundException(bookId, chapterPosition)

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> = emptyList()
        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? = null
    }

    private class RecordingBookmarkRepository : BookmarkRepository {
        val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
        val added = mutableListOf<Bookmark>()

        override fun observeBookmarks(): Flow<List<Bookmark>> = bookmarks
        override fun observeBookmarksForChapter(bookId: String, chapterId: Long): Flow<List<Bookmark>> = bookmarks

        override suspend fun isBookmarked(bookId: String, chapterId: Long, verseId: Long): Boolean =
            bookmarks.value.any { it.bookId == bookId && it.chapterId == chapterId && it.verseId == verseId }

        override suspend fun addBookmark(
            bookId: String, chapterId: Long, chapterPosition: Int, chapterNumber: Int,
            verseId: Long, verseStart: Int, verseEnd: Int
        ): Bookmark? {
            val bookmark = Bookmark(
                id = bookmarks.value.size + 1L, bookId = bookId, chapterId = chapterId,
                chapterPosition = chapterPosition, verseId = verseId, chapterNumber = chapterNumber,
                verseStart = verseStart, verseEnd = verseEnd, createdAt = System.currentTimeMillis()
            )
            bookmarks.value = bookmarks.value + bookmark
            added += bookmark
            return bookmark
        }

        override suspend fun removeBookmark(bookId: String, chapterId: Long, verseId: Long) {
            bookmarks.value = bookmarks.value.filterNot { it.bookId == bookId && it.chapterId == chapterId && it.verseId == verseId }
        }

        override suspend fun removeBookmarkById(id: Long) {
            bookmarks.value = bookmarks.value.filterNot { it.id == id }
        }

        private val _readingState = MutableStateFlow<ReadingState?>(null)
        override fun observeReadingState(): Flow<ReadingState?> = _readingState
        override suspend fun updateReadingState(bookId: String, chapterPosition: Int) {
            _readingState.value = ReadingState(bookId, chapterPosition, System.currentTimeMillis())
        }
        override suspend fun clearReadingState() { _readingState.value = null }
    }

    private fun viewModel(): Pair<ReaderViewModel, RecordingBookmarkRepository> {
        val bookId = "jakob"
        val ch1 = chapter(bookId, 1, 1)
        val ch2 = chapter(bookId, 2, 2)
        val repository = FakeRepository(
            chapterLists = mapOf(bookId to listOf(ch1, ch2)),
            contents = mapOf(
                bookId to mapOf(1 to content(bookId, ch1), 2 to content(bookId, ch2))
            )
        )
        val bookmarks = RecordingBookmarkRepository()
        return ReaderViewModel(repository, bookmarks) to bookmarks
    }

    @Test
    fun tapping_a_verse_selects_it_and_tapping_again_deselects() = runTest(dispatcher.scheduler) {
        val (viewModel, _) = viewModel()
        viewModel.load("jakob", 1)
        runCurrent()

        val verse5 = 1L * 1000 + 5
        viewModel.onVerseSelected(verse5)
        runCurrent()
        assertEquals(verse5, viewModel.uiState.value.selectedVerseId)

        viewModel.onVerseSelected(verse5)
        runCurrent()
        assertNull("tapping the selected verse again clears selection", viewModel.uiState.value.selectedVerseId)
    }

    @Test
    fun toggle_bookmark_applies_to_the_selected_verse() = runTest(dispatcher.scheduler) {
        val (viewModel, bookmarks) = viewModel()
        viewModel.load("jakob", 1)
        runCurrent()

        val verse5 = 1L * 1000 + 5
        viewModel.onVerseSelected(verse5)
        runCurrent()
        viewModel.toggleBookmark()
        runCurrent()

        assertEquals(1, bookmarks.added.size)
        assertEquals("bookmark must target verse 5", verse5, bookmarks.added.single().verseId)

        viewModel.toggleBookmark()
        runCurrent()
        assertTrue("unbookmark removes it", bookmarks.bookmarks.value.isEmpty())
    }

    @Test
    fun two_verses_bookmark_independently() = runTest(dispatcher.scheduler) {
        val (viewModel, bookmarks) = viewModel()
        viewModel.load("jakob", 1)
        runCurrent()

        val verse5 = 1L * 1000 + 5
        val verse7 = 1L * 1000 + 7

        viewModel.onVerseSelected(verse5); runCurrent(); viewModel.toggleBookmark(); runCurrent()
        viewModel.onVerseSelected(verse7); runCurrent(); viewModel.toggleBookmark(); runCurrent()

        val ids = bookmarks.bookmarks.value.map { it.verseId }.toSet()
        assertEquals(setOf(verse5, verse7), ids)

        // removing one leaves the other
        viewModel.onVerseSelected(verse5); runCurrent(); viewModel.toggleBookmark(); runCurrent()
        assertEquals(setOf(verse7), bookmarks.bookmarks.value.map { it.verseId }.toSet())
    }

    @Test
    fun selection_resets_when_a_new_chapter_loads() = runTest(dispatcher.scheduler) {
        val (viewModel, _) = viewModel()
        viewModel.load("jakob", 1)
        runCurrent()
        viewModel.onVerseSelected(1L * 1000 + 5)
        runCurrent()
        assertEquals(1L * 1000 + 5, viewModel.uiState.value.selectedVerseId)

        viewModel.load("jakob", 2)
        runCurrent()
        assertNull("a new chapter clears the selection", viewModel.uiState.value.selectedVerseId)
    }

    @Test
    fun bookmark_survives_leaving_and_returning_to_the_chapter() = runTest(dispatcher.scheduler) {
        val (viewModel, bookmarks) = viewModel()
        viewModel.load("jakob", 1)
        runCurrent()
        val verse5 = 1L * 1000 + 5
        viewModel.onVerseSelected(verse5); runCurrent(); viewModel.toggleBookmark(); runCurrent()
        assertTrue(bookmarks.bookmarks.value.isNotEmpty())

        // leave the reader (navigate away) and return to chapter 1
        viewModel.load("jakob", 2)
        runCurrent()
        viewModel.load("jakob", 1)
        runCurrent()

        assertTrue(
            "verse 5 must still be bookmarked after leave/return",
            viewModel.uiState.value.bookmarkedVerseIds.contains(verse5)
        )
    }
}
