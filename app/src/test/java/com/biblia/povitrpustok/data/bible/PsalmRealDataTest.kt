package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.feature.reader.PsalmPoetry
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
 * Real-corpus Psalm diagnostics (corrective phase Issues 8 & 9):
 * poetic alignment is preserved across every Psalm verse, <br> line
 * structure is intact, and the verse numbering offset (starts at 2) is a
 * DATABASE fact — the application renders verse.verseStart verbatim.
 */
@RunWith(RobolectricTestRunner::class)
class PsalmRealDataTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var provider: AssetBibleDatabaseProvider
    private val parser = BibleHtmlParser()

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
    fun psalm_poetic_alignment_applies_across_all_verses() = runBlocking {
        val db = provider.openBook(PsalmPoetry.PSALMS_BOOK_ID)
        for (position in listOf(1, 23, 112, 150)) {
            val chapter = db.getChapter(PsalmPoetry.PSALMS_BOOK_ID, position)
            assertTrue("psalm at position $position must exist", chapter != null)
            val verses = db.getVerses(chapter!!.id)
            assertTrue("psalm $position must have multiple verses", verses.size >= 2)

            for (verse in verses) {
                val document = parser.parse(verse.html)
                // every verse's line breaks survive rendering (no flattening)
                assertTrue(
                    "verse ${verse.verseStart} must contain at least one <br/> in the corpus",
                    verse.html.contains("<br/>")
                )
                if (verse.verseStart > verses.first().verseStart) {
                    if (verse.html.contains("align=")) {
                        // Verses that carry their own alignment keep it; poetic
                        // mode must not override explicit source alignment.
                        assertFalse(
                            "psalm $position verse ${verse.verseStart} has explicit alignment and must not be re-centered",
                            PsalmPoetry.isPoeticVerse(PsalmPoetry.PSALMS_BOOK_ID, document)
                        )
                    } else {
                        assertTrue(
                            "psalm $position verse ${verse.verseStart} must be poetic-centered",
                            PsalmPoetry.isPoeticVerse(PsalmPoetry.PSALMS_BOOK_ID, document)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun psalm_112_starts_at_verse_1_after_the_prefix_repair() = runBlocking {
        val db = provider.openBook(PsalmPoetry.PSALMS_BOOK_ID)
        val psalm112 = db.getChapter(PsalmPoetry.PSALMS_BOOK_ID, 112)
        val verses = db.getVerses(psalm112!!.id)
        assertEquals(
            "the regenerated corpus keeps the verse-1 prefix as its own record",
            1,
            verses.first().verseStart
        )
        // DB-vs-display assertion: the Reader renders the database value.
        val headings = db.getHeadings(psalm112.id)
        val items = ReaderContentBuilder.build(psalm112, headings, verses)
        val displayedFirst = items.filterIsInstance<ReaderContentItem.Verse>().first()
        assertEquals(
            "the reader must display the database verse_start verbatim",
            1,
            displayedFirst.verse.verseStart
        )
    }

    @Test
    fun only_psalm_92_lacks_a_verse_1_record() = runBlocking {
        val db = provider.openBook(PsalmPoetry.PSALMS_BOOK_ID)
        val chapters = db.getChapters(PsalmPoetry.PSALMS_BOOK_ID)
        val missingVerse1 = chapters.filter { chapter ->
            db.getVerses(chapter.id).none { it.verseStart == 1 }
        }
        assertEquals(
            "after the prefix repair only Psalm 92 starts at verse 2 in the source",
            1,
            missingVerse1.size
        )
        assertEquals(92, missingVerse1.single().number)
    }

    @Test
    fun psalm_1_first_verse_has_the_centered_paragraph() = runBlocking {
        val db = provider.openBook(PsalmPoetry.PSALMS_BOOK_ID)
        val psalm1 = db.getChapter(PsalmPoetry.PSALMS_BOOK_ID, 1)
        val verses = db.getVerses(psalm1!!.id)
        val first = verses.first()
        assertTrue(
            "Psalm 1's first verse carries the centered paragraph in the corpus",
            first.html.contains("<p align=center >")
        )
        // and the renderer emits an explicit centered paragraph for it
        val rendered = com.biblia.povitrpustok.core.html.BibleHtmlRenderer.render(parser.parse(first.html))
        assertTrue(
            rendered.paragraphStyles.any { it.item.textAlign == androidx.compose.ui.text.style.TextAlign.Center }
        )
    }
}
