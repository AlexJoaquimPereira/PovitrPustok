package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleDatabase
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DB-01 real-corpus read regression (audit P0).
 *
 * The corpus stores `books.id` (and every `book_id` foreign key) as TEXT.
 * Before the fix, `BibleDatabase` read that id with `Cursor.getLong`, bound
 * "0" in every book-scoped query, and returned zero chapters/verses/
 * introductions for all 73 books. This test exercises the production read
 * path against the packaged assets and fails if any book returns no
 * chapters, if the stored id is not textual, or if the representative
 * chapters are unreadable.
 */
@RunWith(RobolectricTestRunner::class)
class BibleCorpusReadDataTest {

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

    /** DB-01 core regression: every book is readable and its id is textual. */
    @Test
    fun every_book_database_returns_its_chapters_and_stores_a_textual_id() = runBlocking {
        assertEquals("catalog must contain the full 73-book corpus", 73, BibleCatalog.books.size)

        val problems = mutableListOf<String>()
        for (book in BibleCatalog.books) {
            val database = provider.openBook(book.id)

            val idType = storedBookIdType(book.id)
            if (idType != "text") {
                problems += "${book.id}: books.id type is '$idType', expected 'text'"
            }

            val chapters = database.getChapters(book.id)
            if (chapters.isEmpty()) {
                problems += "${book.id}: getChapters returned no rows"
                continue
            }
            if (chapters.map { it.id }.toSet().size != chapters.size) {
                problems += "${book.id}: duplicate chapter ids"
            }
            if (chapters.map { it.position }.sorted() != chapters.map { it.position }) {
                problems += "${book.id}: chapter positions are not ascending"
            }
            if (chapters.any { it.bookId != book.id }) {
                problems += "${book.id}: chapter.bookId was not the requested book id"
            }
        }

        assertTrue(
            "every bundled book must return its chapters:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    /** Representative reads required by DB-01: James, Genesis, Job, Esther, John. */
    @Test
    fun representative_chapters_return_non_empty_verses() = runBlocking {
        val james = chapterByNumber("jakob", 1)
        assertEquals(1, james.number)
        val jamesVerses = provider.openBook("jakob").getVerses(james.id)
        assertTrue("James 1 must contain verses", jamesVerses.isNotEmpty())
        assertTrue("James verses must carry a positive id", jamesVerses.all { it.id > 0 })

        // Single-verse read for a real verse_start present in the corpus.
        val firstStart = jamesVerses.first().verseStart
        val single = provider.openBook("jakob").getVerse(james.id, firstStart)
        assertNotNull("James 1:$firstStart must be readable", single)
        assertEquals(firstStart, single!!.verseStart)

        assertTrue(
            "Genesis 17 must contain verses",
            provider.openBook("Utpoti").getVerses(chapterByNumber("Utpoti", 17).id).isNotEmpty()
        )
        assertTrue(
            "Esther 1 must contain verses",
            provider.openBook("Esther").getVerses(chapterByNumber("Esther", 1).id).isNotEmpty()
        )
        assertTrue(
            "John 1 must contain verses",
            provider.openBook("juanv").getVerses(chapterByNumber("juanv", 1).id).isNotEmpty()
        )
    }

    /**
     * Book-scoped query coverage for [BibleDatabase.getIntroductionBlocks],
     * the third call site fixed by DB-01 (it also bound the numeric "0").
     */
    @Test
    fun introduction_blocks_are_readable_through_the_book_scoped_query() = runBlocking {
        val james = provider.openBook("jakob").getIntroductionBlocks("jakob")
        assertTrue("James must expose its introduction blocks", james.isNotEmpty())
        assertTrue("introduction blocks must carry the requested book id", james.all { it.bookId == "jakob" })
        assertEquals(
            "introduction blocks must stay in source order",
            james.map { it.position }.sorted(),
            james.map { it.position }
        )

        // A second, structurally different book guards against a single-book coincidence.
        val john = provider.openBook("juanv").getIntroductionBlocks("juanv")
        assertTrue("John must expose its introduction blocks", john.isNotEmpty())
        assertTrue("introduction blocks must carry the requested book id", john.all { it.bookId == "juanv" })
    }

    /** Job keeps two distinct chapter-26 records; both resolve by position. */
    @Test
    fun job_duplicate_chapter_numbers_resolve_independently_by_position() = runBlocking {
        val database = provider.openBook("Jobachem_Pustok")
        val chapters = database.getChapters("Jobachem_Pustok")
        assertTrue("Job must have chapters", chapters.isNotEmpty())

        val duplicate26 = chapters.filter { it.number == 26 }
        assertTrue("Job must contain two chapter-26 records", duplicate26.size >= 2)
        assertEquals(
            "the chapter-26 records must have distinct ids",
            duplicate26.size,
            duplicate26.map { it.id }.toSet().size
        )

        duplicate26.forEach { record ->
            val loaded = database.getChapter("Jobachem_Pustok", record.position)
            assertNotNull("chapter at position ${record.position}", loaded)
            assertEquals(record.id, loaded!!.id)
            assertEquals(record.number, loaded.number)
            assertEquals(record.position, loaded.position)
            assertTrue(
                "Job chapter number 26 at position ${record.position} must contain verses",
                database.getVerses(record.id).isNotEmpty()
            )
        }
    }

    private suspend fun chapterByNumber(bookId: String, number: Int): Chapter =
        provider.openBook(bookId).getChapters(bookId).first { it.number == number }

    /** Opens the prepared copy independently to inspect the raw stored schema. */
    private fun storedBookIdType(bookId: String): String? {
        val file = File(context.filesDir, "bible/$bookId.sqlite")
        val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return raw.use { database ->
            database.rawQuery("SELECT typeof(id) FROM books LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }
}
