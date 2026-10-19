package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.core.html.BibleHtmlRenderer
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.domain.repository.BibleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Reader tests against the real corpus (Phase 4 §21): James, Job's
 * duplicate chapter numbers, Esther, verse ranges, formatting.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelRealDataTest {

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

    /** Drives the Robolectric main looper until the reader state settles. */
    private fun await(viewModel: ReaderViewModel, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (!state.isLoading && (state.chapter != null || state.errorMessage != null)) return
            Thread.sleep(10)
        }
        fail("timed out waiting for the reader state")
    }

    @Test
    fun james_chapter_loads_book_chapter_headings_and_verses() {
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load("jakob", 1)
        await(viewModel)

        val state = viewModel.uiState.value
        assertEquals("James", state.book!!.englishName)
        assertEquals(1, state.chapter!!.position)
        assertEquals("jakob", state.chapter!!.bookId)
        assertTrue("expected headings", state.headings.isNotEmpty())
        assertTrue("expected verses", state.verses.isNotEmpty())
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun job_duplicate_26_records_open_independently_and_navigate_by_position() {
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load("Jobachem_Pustok", 26)
        await(viewModel)

        val first = viewModel.uiState.value.chapter!!
        assertEquals(26, first.number)
        assertEquals(26, first.position)

        viewModel.next()
        await(viewModel)
        val second = viewModel.uiState.value.chapter!!
        assertEquals("both records carry source number 26", 26, second.number)
        assertEquals(27, second.position)
        assertTrue("the two 26 records must stay distinct", first.id != second.id)

        viewModel.previous()
        await(viewModel)
        assertEquals(26, viewModel.uiState.value.chapter!!.position)
        assertEquals(26, viewModel.uiState.value.chapter!!.number)
    }

    @Test
    fun esther_chapter_loads() {
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load("Esther", 1)
        await(viewModel)

        val state = viewModel.uiState.value
        assertEquals("Esther", state.book!!.englishName)
        assertEquals(1, state.chapter!!.position)
        assertTrue(state.verses.isNotEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun verse_range_11_12_stays_one_verse() {
        val chapters = runBlocking { repository.getChapters("Utpoti") }
        val chapter16 = chapters.first { it.number == 16 }

        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load("Utpoti", chapter16.position)
        await(viewModel)

        val ranged = viewModel.uiState.value.verses.filter { it.verseStart != it.verseEnd }
        assertTrue("Genesis 16 must contain range verses", ranged.isNotEmpty())
        val rangeVerse = ranged.first { it.verseStart == 11 }
        assertEquals(11, rangeVerse.verseStart)
        assertEquals(12, rangeVerse.verseEnd)
        // one source record, not split into two
        assertEquals(1, viewModel.uiState.value.verses.count { it.verseStart == 11 && it.verseEnd == 12 })
    }

    @Test
    fun poetic_formatting_survives_the_reader_pipeline() {
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository())
        viewModel.load("Jobachem_Pustok", 4)
        await(viewModel)

        val poetic = viewModel.uiState.value.verses.firstOrNull { it.html.contains("<br") }
        assertNotNull("expected a poetic verse with <br> in Job 4", poetic)

        val document = BibleHtmlParser().parse(poetic!!.html)
        val rendered = BibleHtmlRenderer.render(document)
        assertTrue("line breaks must survive", rendered.text.contains("\n"))
    }
}
