package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Book display names sourced from each book's own database title block
 * (corrective phase Issue 1), against the real corpus.
 */
@RunWith(RobolectricTestRunner::class)
class BookTitleRepositoryTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context
    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var repository: BookTitleRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = AssetBibleDatabaseProvider(context)
        repository = BookTitleRepositoryImpl(provider)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun titles_are_sourced_and_normalized_from_the_database() = runBlocking {
        // Issue 2: whitespace noise is normalized, punctuation preserved
        assertEquals("JAKOBACHEM POTR", repository.getDisplayName("jakob"))
        assertEquals("UTPOTI", repository.getDisplayName("Utpoti"))
        assertEquals("JOBACHEM PUSTOK", repository.getDisplayName("Jobachem_Pustok"))
        assertEquals("ESTHER", repository.getDisplayName("Esther"))
        assertEquals("JUANVACHEM POILEM POTR", repository.getDisplayName("juanv1"))
        // previously double-spaced titles now normalize to single spaces
        assertEquals("JOSHUACHEM PUSTOK", repository.getDisplayName("Joshuachem_Pustok"))
        assertEquals("DUSRI SOMURT", repository.getDisplayName("Dusri_Somurt"))
        assertEquals("STOTRANCHEM PUSTOK", repository.getDisplayName("Stotranchem_Pustok"))
        assertEquals("LEVI – XASTR", repository.getDisplayName("Levi_Xastr"))
    }

    @Test
    fun load_all_covers_every_book_with_a_title() = runBlocking {
        val names = repository.loadAllDisplayNames()

        // 72 of 73 books have an introduction title block (2 Kings has none)
        assertTrue("expected titles for 72 books, got ${names.size}", names.size == 72)
        // the database title differs from the filename-derived catalog name
        assertEquals("JAKOBACHEM POTR", names["jakob"])
        assertEquals("UTPOTI", names["Utpoti"])
    }

    @Test
    fun titles_are_cached_and_repeat_calls_are_stable() = runBlocking {
        val first = repository.loadAllDisplayNames()
        val second = repository.loadAllDisplayNames()
        assertEquals(first, second)
    }
}
