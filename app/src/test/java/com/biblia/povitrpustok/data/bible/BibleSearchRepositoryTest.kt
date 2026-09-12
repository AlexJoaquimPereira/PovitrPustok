package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleDatabase
import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JVM repository tests (Phase 5 §21). Robolectric's desktop SQLite cannot
 * execute FTS5 MATCH (documented limitation), so the full search matrix
 * lives in BibleSearchRepositoryInstrumentedTest; here we verify the
 * no-database-work paths and the controlled error mapping.
 */
@RunWith(RobolectricTestRunner::class)
class BibleSearchRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private class CountingProvider(
        private val delegate: BibleDatabaseProvider
    ) : BibleDatabaseProvider {
        val openedBooks = mutableListOf<String>()

        override suspend fun openBook(bookId: String): BibleDatabase {
            openedBooks += bookId
            return delegate.openBook(bookId)
        }

        override fun close() = delegate.close()
    }

    @Test
    fun empty_query_returns_empty_without_opening_any_database() = runBlocking {
        val counting = CountingProvider(AssetBibleDatabaseProvider(context))
        val repository = BibleSearchRepositoryImpl(counting)

        assertTrue(repository.search("").isEmpty())
        assertTrue(repository.search("   ").isEmpty())
        assertTrue("empty queries must never open a database", counting.openedBooks.isEmpty())
    }

    @Test
    fun fts_failures_surface_as_controlled_data_errors() = runBlocking {
        val provider = AssetBibleDatabaseProvider(context)
        try {
            val repository = BibleSearchRepositoryImpl(provider)
            try {
                repository.search("\"subhagi\"", bookIds = listOf("jakob"), limit = 10)
                // If the runtime SQLite supports FTS5 (e.g. a future Robolectric
                // build), results are simply returned.
                assertTrue(true)
            } catch (e: BibleDataException) {
                assertTrue(
                    "expected a controlled BibleDataException, got $e",
                    e is BibleDatabaseAccessException
                )
            }
        } finally {
            provider.close()
        }
    }
}
