package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.testutil.FakeBookNameRepository

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.core.result.CorruptBibleDatabaseException
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Book-chapters and introduction ViewModels over the real corpus
 * (Phase 7 §8, §9, §21): James, Job (duplicate 26), Esther.
 */
@RunWith(RobolectricTestRunner::class)
class BookChaptersViewModelTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var repository: BibleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
        repository = BibleRepositoryImpl(provider)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    private fun settledState(
        timeoutMs: Long = 10_000,
        names: BookNameRepository = FakeBookNameRepository(),
        load: (BookChaptersViewModel) -> Unit
    ): BookChaptersUiState {
        val viewModel = BookChaptersViewModel(repository, names)
        load(viewModel)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (!state.isLoading && (state.book != null || state.error != null)) {
                return state
            }
            Thread.sleep(10)
        }
        error("timed out waiting for chapter state")
    }

    @Test
    fun james_has_five_chapters_in_position_order() {
        val s = settledState { it.load("jakob") }
        assertEquals("James", s.book!!.englishName)
        assertEquals(5, s.chapters.size)
        assertEquals(listOf(1, 2, 3, 4, 5), s.chapters.map { it.position })
        assertNull(s.error)
    }

    @Test
    fun display_name_uses_the_injected_name_repository() {
        val s = settledState(names = FakeBookNameRepository(mapOf("jakob" to "Jakobachem Potr"))) {
            it.load("jakob")
        }
        assertEquals("Jakobachem Potr", s.displayName)
    }

    @Test
    fun job_keeps_both_duplicate_26_records_distinct() {
        val s = settledState { it.load("Jobachem_Pustok") }
        val number26 = s.chapters.filter { it.number == 26 }
        assertEquals("both Job 26 records must be present", 2, number26.size)
        assertEquals(setOf(26, 27), number26.map { it.position }.toSet())
        assertEquals(2, number26.map { it.id }.toSet().size)
        assertEquals(
            "positions must follow source order",
            s.chapters.map { it.position }.sorted(),
            s.chapters.map { it.position }
        )
    }

    @Test
    fun esther_has_ten_chapters() {
        val s = settledState { it.load("Esther") }
        assertEquals("Esther", s.book!!.englishName)
        assertEquals(10, s.chapters.size)
    }

    @Test
    fun unknown_book_reports_book_not_found() {
        val s = settledState { it.load("not-a-book") }
        assertNull(s.book)
        assertEquals(ChaptersError.BOOK_NOT_FOUND, s.error)
        assertTrue(s.chapters.isEmpty())
    }

    @Test
    fun a_data_failure_maps_to_unknown() {
        val viewModel = BookChaptersViewModel(
            FailingChaptersRepository(CorruptBibleDatabaseException("jakob")),
            FakeBookNameRepository()
        )
        viewModel.load("jakob")
        waitForChapters { !viewModel.uiState.value.isLoading }

        assertEquals(ChaptersError.UNKNOWN, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.chapters.isEmpty())
    }

    /** Fails every call with [failure] so the UNKNOWN mapping is deterministic. */
    private class FailingChaptersRepository(
        private val failure: BibleDataException
    ) : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook = throw failure

        override suspend fun getChapters(bookId: String): List<Chapter> = throw failure

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent =
            throw failure

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> = throw failure

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? =
            throw failure

        override suspend fun getVersesCovering(
            bookId: String,
            chapterId: Long,
            verse: Int
        ): List<Verse> = throw failure
    }

    private fun waitForChapters(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        error("timed out waiting for chapter state")
    }
}
