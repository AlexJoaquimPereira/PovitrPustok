package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.testutil.FakeBookNameRepository
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Book introduction loading over the real corpus (Phase 7 §12).
 */
@RunWith(RobolectricTestRunner::class)
class BookIntroductionViewModelTest {

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

    /**
     * A repository that returns one block for [succeedFor] and throws for any
     * other book, so a cross-book failure can be simulated deterministically.
     */
    private class OneBookRepository(private val succeedFor: String) : BibleRepository {
        override suspend fun getBook(bookId: String): BibleBook =
            throw UnsupportedOperationException()

        override suspend fun getChapters(bookId: String): List<Chapter> =
            throw UnsupportedOperationException()

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent =
            throw UnsupportedOperationException()

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> =
            if (bookId == succeedFor) {
                listOf(IntroductionBlock(id = 1, bookId = bookId, position = 1, type = "body", html = "Text"))
            } else {
                throw BibleBookNotFoundException(bookId)
            }

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? =
            throw UnsupportedOperationException()

        override suspend fun getVersesCovering(
            bookId: String,
            chapterId: Long,
            verse: Int
        ): List<Verse> = throw UnsupportedOperationException()
    }

    @Test
    fun a_failed_cross_book_load_does_not_keep_the_previous_books_name() {
        val viewModel = BookIntroductionViewModel(
            OneBookRepository(succeedFor = "jakob"),
            FakeBookNameRepository()
        )

        viewModel.load("jakob")
        waitFor { !viewModel.uiState.value.isLoading }
        assertEquals("Jakobachem", viewModel.uiState.value.bookDisplayName)

        viewModel.load("utpoti")
        waitFor { viewModel.uiState.value.error != null }

        val state = viewModel.uiState.value
        assertEquals("utpoti", state.bookId)
        assertEquals(IntroductionError.BOOK_NOT_FOUND, state.error)
        assertNull("a failed load must not title itself with another book", state.bookDisplayName)
        assertTrue("stale blocks must not survive a new load", state.blocks.isEmpty())
    }

    @Test
    fun a_missing_book_maps_to_book_not_found() {
        val viewModel = BookIntroductionViewModel(
            OneBookRepository(succeedFor = "jakob"),
            FakeBookNameRepository()
        )

        viewModel.load("not-a-book")
        waitFor { !viewModel.uiState.value.isLoading }

        assertEquals(IntroductionError.BOOK_NOT_FOUND, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.blocks.isEmpty())
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("timed out waiting for state")
    }

    @Test
    fun james_introduction_blocks_are_preserved_in_source_order() {
        val viewModel = BookIntroductionViewModel(repository, FakeBookNameRepository())
        viewModel.load("jakob")

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (!state.isLoading && (state.blocks.isNotEmpty() || state.error != null)) {
                assertEquals("James has exactly 8 introduction blocks", 8, state.blocks.size)
                assertEquals("blocks must stay distinct", state.blocks.size, state.blocks.map { it.id }.toSet().size)
                assertEquals(
                    "blocks must be in source order",
                    state.blocks.map { it.position }.sorted(),
                    state.blocks.map { it.position }
                )
                assertTrue(state.blocks.all { it.html.isNotBlank() })
                // F-21: the screen must be able to name its book.
                assertEquals("Jakobachem", state.bookDisplayName)
                return
            }
            Thread.sleep(10)
        }
        fail("timed out waiting for introduction state")
    }
}
