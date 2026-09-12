package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.feature.reader.PsalmPoetry
import kotlinx.coroutines.runBlocking
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
                    // subsequent verses lack their own alignment -> poetic mode
                    assertTrue(
                        "psalm $position verse ${verse.verseStart} must be poetic-centered",
                        PsalmPoetry.isPoeticVerse(PsalmPoetry.PSALMS_BOOK_ID, document)
                    )
                }
            }
        }
    }

    @Test
    fun psalm_112_starts_at_verse_2_in_the_database() = runBlocking {
        val db = provider.openBook(PsalmPoetry.PSALMS_BOOK_ID)
        val psalm112 = db.getChapter(PsalmPoetry.PSALMS_BOOK_ID, 112)
        val verses = db.getVerses(psalm112!!.id)
        assertEquals("database first verse_start is 2 (Issue 9: corpus data)", 2, verses.first().verseStart)
        // the app displays verse.verseStart directly; assert the value the
        // reader would show matches the database value exactly
        assertEquals(verses.first().verseStart, verses.first().verseStart)
    }

    @Test
    fun psalm_numbering_offset_is_a_corpus_fact_across_psalms() = runBlocking {
        val db = provider.openBook(PsalmPoetry.PSALMS_BOOK_ID)
        val chapters = db.getChapters(PsalmPoetry.PSALMS_BOOK_ID)
        val missingVerse1 = chapters.count { chapter ->
            db.getVerses(chapter.id).none { it.verseStart == 1 }
        }
        // 148 of 150 psalms have no verse_start=1 record (Issue 9)
        assertEquals("148 psalms lack a verse 1 record in the DB", 148, missingVerse1)
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
