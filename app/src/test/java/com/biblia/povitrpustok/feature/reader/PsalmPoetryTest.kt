package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Psalm poetic-mode decision (corrective phase Issue 8): Psalms verses
 * whose own HTML has no explicit alignment are centered as poetic blocks;
 * the mode never applies outside Psalms and never overrides an explicit
 * alignment.
 */
class PsalmPoetryTest {

    private val parser = BibleHtmlParser()

    private fun doc(html: String): BibleHtmlDocument = parser.parse(html)

    @Test
    fun unaligned_psalm_verse_is_poetic() {
        val html = "Zo monis noxtteanchi<br/>budh gheun cholona,<br/>vô patkianchea"
        assertTrue(PsalmPoetry.isPoeticVerse(PsalmPoetry.PSALMS_BOOK_ID, doc(html)))
    }

    @Test
    fun psalm_verse_with_own_center_alignment_keeps_it() {
        val html = "<p align=center >\nZo monis noxtteanchi<br/>budh gheun cholona,</p>"
        assertFalse("explicit alignment must not be overridden", PsalmPoetry.isPoeticVerse(PsalmPoetry.PSALMS_BOOK_ID, doc(html)))
    }

    @Test
    fun poetic_mode_is_limited_to_psalms() {
        val html = "Zo monis noxtteanchi<br/>budh gheun cholona,"
        assertFalse("other books are never centered", PsalmPoetry.isPoeticVerse("jakob", doc(html)))
        assertFalse("unknown book is never centered", PsalmPoetry.isPoeticVerse(null, doc(html)))
    }

    @Test
    fun heading_alignment_inside_a_verse_counts_as_explicit() {
        // e.g. a psalm with an internal centered heading
        val html = "<h3 align=center>Sela</h3><br/>Zo monis noxtteanchi"
        assertFalse(PsalmPoetry.isPoeticVerse(PsalmPoetry.PSALMS_BOOK_ID, doc(html)))
    }

    @Test
    fun br_line_structure_is_not_flattened_by_the_renderer() {
        val html = "line one<br/>line two<br/>line three"
        val rendered = com.biblia.povitrpustok.core.html.BibleHtmlRenderer.render(doc(html)).text
        assertTrue(rendered.contains("line one\nline two\nline three"))
    }
}
