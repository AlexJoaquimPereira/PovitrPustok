package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.feature.reader.ReaderContentBuilder
import com.biblia.povitrpustok.feature.reader.ReaderContentItem
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DB-03 regression: `verses.position` is the source/XML order. The reader
 * previously ordered by `verse_start, id`, which reorders 77 chapters where
 * the source order is not monotonic in `verse_start` (Job 33, Esther 3,
 * Daniel 6's preface, etc.). The repository must return exactly the source
 * `position` order.
 */
@RunWith(RobolectricTestRunner::class)
class BibleVerseOrderTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

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

    @Test
    fun job_33_repository_order_matches_source_position() = runBlocking {
        assertChapterFollowsSourcePosition("Jobachem_Pustok", number = 33)
    }

    @Test
    fun esther_3_duplicate_verse_starts_follow_source_position() = runBlocking {
        assertChapterFollowsSourcePosition("Esther", number = 3)
    }

    @Test
    fun daniel_6_preface_stays_first_in_source_order() = runBlocking {
        val chapter = chapterByNumber("Daniel", 6)
        val content = repository.getChapter("Daniel", chapter.position)
        assertTrue("Daniel 6 must contain verses", content.verses.isNotEmpty())

        // The source's first record is the chapter preface, whose source
        // verse_start is 31; start-ordering used to move verse 2 to the front.
        assertEquals(
            "the source preface must remain first",
            31,
            content.verses.first().verseStart
        )
        assertEquals(
            rawIds("Daniel", chapter.id, "position, id"),
            content.verses.map { it.id }
        )
    }

    @Test
    fun reader_builder_consumes_source_ordered_verses_and_keeps_headings() = runBlocking {
        val chapter = chapterByNumber("Razanchem_Poilem_Pustok", 5)
        val content = repository.getChapter("Razanchem_Poilem_Pustok", chapter.position)
        val items = ReaderContentBuilder.build(
            chapter = chapter,
            headings = content.headings,
            verses = content.verses
        )
        val builderVerseIds = items.filterIsInstance<ReaderContentItem.Verse>().map { it.verse.id }

        // Discriminating: the builder must see source order, not start order.
        assertEquals(rawIds("Razanchem_Poilem_Pustok", chapter.id, "position, id"), builderVerseIds)
        assertNotEquals(rawIds("Razanchem_Poilem_Pustok", chapter.id, "verse_start, id"), builderVerseIds)
        assertEquals(
            "no heading may be dropped by the reordered input",
            content.headings.map { it.id }.toSet(),
            items.filterIsInstance<ReaderContentItem.Heading>().map { it.heading.id }.toSet()
        )
    }

    @Test
    fun normal_chapter_order_is_unchanged() = runBlocking {
        val chapter = chapterByNumber("jakob", 1)
        val content = repository.getChapter("jakob", chapter.position)
        val positionOrder = rawIds("jakob", chapter.id, "position, id")
        val startOrder = rawIds("jakob", chapter.id, "verse_start, id")

        // A well-formed chapter has identical source and numeric order.
        assertEquals(startOrder, positionOrder)
        assertEquals(positionOrder, content.verses.map { it.id })
    }

    /**
     * Asserts the repository matches the raw `position` order and proves the
     * fixture actually distinguishes it from `verse_start` ordering.
     */
    private suspend fun assertChapterFollowsSourcePosition(bookId: String, number: Int) {
        val chapter = chapterByNumber(bookId, number)
        val content = repository.getChapter(bookId, chapter.position)
        assertTrue("$bookId $number must contain verses", content.verses.isNotEmpty())

        val positionOrder = rawIds(bookId, chapter.id, "position, id")
        val startOrder = rawIds(bookId, chapter.id, "verse_start, id")
        val insertionOrder = rawIds(bookId, chapter.id, "id")
        assertNotEquals(
            "$bookId $number must be a fixture where source and start order differ",
            positionOrder,
            startOrder
        )
        // Independent of the production ORDER BY: position must agree with the
        // rows' insertion (source document) order, so this is not tautological.
        assertEquals(
            "$bookId $number position order must match source insertion order",
            insertionOrder,
            positionOrder
        )
        assertEquals(
            "$bookId $number must follow source position order",
            positionOrder,
            content.verses.map { it.id }
        )
    }

    private suspend fun chapterByNumber(bookId: String, number: Int): Chapter =
        repository.getChapters(bookId).first { it.number == number }

    /** Reads the prepared copy independently so the assertion is not self-confirming. */
    private fun rawIds(bookId: String, chapterId: Long, orderBy: String): List<Long> {
        val file = File(context.filesDir, "bible/$bookId.sqlite")
        val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return raw.use { database ->
            database.rawQuery(
                "SELECT id FROM verses WHERE chapter_id = ? ORDER BY $orderBy",
                arrayOf(chapterId.toString())
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0))
                }
            }
        }
    }
}
