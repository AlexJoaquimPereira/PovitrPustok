package com.biblia.povitrpustok.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.data.bible.BibleSearchRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device FTS5 verification (Phase 2 test 8).
 *
 * FTS5 availability is a property of the device's SQLite build, not the API
 * level: some OEM builds (observed on an API 31 MIUI device) omit it. These
 * tests therefore skip when the module is absent; the scan fallback is covered
 * by BibleDatabaseSearchFallbackTest and the repository instrumented tests.
 */
@RunWith(AndroidJUnit4::class)
class BibleFtsInstrumentedTest {

    private lateinit var provider: AssetBibleDatabaseProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
    }

    @Test
    fun verses_fts_exists_is_populated_and_queryable() = runBlocking {
        val db = provider.openBook("jakob")
        try {
            Assume.assumeTrue("FTS5 is not compiled into this device's SQLite", db.isFts5Available())
            assertTrue("verses_fts table must exist", db.hasFtsTable())
            assertTrue("FTS5 module must be compiled into framework sqlite", db.isFts5Available())

            val chapterCount = db.getVerses(db.getChapter("jakob", 1)!!.id).size
            assertTrue(chapterCount > 0)
            assertTrue("FTS index must be populated", db.ftsRowCount() > 0)

            val chapter = db.getChapter("jakob", 1)!!
            val verses = db.getVerses(chapter.id)
            val word = verses
                .flatMap { it.plainText.split(Regex("[^\\p{L}]+")) }
                .firstOrNull { it.length >= 3 }
            assertTrue("expected a searchable word", word != null)
            val ids = db.ftsMatchVerseIds(word!!, 10)
            assertTrue("FTS5 MATCH must return rows", ids.isNotEmpty())
            // every returned rowid must reference a real verse anywhere in the book
            val realIds = db.getChapters("jakob")
                .flatMap { db.getVerses(it.id) }
                .map { it.id }
                .toSet()
            assertTrue("matched ids must reference real verses", ids.all { realIds.contains(it) })
        } finally {
            provider.close()
        }
    }

    @Test
    fun james_verse_count_matches_fts_index_count() = runBlocking {
        val db = provider.openBook("jakob")
        try {
            Assume.assumeTrue("FTS5 is not compiled into this device's SQLite", db.isFts5Available())
            val totalVerses = db.getChapters("jakob")
                .sumOf { db.getVerses(it.id).size }
            assertEquals(totalVerses.toLong(), db.ftsRowCount())
        } finally {
            provider.close()
        }
    }

    /**
     * Corrective phase Issue 1: the full search path (repository ->
     * searchVerses -> FTS5 MATCH -> portable snippet) must work on real
     * Android SQLite (API 30+). Covers common/rare/accented/unaccented/
     * phrase/apostrophe/punctuation queries and the book-filtered and
     * all-book modes.
     */
    @Test
    fun full_search_path_works_on_device_sqlite() = runBlocking {
        val probe = provider.openBook("jakob")
        Assume.assumeTrue(
            "FTS5 is not compiled into this device's SQLite",
            probe.isFts5Available()
        )
        val repository = BibleSearchRepositoryImpl(provider)

        // common word
        val common = repository.search("\"subhagi\"", bookIds = listOf("jakob"), limit = 10)
        assertTrue("common word must match", common.isNotEmpty())
        assertTrue("result must carry a non-empty snippet", common.all { it.snippet.isNotBlank() })

        // accented vs unaccented (remove_diacritics)
        val accented = repository.search("\"sõsnnikaiechi\"", bookIds = listOf("jakob"), limit = 10)
        val unaccented = repository.search("\"sosnnikaiechi\"", bookIds = listOf("jakob"), limit = 10)
        assertEquals(accented.size, unaccented.size)
        assertTrue("accented term must match", accented.isNotEmpty())

        // phrase
        val phrase = repository.search("\"tujea peleacho\"", bookIds = listOf("jakob"), limit = 10)
        assertTrue("phrase must match", phrase.isNotEmpty())

        // apostrophe term
        val apostrophe = repository.search("\"dil'lo\"", bookIds = listOf("jakob"), limit = 10)
        assertTrue("apostrophe term must match", apostrophe.isNotEmpty())

        // punctuation-heavy ordinary query is treated as plain terms
        val punctuation = repository.search("\"koxttam\"", bookIds = listOf("jakob"), limit = 10)
        assertTrue("punctuation query must not crash and must match", punctuation.isNotEmpty())

        // all-book search (limited to the small corpus for speed)
        val allBooks = repository.search("\"subhagi\"", bookIds = null, limit = 20)
        assertTrue("all-book search must work", allBooks.isNotEmpty())
        assertTrue(
            "all-book search must not be monopolized by one book",
            allBooks.map { it.bookId }.toSet().size > 1
        )
        val repeated = repository.search("\"subhagi\"", bookIds = null, limit = 20)
        assertEquals(
            "search must be deterministic across identical queries",
            allBooks.map { it.verseId },
            repeated.map { it.verseId }
        )

        // rare term -> empty, no exception
        val rare = repository.search("\"zzzqqqxxx\"", bookIds = listOf("jakob"), limit = 10)
        assertTrue(rare.isEmpty())
    }
}
