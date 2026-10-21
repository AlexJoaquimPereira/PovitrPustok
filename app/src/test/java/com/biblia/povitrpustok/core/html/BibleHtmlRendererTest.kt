package com.biblia.povitrpustok.core.html

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for BibleHtmlRenderer: parsed real corpus HTML must map to an
 * AnnotatedString carrying the formatting (italic/bold/color/alignment)
 * and preserving line breaks and text. No Android UI involved.
 */
class BibleHtmlRendererTest {

    private val parser = BibleHtmlParser()

    private fun fixture(name: String): String {
        val stream = javaClass.getResourceAsStream("/html/$name")
            ?: error("missing fixture $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun james_italic_renders_as_italic_span() {
        val doc = parser.parse(fixture("james-1-8.html"))
        val rendered = BibleHtmlRenderer.render(doc)

        assertEquals(
            "“Tuzo môg kortai tosoch tujea peleacho kôr!” – ho Povitr Pustokant dil’lo Devachea Rajeacho mukhêl kaido tumi samballxat zalear, borem kortat.",
            rendered.text
        )
        assertTrue(
            "expected an italic span",
            rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        )
        // only the quoted phrase is italic
        val italicRange = rendered.spanStyles.first { it.item.fontStyle == FontStyle.Italic }
        assertEquals(0, italicRange.start)
        assertEquals("“Tuzo môg kortai tosoch tujea peleacho kôr!”".length, italicRange.end)
    }

    @Test
    fun john_1_1_keeps_left_opening_and_centered_italic_continuation() {
        val doc = parser.parse(fixture("john-1-1.html"))
        val rendered = BibleHtmlRenderer.render(doc)

        assertTrue(
            "the normal opening must stay left-aligned",
            rendered.text.startsWith("Orombak sogllê rochnnê adim,")
        )
        assertTrue(
            "the continuation must remain italic",
            rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        )
        assertTrue(
            "the italic continuation must carry a centered paragraph style",
            rendered.paragraphStyles.any { it.item.textAlign == TextAlign.Center }
        )
        assertTrue(
            "the full verse text must be present",
            rendered.text.contains("Sôbd aslo") && rendered.text.contains("Sôbd Dev aslo")
        )
    }

    @Test
    fun job_poetic_preserves_line_breaks_and_center_alignment() {
        val doc = parser.parse(fixture("job-4-2-poetic.html"))
        val rendered = BibleHtmlRenderer.render(doc)

        assertTrue("poetic text must keep line breaks", rendered.text.contains("Ami tuka kiteim sanglear\ntum somzun ghenvxi?"))
        assertTrue(
            "expected a centered paragraph style",
            rendered.paragraphStyles.any { it.item.textAlign == TextAlign.Center }
        )
    }

    @Test
    fun esther_heading_renders_text_and_centering() {
        val doc = parser.parse(fixture("esther-intro.html"))
        val rendered = BibleHtmlRenderer.render(doc)
        assertEquals("ESTHER", rendered.text)
        assertTrue(rendered.paragraphStyles.any { it.item.textAlign == TextAlign.Center })
    }

    @Test
    fun leviticus_heading_preserves_color_span() {
        val doc = parser.parse(fixture("leviticus-heading.html"))
        val rendered = BibleHtmlRenderer.render(doc)

        assertTrue(rendered.text.contains("Hom’-Bhettô"))
        assertTrue(
            "expected a #0000ff color span",
            rendered.spanStyles.any { it.item.color == Color(0xFF0000FF) }
        )
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun psalms_intro_preserves_bold_span() {
        val doc = parser.parse(fixture("psalms-intro.html"))
        val rendered = BibleHtmlRenderer.render(doc)

        assertTrue(rendered.text.contains("Iave (Sorvespora), Tum amcho mogacho Dev."))
        assertTrue(
            "expected a bold span",
            rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        )
    }

    @Test
    fun jeremias_decoded_text_renders_without_entities() {
        val doc = parser.parse(fixture("jeremias-1-15.html"))
        val rendered = BibleHtmlRenderer.render(doc)
        assertTrue(!rendered.text.contains('&'))
        assertTrue(rendered.text.contains("\u2018Kiteak, polle,\nUt’torechea raxttrantlea"))
    }

    @Test
    fun malformed_input_does_not_crash_and_keeps_stray_less_than() {
        val doc = parser.parse(fixture("ikings-verse-132-malformed.html"))
        val rendered = BibleHtmlRenderer.render(doc)
        assertTrue("stray '<' must survive", rendered.text.endsWith(".\n<"))
    }

    @Test
    fun malformed_font_color_is_ignored_without_crash() {
        val doc = parser.parse("x<font color='ff#0000'>y</font>z")
        val rendered = BibleHtmlRenderer.render(doc)
        assertEquals("xyz", rendered.text)
        // the malformed color must not produce a color span
        assertTrue(rendered.spanStyles.none { it.item.color != Color.Unspecified })
    }

    @Test
    fun block_elements_are_separated_by_single_newlines() {
        val doc = parser.parse("<p>one</p><p>two</p><p>three</p>")
        val rendered = BibleHtmlRenderer.render(doc)
        assertEquals("one\ntwo\nthree", rendered.text)
    }
}
