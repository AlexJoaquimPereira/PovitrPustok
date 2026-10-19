package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleDatabase
import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BOOKS-01: the Books screen must not rescan all 73 databases on every
 * visit just because 2 Kings has no title block, and concurrent scans must
 * open each book once.
 */
@RunWith(RobolectricTestRunner::class)
class BookTitleRepositoryCacheTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context
    private lateinit var delegate: AssetBibleDatabaseProvider
    private lateinit var counting: CountingProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        delegate = AssetBibleDatabaseProvider(context)
        counting = CountingProvider(delegate)
    }

    @After
    fun tearDown() {
        delegate.close()
    }

    @Test
    fun repeated_scans_do_not_reopen_the_corpus() = runBlocking {
        val repository = BookTitleRepositoryImpl(counting)

        val first = repository.loadAllDisplayNames()
        assertEquals("one scan opens every book exactly once", 73, counting.opens)
        assertEquals("2 Kings has no title block", 72, first.size)

        val opensAfterFirst = counting.opens
        val second = repository.loadAllDisplayNames()
        val third = repository.loadAllDisplayNames()

        assertEquals("repeat scans must not reopen any database", opensAfterFirst, counting.opens)
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun absent_title_is_cached_distinctly_from_not_loaded() = runBlocking {
        val repository = BookTitleRepositoryImpl(counting)

        assertNull(repository.getDisplayName("Razanchem_Dusrem_Pustok"))
        assertEquals("the first lookup opens the book", 1, counting.opens)

        assertNull(repository.getDisplayName("Razanchem_Dusrem_Pustok"))
        assertEquals("a cached absent title must not reopen the book", 1, counting.opens)
    }

    @Test
    fun concurrent_scans_open_each_book_once() = runBlocking {
        val repository = BookTitleRepositoryImpl(counting)

        coroutineScope {
            (1..4).map {
                async(Dispatchers.IO) { repository.loadAllDisplayNames() }
            }.awaitAll()
        }

        assertEquals("concurrent scans must be serialized", 73, counting.opens)
    }

    private class CountingProvider(
        private val delegate: BibleDatabaseProvider
    ) : BibleDatabaseProvider {
        var opens = 0

        override suspend fun openBook(bookId: String): BibleDatabase {
            opens++
            return delegate.openBook(bookId)
        }

        override fun close() = delegate.close()
    }
}
