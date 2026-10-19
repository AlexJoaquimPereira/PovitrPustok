package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.feature.reader.ReaderContentBuilder
import com.biblia.povitrpustok.feature.reader.ReaderContentItem
import kotlinx.coroutines.runBlocking
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

        // the complete database content for chapter 1
        assertTrue("chapter content must be non-empty", verses.isNotEmpty())
        assertEquals("first verse is 1", 1, verses.first().verseStart)
        assertEquals("last verse is 51", 51, verses.last().verseStart)
        assertEquals("chapter 1 has 35 verse records in the corpus", 35, verses.size)
        assertTrue("headings are present", headings.isNotEmpty())
        assertEquals(5, headings.size)

        // normal mode: no passage -> the full chapter renders, unfiltered
        val items = ReaderContentBuilder.build(
            chapter = chapter,
            headings = headings,
            verses = verses,
            passage = null
        )
        assertEquals("normal mode must render every verse", 35, items.count { it is ReaderContentItem.Verse })
        assertEquals("all headings render", 5, items.count { it is ReaderContentItem.Heading })
        assertTrue("header present", items.any { it is ReaderContentItem.Header })
        // headings interleaved by before_verse (1, 19, 29, 35, 43)
        val headingOrder = items.filterIsInstance<ReaderContentItem.Heading>().map { it.heading.beforeVerse }
        assertEquals(listOf(1, 19, 29, 35, 43), headingOrder)
    }

    @Test
    fun john_chapter_one_gap_is_a_database_fact() = runBlocking {
        val db = provider.openBook("juanv")
        val chapter = db.getChapter("juanv", 1)!!
        val verses = db.getVerses(chapter.id)
        val starts = verses.map { it.verseStart }.toSet()

        // verses 2..17 do NOT exist in the corpus for chapter 1
        // (they exist for chapters 2-21). This documents the DATABASE
        // state; the app must not invent content (Issue 5, no faking).
        assertEquals("verse 1 exists", true, 1 in starts)
        assertTrue("no verse_start 2..17 in the DB for John 1", (2..17).none { it in starts })
        assertEquals("verse 18 exists", true, 18 in starts)

        // a normal chapter (John 2) has its expected first verses, proving
        // the gap is specific to chapter 1's source data, not the reader
        val chapter2 = db.getChapter("juanv", 2)!!
        val starts2 = db.getVerses(chapter2.id).map { it.verseStart }.toSet()
        assertTrue("John 2 starts at verse 1", starts2.contains(1))
        assertTrue("John 2 contains verse 2", starts2.contains(2))
        assertTrue("John 2 contains verse 5", starts2.contains(5))
    }
}
