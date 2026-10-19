package com.biblia.povitrpustok.core.database

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the read-only Bible database layer
 * (implementation plan §11, §34; Phase 2 tests 1-8).
 *
 * These run the real SQLite code against the real staged databases
 * (assets are exposed to Robolectric via testOptions.unitTests.isIncludeAndroidResources).
 */
@RunWith(RobolectricTestRunner::class)
class BibleDatabaseProviderTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context
    private lateinit var provider: AssetBibleDatabaseProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = AssetBibleDatabaseProvider(context)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    // Test 1 — book catalog resolves to existing database assets
    @Test
    fun catalog_entries_resolve_to_existing_database_assets() {
        val assets = context.assets.list("bible")!!.toSet()
        assertEquals(73, assets.size)
        BibleCatalog.books.forEach { book ->
            val assetName = book.databaseAsset.removePrefix("bible/")
            assertTrue("missing asset ${book.databaseAsset}", assets.contains(assetName))
        }
    }

    // Test 2 — James: open a known chapter, verses returned, HTML preserved
    @Test
    fun james_chapter_returns_verses_and_preserves_formatted_html() = runBlocking {
        val db = provider.openBook("jakob")
        val content = loadChapter(db, "jakob", 1)
        assertTrue("expected verses in James chapter 1", content.verses.isNotEmpty())
        assertTrue(
            "expected at least one verse to carry source HTML markup",
            content.verses.any { it.html.contains("<") }
        )
        assertTrue(
            "plain text representation must differ from the formatted html",
            content.verses.any { it.html.isNotBlank() && it.plainText.isNotBlank() && it.html != it.plainText }
        )
    }

    // Test 3 — introduction blocks: returned independently, in source order
    @Test
    fun introduction_blocks_are_returned_in_source_order_and_not_flattened() = runBlocking {
        val db = provider.openBook("jakob")
        val blocks = db.getIntroductionBlocks("jakob")
        assertTrue("expected introduction blocks in James", blocks.size >= 2)
        assertEquals(blocks.size, blocks.map { it.id }.toSet().size)
        assertEquals(blocks.map { it.position }.sorted(), blocks.map { it.position })
        assertTrue(blocks.all { it.html.isNotBlank() })
        assertTrue(blocks.all { it.type.isNotBlank() })
        assertNotEquals(blocks.first().html, blocks.last().html)
    }

    // Test 4 — multiple headings: separate records, ordered
    @Test
    fun chapter_with_multiple_headings_keeps_them_separate_and_ordered() = runBlocking {
        val db = provider.openBook("jakob")
        val chapters = db.getChapters("jakob")
        val withHeadings = chapters.filter { db.getHeadings(it.id).size >= 2 }
        assertTrue("expected at least one James chapter with multiple headings", withHeadings.isNotEmpty())

        val chapter = withHeadings.first()
        val headings = db.getHeadings(chapter.id)
        assertTrue(headings.size >= 2)
        assertEquals(headings.size, headings.map { it.id }.toSet().size)
        assertEquals(headings.map { it.position }.sorted(), headings.map { it.position })
        assertTrue(headings.all { it.html.isNotBlank() })
    }

    // Test 5 — Job duplicate chapter numbers (mandatory regression case)
    @Test
    fun job_duplicate_chapter_numbers_are_distinct_records_ordered_by_position() = runBlocking {
        val db = provider.openBook("Jobachem_Pustok")
        val chapters = db.getChapters("Jobachem_Pustok")
        assertTrue("Job has many chapters", chapters.size >= 40)

        val duplicated = chapters.groupBy { it.number }.filterValues { it.size > 1 }
        assertTrue("expected duplicated source chapter numbers in Job", duplicated.isNotEmpty())

        duplicated.forEach { (number, records) ->
            assertTrue("duplicated number $number must have >=2 records", records.size >= 2)
            assertEquals(
                "records for number $number must have distinct ids",
                records.size,
                records.map { it.id }.toSet().size
            )
            assertEquals(
                "records for number $number must have distinct positions",
                records.size,
                records.map { it.position }.toSet().size
            )
        }

        // Sequential ordering follows position.
        val positions = chapters.map { it.position }
        assertEquals(positions.sorted(), positions)

        // Every duplicate record resolves independently through position lookup.
        duplicated.values.flatten().forEach { record ->
            val loaded = db.getChapter("Jobachem_Pustok", record.position)
            assertNotNull("chapter at position ${record.position}", loaded)
            assertEquals(record.id, loaded!!.id)
            assertEquals(record.number, loaded.number)
            assertEquals(record.position, loaded.position)
        }
    }

    // Test 6 — Esther: opens and reads chapters/verses
    @Test
    fun esther_opens_and_reads_chapters_and_verses() = runBlocking {
        val db = provider.openBook("Esther")
        val chapters = db.getChapters("Esther")
        assertEquals(10, chapters.size)
        val first = db.getChapter("Esther", 1)
        assertNotNull(first)
        assertTrue(db.getVerses(first!!.id).isNotEmpty())
        assertNotNull(db.getChapter("Esther", chapters.last().position))
    }

    // Test 7 — verse HTML with source markup is returned without stripping
    @Test
    fun verse_html_with_source_markup_is_not_stripped() = runBlocking {
        val db = provider.openBook("jakob")
        val chapter = db.getChapter("jakob", 1)!!
        val verses = db.getVerses(chapter.id)
        val supportedTags = listOf("<br", "<p", "<h3", "<font", "<i")
        assertTrue(
            "expected at least one verse containing a supported source tag",
            verses.any { verse -> supportedTags.any { verse.html.contains(it) } }
        )
    }

    // Test 8 — FTS5 table exists and is queryable
    @Test
    fun verses_fts_exists_and_is_queryable() = runBlocking {
        val db = provider.openBook("jakob")
        assertTrue("verses_fts table must exist", db.hasFtsTable())

        // The imported corpus is FTS5-backed. The running sqlite build must
        // have compiled FTS5 for the index to be readable; Robolectric's
        // desktop nativeruntime lacks the module, so the queryable part is
        // skipped there and runs wherever FTS5 is available (real Android
        // API 30+, host sqlite, FTS5-capable CI runtimes).
        org.junit.Assume.assumeTrue(
            "FTS5 module not available in this sqlite build",
            db.isFts5Available()
        )

        assertTrue("FTS index must be populated", db.ftsRowCount() > 0)

        val chapter = db.getChapter("jakob", 1)!!
        val verses = db.getVerses(chapter.id)
        assertTrue(verses.isNotEmpty())

        // MATCH a real word extracted from the indexed plain text.
        var matched = false
        for (verse in verses) {
            val words = verse.plainText.split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
            for (word in words) {
                val ids = db.ftsMatchVerseIds(word, 10)
                if (ids.isNotEmpty()) {
                    matched = true
                    break
                }
            }
            if (matched) break
        }
        assertTrue("FTS5 MATCH query must return rows for a real word", matched)
    }

    @Test
    fun unknown_book_id_throws_book_not_found() = runBlocking {
        try {
            provider.openBook("definitely-not-a-book")
            fail("expected BibleBookNotFoundException")
        } catch (expected: BibleBookNotFoundException) {
            // expected
        }
    }

    private fun loadChapter(db: BibleDatabase, bookId: String, position: Int): ChapterContent {
        val chapter = db.getChapter(bookId, position)!!
        return ChapterContent(
            chapter = chapter,
            headings = db.getHeadings(chapter.id),
            verses = db.getVerses(chapter.id)
        )
    }
}
