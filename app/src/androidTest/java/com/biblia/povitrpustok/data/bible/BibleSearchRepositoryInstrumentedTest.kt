package com.biblia.povitrpustok.data.bible

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import com.biblia.povitrpustok.core.result.CorruptBibleDatabaseException
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device full-text search tests against the real corpus (Phase 5 §21).
 * The Android framework SQLite has FTS5 (unlike Robolectric), so MATCH
 * queries run here. Terms were verified against the real databases:
 *  - "subhagi" / "sõsnnikaiechi" in James
 *  - "sorvespor" across 55 books, "dev" across 66 books
 *  - "mhonn" in both Job chapter records with number 26 (positions 26, 27)
 *  - "sunôt" in the Genesis 17:11-12 range verse
 */
@RunWith(AndroidJUnit4::class)
class BibleSearchRepositoryInstrumentedTest {

    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var repository: BibleSearchRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
        repository = BibleSearchRepositoryImpl(provider)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun basic_search_returns_results_for_a_known_word() = runBlocking {
        val results = repository.search("\"subhagi\"")
        assertTrue(results.isNotEmpty())
        assertTrue("expected a James hit", results.any { it.bookId == "jakob" })
        results.forEach { assertTrue(it.snippet.isNotBlank()) }
    }

    @Test
    fun no_results_returns_empty_list() = runBlocking {
        assertTrue(repository.search("\"zzzqqqxxx\"").isEmpty())
    }

    @Test
    fun empty_query_returns_empty_list() = runBlocking {
        assertTrue(repository.search("").isEmpty())
        assertTrue(repository.search("   ").isEmpty())
    }

    @Test
    fun snippets_contain_no_html() = runBlocking {
        val results = repository.search("\"sorvespor\"", limit = 100)
        assertTrue(results.isNotEmpty())
        results.forEach { result ->
            assertFalse("snippet must not contain HTML: ${result.snippet}", result.snippet.contains("<"))
        }
    }

    @Test
    fun common_term_returns_results_from_many_books() = runBlocking {
        val results = repository.search("\"sorvespor\"", limit = 300)
        val books = results.map { it.bookId }.toSet()
        assertTrue("expected hits from many books, got ${books.size}", books.size >= 10)
    }

    @Test
    fun book_filter_restricts_results_to_the_requested_book() = runBlocking {
        val results = repository.search("\"dev\"", bookIds = listOf("jakob"), limit = 100)
        assertTrue(results.isNotEmpty())
        assertTrue("all results must belong to jakob", results.all { it.bookId == "jakob" })
    }

    @Test
    fun job_duplicate_chapters_keep_distinct_records() = runBlocking {
        val results = repository.search("\"mhonn\"", bookIds = listOf("Jobachem_Pustok"), limit = 200)

        val duplicate26 = results.filter { it.chapterNumber == 26 }
        assertTrue(
            "expected hits in both Job duplicate-26 chapters, got ${duplicate26.map { it.chapterPosition }}",
            duplicate26.map { it.chapterPosition }.toSet().containsAll(setOf(26, 27))
        )
        // both records carry number 26 but are distinct chapters
        assertEquals(
            "the two 26 records must not collapse",
            2,
            duplicate26.map { it.chapterId }.toSet().size
        )
    }

    @Test
    fun verse_range_preserves_start_and_end() = runBlocking {
        val results = repository.search("\"sunôt\"", bookIds = listOf("Utpoti"), limit = 100)
        val ranged = results.firstOrNull { it.verseStart != it.verseEnd }
        assertNotNull("expected the Genesis 17:11-12 range hit", ranged)
        assertEquals(11, ranged!!.verseStart)
        assertEquals(12, ranged.verseEnd)
    }

    @Test
    fun plain_hits_coalesce_null_verse_end_to_verse_start() = runBlocking {
        val results = repository.search("\"subhagi\"", bookIds = listOf("jakob"), limit = 50)
        assertTrue(results.isNotEmpty())
        // DB-02: a NULL verse_end must never surface as 0 or a broken range.
        assertTrue(
            "every hit must satisfy 1 <= verseStart <= verseEnd, got " +
                results.map { "${it.chapterNumber}:${it.verseStart}-${it.verseEnd}" },
            results.all { it.verseStart >= 1 && it.verseEnd >= it.verseStart }
        )
        assertTrue("plain hits must coalesce to verseEnd == verseStart", results.any { it.verseEnd == it.verseStart })

        // Prove the NULL branch was actually exercised (not vacuous): at least
        // one returned hit has a stored verse_end that is NULL in the corpus.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "bible/jakob.sqlite")
        val ids = results.map { it.verseId }.joinToString(",")
        val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        val nullEnded = raw.use { database ->
            database.rawQuery(
                "SELECT count(*) FROM verses WHERE id IN ($ids) AND verse_end IS NULL",
                null
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
        assertTrue("expected at least one hit with a stored NULL verse_end", nullEnded > 0)
    }

    @Test
    fun accented_and_unaccented_terms_both_match() = runBlocking {
        val accented = repository.search("\"sõsnnikaiechi\"", bookIds = listOf("jakob"), limit = 10)
        val unaccented = repository.search("\"sosnnikaiechi\"", bookIds = listOf("jakob"), limit = 10)
        assertTrue("accented query must match", accented.isNotEmpty())
        assertTrue("unaccented query must match (remove_diacritics)", unaccented.isNotEmpty())
    }

    @Test
    fun malformed_fts_expression_is_a_controlled_error() = runBlocking {
        try {
            repository.search("\"", bookIds = listOf("jakob"), limit = 10)
            // Tolerated by some SQLite builds -> still controlled.
            assertTrue(true)
        } catch (e: BibleDataException) {
            assertTrue(
                "must be a controlled data exception, got $e",
                e is BibleDatabaseAccessException || e is CorruptBibleDatabaseException
            )
        }
    }

    @Test
    fun results_are_ordered_deterministically() = runBlocking {
        val results = repository.search("\"dev\"", bookIds = listOf("jakob"), limit = 200)
        assertTrue(results.isNotEmpty())
        val pairs = results.map { it.chapterPosition to it.verseStart }
        assertEquals(
            "results must be ordered by chapter.position then verseStart",
            pairs.sortedWith(compareBy({ it.first }, { it.second })),
            pairs
        )
    }
}
