package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.feature.reader.ReaderContentBuilder
import com.biblia.povitrpustok.feature.reader.ReaderContentItem
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
 * John chapter 1 — real-corpus diagnostic (corrective phase Issue 5).
 *
 * Reproduces the reader path against the actual `juanv.sqlite`: the
 * chapter loads completely, headings interleave by before_verse, and
 * NORMAL chapter mode applies no passage filtering. The observed absence
 * of verse_start 2..17 is asserted as a DATABASE fact (the corpus simply
 * contains no such records for chapter 1) — the application renders the
 * database's complete content faithfully.
 */
@RunWith(RobolectricTestRunner::class)
class JohnChapterOneDataTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var provider: AssetBibleDatabaseProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun john_chapter_one_loads_completely_in_normal_mode() = runBlocking {
        val db = provider.openBook("juanv")
        val chapter = db.getChapter("juanv", 1)
        assertTrue("John must have chapter position 1", chapter != null)
        assertEquals(1, chapter!!.number)

        val verses = db.getVerses(chapter.id)
        val headings = db.getHeadings(chapter.id)

        // The repaired corpus keeps the source's verse-1 prefix (normal
        // left-aligned opening followed by the centred italic continuation)
        // and has 51 records, 1..51.
        assertTrue("chapter content must be non-empty", verses.isNotEmpty())
        assertEquals("first verse is 1", 1, verses.first().verseStart)
        assertEquals("last verse is 51", 51, verses.last().verseStart)
        assertEquals("chapter 1 has 51 verse records in the corpus", 51, verses.size)
        val verseOne = verses.first().html
        assertTrue(
            "verse 1 must keep the normal left-aligned opening",
            verseOne.contains("Orombak sogllê rochnnê adim")
        )
        assertTrue(
            "verse 1 must keep the centred italic continuation",
            verseOne.contains("Sôbd aslo") && verseOne.contains("align=center")
        )
        assertTrue("headings are present", headings.isNotEmpty())
        assertEquals(5, headings.size)

        // normal mode: no passage -> the full chapter renders, unfiltered
        val items = ReaderContentBuilder.build(
            chapter = chapter,
            headings = headings,
            verses = verses,
            passage = null
        )
        assertEquals("normal mode must render every verse", 51, items.count { it is ReaderContentItem.Verse })
        assertEquals("all headings render", 5, items.count { it is ReaderContentItem.Heading })
        assertTrue("header present", items.any { it is ReaderContentItem.Header })
        // Section headings are anchored to their source-adjacent verses.
        val headingOrder = items.filterIsInstance<ReaderContentItem.Heading>().map { it.heading.beforeVerse }
        assertEquals(listOf(1, 19, 29, 35, 43), headingOrder)
    }

    @Test
    fun john_chapter_one_starts_at_verse_one_without_losing_the_prefix() = runBlocking {
        val db = provider.openBook("juanv")
        val chapter = db.getChapter("juanv", 1)!!
        val verses = db.getVerses(chapter.id)
        val starts = verses.map { it.verseStart }.toSet()

        // The source opens inside <_1> without a literal "1" marker; the
        // importer must preserve that prefix as verse 1, then continue
        // contiguously through 51.
        assertTrue("verse 1 must exist after the repair", 1 in starts)
        assertEquals("John 1 starts at verse 1", 1, verses.first().verseStart)
        assertTrue("verses 1..51 are contiguous", (1..51).all { it in starts })

        // a normal chapter (John 2) also starts at 1, proving chapter 1's
        // offset is source data rather than a reader bug
        val chapter2 = db.getChapter("juanv", 2)!!
        val starts2 = db.getVerses(chapter2.id).map { it.verseStart }.toSet()
        assertTrue("John 2 starts at verse 1", starts2.contains(1))
        assertTrue("John 2 contains verse 2", starts2.contains(2))
        assertTrue("John 2 contains verse 5", starts2.contains(5))
    }
}
