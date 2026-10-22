package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.testutil.FakeBookNameRepository

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.data.app.BookmarkRepositoryImpl
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Reader bookmark integration over the real corpus and the real mutable
 * AppDatabase (Phase 9 §24–§26): add/remove, coexistence with the target
 * highlight, Job duplicate-chapter bookmarks distinguishable by chapterId,
 * reading state, and the Bible-content separation invariant.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderBookmarkIntegrationTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context
    private lateinit var bibleProvider: AssetBibleDatabaseProvider
    private lateinit var bibleRepository: BibleRepository
    private lateinit var appDatabase: AppDatabase
    private lateinit var bookmarkRepository: BookmarkRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        bibleProvider = AssetBibleDatabaseProvider(context)
        bibleRepository = BibleRepositoryImpl(bibleProvider)
        appDatabase = AppDatabase.create(context)
        bookmarkRepository = BookmarkRepositoryImpl(appDatabase)
    }

    @After
    fun tearDown() {
        appDatabase.close()
        bibleProvider.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun awaitCondition(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("timed out waiting for condition")
    }

    private fun awaitLoaded(viewModel: ReaderViewModel): ReaderUiState {
        awaitCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.chapter != null }
        return viewModel.uiState.value
    }

    @Test
    fun bookmark_add_and_remove_reflect_in_reader_state() {
        val firstVerse = runBlocking { bibleRepository.getChapter("jakob", 1).verses.first() }
        val viewModel = ReaderViewModel(bibleRepository, bookmarkRepository, FakeBookNameRepository())

        viewModel.load("jakob", 1, targetVerseId = firstVerse.id)
        awaitLoaded(viewModel)
        viewModel.onTargetApplied()
        awaitCondition { viewModel.uiState.value.selectedVerseId == firstVerse.id }

        // initially not bookmarked
        assertTrue(viewModel.uiState.value.bookmarkedVerseIds.isEmpty())

        // add
        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.contains(firstVerse.id) }
        assertTrue(runBlocking { bookmarkRepository.isBookmarked("jakob", viewModel.uiState.value.chapter!!.id, firstVerse.id) })

        // remove
        viewModel.toggleBookmark()
        awaitCondition { !viewModel.uiState.value.bookmarkedVerseIds.contains(firstVerse.id) }
        assertFalse(runBlocking { bookmarkRepository.isBookmarked("jakob", viewModel.uiState.value.chapter!!.id, firstVerse.id) })
    }

    @Test
    fun target_highlight_and_bookmark_coexist_independently() {
        val firstVerse = runBlocking { bibleRepository.getChapter("jakob", 1).verses.first() }
        val viewModel = ReaderViewModel(bibleRepository, bookmarkRepository, FakeBookNameRepository())

        viewModel.load("jakob", 1, targetVerseId = firstVerse.id)
        awaitLoaded(viewModel)
        viewModel.onTargetApplied()
        awaitCondition { viewModel.uiState.value.highlightedVerseId == firstVerse.id }

        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.contains(firstVerse.id) }

        val state = viewModel.uiState.value
        assertEquals("transient target highlight must stay", firstVerse.id, state.highlightedVerseId)
        assertTrue("persistent bookmark must coexist", state.bookmarkedVerseIds.contains(firstVerse.id))
    }

    @Test
    fun job_duplicate_chapter_bookmarks_are_distinguishable_by_chapter_id() {
        val jobChapter26Verse = runBlocking { bibleRepository.getChapter("Jobachem_Pustok", 26).verses.first() }
        val jobChapter27Verse = runBlocking { bibleRepository.getChapter("Jobachem_Pustok", 27).verses.first() }
        assertEquals("both Job records carry number 26", 26, jobChapter26Verse.chapterNumber)
        assertEquals(26, jobChapter27Verse.chapterNumber)

        val viewModel = ReaderViewModel(bibleRepository, bookmarkRepository, FakeBookNameRepository())
        viewModel.load("Jobachem_Pustok", 26, targetVerseId = jobChapter26Verse.id)
        awaitLoaded(viewModel)
        viewModel.onTargetApplied()
        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.isNotEmpty() }

        viewModel.load("Jobachem_Pustok", 27, targetVerseId = jobChapter27Verse.id)
        awaitLoaded(viewModel)
        viewModel.onTargetApplied()
        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.isNotEmpty() }

        val bookmarks = runBlocking { bookmarkRepository.observeBookmarks().first() }
        assertEquals("two separate bookmarks", 2, bookmarks.size)
        assertEquals("chapterNumber is 26 for both", setOf(26), bookmarks.map { it.chapterNumber }.toSet())
        assertEquals("chapterIds must differ", 2, bookmarks.map { it.chapterId }.toSet().size)
        assertTrue(bookmarks.any { it.chapterPosition == 26 })
        assertTrue(bookmarks.any { it.chapterPosition == 27 })
    }

    @Test
    fun genesis_range_verse_can_be_bookmarked_as_one_record() {
        val chapter16 = runBlocking { bibleRepository.getChapters("Utpoti").first { it.number == 16 } }
        val rangeVerse = runBlocking {
            bibleRepository.getChapter("Utpoti", chapter16.position).verses.first { it.verseStart != it.verseEnd }
        }
        val viewModel = ReaderViewModel(bibleRepository, bookmarkRepository, FakeBookNameRepository())

        viewModel.load("Utpoti", chapter16.position, targetVerseId = rangeVerse.id)
        awaitLoaded(viewModel)
        viewModel.onTargetApplied()
        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.contains(rangeVerse.id) }

        val bookmarks = runBlocking { bookmarkRepository.observeBookmarks().first() }
        val saved = bookmarks.first { it.chapterId == chapter16.id }
        assertEquals(11, saved.verseStart)
        assertEquals(12, saved.verseEnd)
        assertEquals("one source record", 1, bookmarks.count { it.chapterId == chapter16.id && it.verseId == rangeVerse.id })
    }

    @Test
    fun reading_state_is_persisted_on_chapter_open() {
        val viewModel = ReaderViewModel(bibleRepository, bookmarkRepository, FakeBookNameRepository())
        viewModel.load("jakob", 3)
        awaitLoaded(viewModel)

        // Reading state is persisted as part of the same load; wait for it.
        var state: com.biblia.povitrpustok.domain.model.ReadingState? = null
        awaitCondition {
            state = runBlocking { bookmarkRepository.observeReadingState().first() }
            state != null
        }
        val persisted = checkNotNull(state)
        assertEquals("jakob", persisted.bookId)
        assertEquals(3, persisted.chapterPosition)
    }

    @Test
    fun bible_content_is_untouched_by_bookmark_operations() {
        // snapshot the immutable Bible content
        val before = runBlocking {
            val chapter = bibleRepository.getChapter("jakob", 1)
            chapter.verses.size to chapter.verses.first().html
        }

        val firstVerse = runBlocking { bibleRepository.getChapter("jakob", 1).verses.first() }
        val viewModel = ReaderViewModel(bibleRepository, bookmarkRepository, FakeBookNameRepository())
        viewModel.load("jakob", 1, targetVerseId = firstVerse.id)
        awaitLoaded(viewModel)
        viewModel.onTargetApplied()
        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.contains(firstVerse.id) }
        viewModel.toggleBookmark()
        awaitCondition { viewModel.uiState.value.bookmarkedVerseIds.isEmpty() }

        val after = runBlocking {
            val chapter = bibleRepository.getChapter("jakob", 1)
            chapter.verses.size to chapter.verses.first().html
        }
        assertEquals("Bible content must be identical after bookmark ops", before, after)
        // and the mutable state lives in the separate app database file
        assertTrue(context.databaseList().contains(AppDatabase.DATABASE_NAME))
    }
}
