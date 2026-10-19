package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleDatabase
import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleVerseHit
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Testament
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    /**
     * DB-05: the corpus loop must stop at the next book after its caller is
     * cancelled. Deterministic (no FTS/SQLite, no timing): without the
     * ensureActive checkpoint the loop would process all 73 books because the
     * fake block never suspends.
     */
    @Test
    fun cancellation_stops_iterating_books_before_the_next_book() = runBlocking {
        val repository = BibleSearchRepositoryImpl(
            object : BibleDatabaseProvider {
                override suspend fun openBook(bookId: String): BibleDatabase =
                    error("the cancellation test must not open a database")

                override fun close() = Unit
            }
        )

        var processed = 0
        lateinit var job: Job
        job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            repository.forEachBook(BibleCatalog.books) { book ->
                processed++
                if (processed == 2) job.cancel()
                book.id
            }
        }
        job.start()
        job.join()

        assertTrue("the cancelled search must terminate as cancelled", job.isCancelled)
        assertEquals(
            "no further book may be processed after cancellation",
            2,
            processed
        )
    }

    /** SEARCH-01: fair rank interleaving and the global limit. */
    @Test
    fun merge_interleaves_books_by_rank_and_applies_the_global_limit() {
        val a = book("a")
        val b = book("b")
        val c = book("c")
        val perBook = listOf(
            a to listOf(hit(1), hit(2), hit(3)),
            b to listOf(hit(4)),
            c to listOf(hit(5), hit(6))
        )

        assertEquals(
            "rank 1 from each book first, then rank 2, in canonical order",
            listOf("a:1", "b:4", "c:5", "a:2", "c:6"),
            mergeByRank(perBook, limit = 5).map { (book, hit) -> "${book.id}:${hit.verseId}" }
        )
        assertEquals("the global limit applies after merging", 3, mergeByRank(perBook, limit = 3).size)
        assertTrue("no books means no results", mergeByRank(emptyList(), limit = 10).isEmpty())
    }

    private fun book(id: String) = BibleBook(
        id = id,
        displayName = id,
        englishName = id,
        testament = Testament.OLD_TESTAMENT,
        position = 1,
        databaseAsset = "bible/$id.sqlite"
    )

    private fun hit(id: Long) = BibleVerseHit(
        verseId = id,
        chapterId = 1L,
        chapterPosition = 1,
        chapterNumber = 1,
        verseStart = id.toInt(),
        verseEnd = id.toInt(),
        plainText = "verse $id"
    )
}
