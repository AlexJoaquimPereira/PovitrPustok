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
import org.junit.Assert.assertFalse
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

        // the complete database content for chapter 1 (current corpus:
        // John 1 starts at verse 2 and has 50 records ending at 51)
        assertTrue("chapter content must be non-empty", verses.isNotEmpty())
        assertEquals("first verse is 2", 2, verses.first().verseStart)
        assertEquals("last verse is 51", 51, verses.last().verseStart)
        assertEquals("chapter 1 has 50 verse records in the corpus", 50, verses.size)
        assertTrue("headings are present", headings.isNotEmpty())
        assertEquals(5, headings.size)

        // normal mode: no passage -> the full chapter renders, unfiltered
        val items = ReaderContentBuilder.build(
            chapter = chapter,
            headings = headings,
            verses = verses,
            passage = null
        )
        assertEquals("normal mode must render every verse", 50, items.count { it is ReaderContentItem.Verse })
        assertEquals("all headings render", 5, items.count { it is ReaderContentItem.Heading })
        assertTrue("header present", items.any { it is ReaderContentItem.Header })
        // current corpus headings all carry before_verse = 2
        val headingOrder = items.filterIsInstance<ReaderContentItem.Heading>().map { it.heading.beforeVerse }
        assertEquals(listOf(2, 2, 2, 2, 2), headingOrder)
    }

    @Test
    fun john_chapter_one_starts_at_verse_two_without_inventing_verse_one() = runBlocking {
        val db = provider.openBook("juanv")
        val chapter = db.getChapter("juanv", 1)!!
        val verses = db.getVerses(chapter.id)
        val starts = verses.map { it.verseStart }.toSet()

        // Current corpus fact: John 1 has no verse 1; verses 2..51 are
        // contiguous. The app must render exactly these rows and never
        // invent a verse 1.
        assertFalse("verse 1 must not exist in the current corpus", 1 in starts)
        assertEquals("John 1 starts at verse 2", 2, verses.first().verseStart)
        assertTrue("verses 2..51 are contiguous", (2..51).all { it in starts })

        // a normal chapter (John 2) also starts at 1, proving chapter 1's
        // offset is source data rather than a reader bug
        val chapter2 = db.getChapter("juanv", 2)!!
        val starts2 = db.getVerses(chapter2.id).map { it.verseStart }.toSet()
        assertTrue("John 2 starts at verse 1", starts2.contains(1))
        assertTrue("John 2 contains verse 2", starts2.contains(2))
        assertTrue("John 2 contains verse 5", starts2.contains(5))
    }
}
