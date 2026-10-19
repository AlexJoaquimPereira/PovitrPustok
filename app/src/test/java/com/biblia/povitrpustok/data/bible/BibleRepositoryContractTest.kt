package com.biblia.povitrpustok.data.bible

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Testament
import com.biblia.povitrpustok.domain.repository.BibleRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository contract tests against the real corpus (Phase 3 Part B).
 *
 * "Unchanged" assertions read the same rows through an independent raw
 * SQLiteDatabase connection on the copied file, so they cannot be
 * self-confirming.
 */
@RunWith(RobolectricTestRunner::class)
class BibleRepositoryContractTest {

    private lateinit var context: Context
    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var repository: BibleRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = AssetBibleDatabaseProvider(context)
        repository = BibleRepositoryImpl(provider)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    // 1. BibleRepository returns canonical verses.text unchanged
    @Test
    fun verses_text_is_returned_unchanged() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        assertTrue(content.verses.isNotEmpty())
        content.verses.forEach { verse ->
            assertEquals(
                "verse ${verse.id} text must equal the stored value",
                rawColumn("jakob", "verses", "text", verse.id),
                verse.html
            )
        }
    }

    // 2. text_plain is never used for display: display source is the html column
    @Test
    fun text_plain_is_kept_separate_from_display_html() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        content.verses.forEach { verse ->
            assertEquals("plainText must come from text_plain", rawColumn("jakob", "verses", "text_plain", verse.id), verse.plainText)
            assertTrue("html must remain the canonical formatted source", verse.html.isNotBlank())
        }
        // for at least one verse the two representations differ (formatted vs plain)
        assertTrue(content.verses.any { it.html != it.plainText })
    }

    // 3. headings retain their HTML unchanged
    @Test
    fun headings_retain_html_unchanged() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        assertTrue(content.headings.isNotEmpty())
        content.headings.forEach { heading ->
            assertEquals(rawColumn("jakob", "headings", "html", heading.id), heading.html)
        }
    }

    // 4. introduction blocks retain their HTML unchanged
    @Test
    fun introduction_blocks_retain_html_unchanged() = runBlocking {
        val blocks = repository.getIntroduction("jakob")
        assertTrue(blocks.isNotEmpty())
        blocks.forEach { block ->
            assertEquals(rawColumn("jakob", "introduction_blocks", "html", block.id), block.html)
        }
    }

    // 5. multiple headings remain separate and ordered
    @Test
    fun multiple_headings_remain_separate_and_ordered() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        assertTrue("James ch.1 has multiple headings", content.headings.size >= 2)
        assertEquals(content.headings.size, content.headings.map { it.id }.toSet().size)
        assertEquals(content.headings.map { it.position }.sorted(), content.headings.map { it.position })
    }

    // 6. chapter.position controls source ordering
    @Test
    fun chapter_position_controls_source_ordering() = runBlocking {
        val chapters = repository.getChapters("jakob")
        assertEquals(chapters.map { it.position }.sorted(), chapters.map { it.position })
        assertEquals((1..5).toList(), chapters.map { it.position })
    }

    // 7. duplicate chapter.number values remain distinct records
    @Test
    fun duplicate_chapter_numbers_remain_distinct() = runBlocking {
        val chapters = repository.getChapters("Jobachem_Pustok")
        val duplicated = chapters.groupBy { it.number }.filterValues { it.size > 1 }
        assertTrue("Job must contain duplicate chapter numbers", duplicated.isNotEmpty())
        duplicated.forEach { (number, records) ->
            assertEquals(records.size, records.map { it.id }.toSet().size)
            assertEquals(records.size, records.map { it.position }.toSet().size)
            assertTrue("duplicate group $number must keep equal numbers", records.all { it.number == number })
        }
    }

    // 8. Job's duplicate chapter records remain independently addressable
    @Test
    fun job_duplicates_are_independently_addressable_by_position() = runBlocking {
        val chapters = repository.getChapters("Jobachem_Pustok")
        val duplicated = chapters.groupBy { it.number }.filterValues { it.size > 1 }
        duplicated.values.flatten().forEach { record ->
            val content = repository.getChapter("Jobachem_Pustok", record.position)
            assertEquals(record.id, content.chapter.id)
            assertEquals(record.number, content.chapter.number)
            assertEquals(record.position, content.chapter.position)
            assertTrue(content.verses.isNotEmpty())
        }
    }

    // 9. Esther loads successfully
    @Test
    fun esther_loads_successfully() = runBlocking {
        val chapters = repository.getChapters("Esther")
        assertEquals(10, chapters.size)
        val first = repository.getChapter("Esther", 1)
        assertTrue(first.verses.isNotEmpty())
    }

    // 10. James loads successfully
    @Test
    fun james_loads_successfully() = runBlocking {
        val chapters = repository.getChapters("jakob")
        assertEquals(5, chapters.size)
        val first = repository.getChapter("jakob", 1)
        assertTrue(first.verses.isNotEmpty())
        assertEquals(1, first.chapter.position)
    }

    // 11. verse ranges preserve verse_start / verse_end
    @Test
    fun verse_ranges_preserve_start_and_end() = runBlocking {
        val chapters = repository.getChapters("Utpoti")
        val chapter16 = chapters.first { it.number == 16 }
        val ranged = repository.getChapter("Utpoti", chapter16.position).verses.filter { it.verseStart != it.verseEnd }
        assertTrue("Genesis must contain range verses", ranged.isNotEmpty())
        assertTrue(ranged.all { it.verseEnd > it.verseStart })

        val rangedVerse = repository.getVerse("Utpoti", chapter16.id, ranged.first().verseStart)
        assertNotNull(rangedVerse)
        assertEquals(ranged.first().verseStart, rangedVerse!!.verseStart)
        assertEquals(ranged.first().verseEnd, rangedVerse.verseEnd)
    }

    // 12. all 73 book assets remain resolvable
    @Test
    fun all_73_book_assets_remain_resolvable() {
        val assets = context.assets.list("bible")!!.toSet()
        assertEquals(73, assets.size)
        BibleCatalog.books.forEach { book ->
            assertTrue(assets.contains(book.databaseAsset.removePrefix("bible/")))
        }
    }

    // Known source-data anomalies must not be "fixed" by the Android layer.
    @Test
    fun corpus_metadata_anomalies_are_preserved_verbatim() = runBlocking {
        val tesalonikarank1 = repository.getBook("tesalonikarank1")
        // CATALOG-01: catalog metadata was corrected to match the corpus.
        assertEquals(Testament.NEW_TESTAMENT, tesalonikarank1.testament)
        assertEquals("1 Thessalonians", tesalonikarank1.englishName)

        val korintkarant1 = repository.getBook("korintkarant1")
        assertEquals("Poilem", korintkarant1.displayName)

        // books.position follows importer filename order
        val byPosition = BibleCatalog.books.sortedBy { it.position }.map { it.id }
        val byAsset = BibleCatalog.books.sortedBy { it.databaseAsset }.map { it.id }
        assertEquals(byAsset, byPosition)
    }

    private fun rawColumn(bookId: String, table: String, column: String, id: Long): String? {
        val file = File(context.filesDir, "bible/$bookId.sqlite")
        assertTrue("database file must exist (provider copied the asset)", file.exists())
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.query(table, arrayOf(column), "id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } finally {
            db.close()
        }
    }
}
