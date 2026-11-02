package com.biblia.povitrpustok.core.html

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1-01/S1-02: the renderer's source→rendered map and the overlay seam.
 *
 * Source offsets are raw-HTML offsets; rendered offsets are character
 * positions in the displayed AnnotatedString. These tests pin entities,
 * line breaks, block separators, dropped markup and whitespace collapsing.
 */
class RenderedTextTest {

    private val parser = BibleHtmlParser()

    private fun render(html: String): RenderedText =
        BibleHtmlRenderer.render(parser.parse(html))

    @Test
    fun source_word_maps_to_the_same_rendered_word() {
        val html = "<p>Devan mhollem: uzvadd zaum</p>"
        val rendered = render(html)
        val start = html.indexOf("uzvadd")
        val mapped = rendered.sourceToRendered(start..start + "uzvadd".length - 1)

        assertEquals("uzvadd", rendered.text.substring(mapped!!.first, mapped.last + 1))
    }

    @Test
    fun decoded_entity_maps_to_its_single_rendered_character() {
        val html = "a &amp; b"
        val rendered = render(html)
        val entityStart = html.indexOf("&amp;")
        val mapped = rendered.sourceToRendered(entityStart..entityStart + "&amp;".length - 1)

        assertEquals("&", rendered.text.substring(mapped!!.first, mapped.last + 1))
    }

    @Test
    fun a_word_after_a_line_break_maps_past_the_inserted_newline() {
        val html = "one<br>two"
        val rendered = render(html)
        assertEquals("one\ntwo", rendered.text)
        val start = html.indexOf("two")
        val mapped = rendered.sourceToRendered(start..start + 2)

        assertEquals(4..6, mapped)
        assertEquals("two", rendered.text.substring(mapped!!.first, mapped.last + 1))
    }

    @Test
    fun a_word_after_a_paragraph_separator_maps_past_the_inserted_newline() {
        val html = "<p>one</p><p>two</p>"
        val rendered = render(html)
        assertEquals("one\ntwo", rendered.text)
        val start = html.indexOf("two")
        val mapped = rendered.sourceToRendered(start..start + 2)

        assertEquals(4..6, mapped)
        assertEquals("two", rendered.text.substring(mapped!!.first, mapped.last + 1))
    }

    @Test
    fun a_range_spanning_two_blocks_covers_both_rendered_runs() {
        val html = "<p>one</p><p>two</p>"
        val rendered = render(html)
        val mapped = rendered.sourceToRendered(0..html.lastIndex)

        assertEquals(0..rendered.text.lastIndex, mapped)
    }

    @Test
    fun markup_without_text_maps_to_nothing() {
        val rendered = render("<p>one</p>")
        assertNull(rendered.sourceToRendered(0..2))
    }

    @Test
    fun a_collapsed_whitespace_run_maps_to_one_space() {
        val rendered = render("a  \n b")
        assertEquals("a b", rendered.text)
        val mapped = rendered.sourceToRendered(1..4)

        assertEquals(1..1, mapped)
        assertEquals(' ', rendered.text[1])
    }

    @Test
    fun leading_newlines_are_trimmed_without_shearing_the_map() {
        val html = "<br>one"
        val rendered = render(html)
        assertEquals("one", rendered.text)
        val start = html.indexOf("one")
        val mapped = rendered.sourceToRendered(start..start + 2)

        assertEquals(0..2, mapped)
        assertEquals("one", rendered.text.substring(mapped!!.first, mapped.last + 1))
    }

    @Test
    fun an_empty_source_range_maps_to_nothing() {
        assertNull(render("one").sourceToRendered(2..1))
    }

    @Test
    fun a_no_break_space_survives_as_content() {
        val rendered = render("a\u00A0b")

        assertEquals("a\u00A0b", rendered.text)
        val mapped = rendered.sourceToRendered(1..1)!!
        assertEquals("\u00A0", rendered.text.substring(mapped.first, mapped.last + 1))
    }

    @Test
    fun a_space_dropped_before_a_break_maps_to_nothing() {
        val html = "one <br>two"
        val rendered = render(html)

        assertEquals("one\ntwo", rendered.text)
        assertNull(rendered.sourceToRendered(3..3))
    }

    @Test
    fun every_rendered_character_has_an_entry_and_separators_are_null() {
        val rendered = render("<p>one</p><p>two</p>")

        assertEquals(rendered.text.length, rendered.sourceSpans.size)
        assertNotNull(rendered.sourceSpans[0])
        assertNull(rendered.sourceSpans[3])
    }

    @Test
    fun parsed_text_nodes_carry_one_span_per_character() {
        val document = parser.parse("ab &amp; c")
        val text = document.children.filterIsInstance<BibleHtmlNode.Text>().single()

        assertEquals(text.text.length * 2, text.sourceSpans.size)
        val ampersand = text.text.indexOf('&')
        assertEquals(3..7, text.sourceSpanAt(ampersand))
    }

    @Test
    fun an_empty_overlay_returns_the_base_unchanged() {
        val base = AnnotatedString("hello")

        assertSame(base, RenderedOverlay().applyTo(base))
    }

    @Test
    fun a_highlight_is_added_without_dropping_base_styles() {
        val base = AnnotatedString.Builder("hello").apply {
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, 5)
        }.toAnnotatedString()
        val highlight = SpanStyle(background = Color(0xFFFFF176))
        val mapped = RenderedOverlay(
            highlights = listOf(AnnotatedString.Range(highlight, 1, 4))
        ).applyTo(base)

        assertEquals("hello", mapped.text)
        assertTrue(mapped.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(
            mapped.spanStyles.any {
                it.item.background == Color(0xFFFFF176) && it.start == 1 && it.end == 4
            }
        )
    }

    @Test
    fun a_link_annotation_is_applied_in_rendered_coordinates() {
        val base = AnnotatedString("hello world")
        val link = LinkAnnotation.Url("https://example.com")
        val mapped = RenderedOverlay(
            links = listOf(AnnotatedString.Range(link, 6, 11))
        ).applyTo(base)

        assertEquals(1, mapped.getLinkAnnotations(6, 11).size)
        assertTrue(mapped.getLinkAnnotations(0, 5).isEmpty())
    }

    @Test
    fun a_source_word_can_be_highlighted_through_the_map() {
        val html = "<p>one two</p>"
        val rendered = render(html)
        val start = html.indexOf("two")
        val range = rendered.sourceToRendered(start..start + 2)!!
        val mapped = RenderedOverlay(
            highlights = listOf(AnnotatedString.Range(SpanStyle(background = Color.Yellow), range.first, range.last + 1))
        ).applyTo(rendered.annotated)

        val highlight = mapped.spanStyles.single { it.item.background == Color.Yellow }
        assertEquals("two", mapped.text.substring(highlight.start, highlight.end))
    }
}
