package com.biblia.povitrpustok.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST-05: adversarial HTML inputs must terminate deterministically,
 * preserve literal content, and report controlled diagnostics.
 */
class BibleHtmlAdversarialTest {

    private val parser = BibleHtmlParser()

    @Test(timeout = 5_000)
    fun deeply_nested_inline_tags_terminate_and_preserve_text() {
        val html = "<i>".repeat(500) + "text" + "</i>".repeat(500)
        val document = parser.parse(html)
        assertTrue("well-formed nesting must not produce diagnostics", document.diagnostics.isEmpty())
        assertTrue("content must survive deep nesting", collectText(document.children).contains("text"))
    }

    @Test(timeout = 5_000)
    fun unterminated_tags_are_closed_at_end_with_diagnostics() {
        val document = parser.parse("<p><i>text")
        assertTrue(
            "unclosed tags must be reported",
            document.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.UNCLOSED_TAG }
        )
        assertTrue(collectText(document.children).contains("text"))
    }

    @Test(timeout = 5_000)
    fun unknown_entity_is_preserved_verbatim_and_reported() {
        val document = parser.parse("A &bogus; B")
        assertTrue(
            "unknown entity must be reported",
            document.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.UNKNOWN_ENTITY }
        )
        assertTrue(
            "unknown entity must remain literal text",
            collectText(document.children).contains("&bogus;")
        )
    }

    @Test(timeout = 5_000)
    fun stray_lt_is_literal_text_with_a_diagnostic() {
        val document = parser.parse("1 < 2 and 3 > 2")
        assertTrue(
            "stray '<' must be reported as a malformed tag",
            document.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.MALFORMED_TAG }
        )
        assertTrue(collectText(document.children).contains("< 2"))
    }

    @Test(timeout = 5_000)
    fun quoted_attribute_values_may_contain_markup_characters() {
        val document = parser.parse("<p title=\"a>b<c\">text</p>")
        assertTrue("a well-formed quoted attribute must not emit diagnostics", document.diagnostics.isEmpty())
        assertTrue(collectText(document.children).contains("text"))
    }

    @Test(timeout = 5_000)
    fun unterminated_quoted_attribute_terminates_as_a_malformed_tag() {
        val document = parser.parse("<p title=\"abc")
        assertTrue(
            "an unterminated quote must be reported",
            document.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.MALFORMED_TAG }
        )
    }

    @Test(timeout = 5_000)
    fun attribute_starting_with_equals_terminates_and_preserves_text() {
        val document = parser.parse("<p =>text")
        assertTrue(collectText(document.children).contains("text"))
    }

    @Test(timeout = 5_000)
    fun mixed_entities_decode_known_and_preserve_unknown() {
        val document = parser.parse("&amp;&bogus;&#65;")
        val text = collectText(document.children)
        assertTrue("known entity must decode, got '$text'", text.contains("&"))
        assertTrue("unknown entity must stay literal, got '$text'", text.contains("&bogus;"))
        assertTrue("numeric entity must decode, got '$text'", text.contains("A"))
        assertTrue(
            "the unknown entity must be diagnosed",
            document.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.UNKNOWN_ENTITY }
        )
    }

    @Test(timeout = 5_000)
    fun large_repetitive_input_terminates() {
        val document = parser.parse("<br/>".repeat(10_000))
        assertNotNull(document)
        assertEquals(
            "every break must be retained",
            10_000,
            document.children.count { it is BibleHtmlNode.Break }
        )
    }

    private fun collectText(nodes: List<BibleHtmlNode>): String =
        nodes.joinToString("") { node ->
            when (node) {
                is BibleHtmlNode.Text -> node.text
                is BibleHtmlNode.Break -> "\n"
                is BibleHtmlNode.Paragraph -> collectText(node.children)
                is BibleHtmlNode.Heading -> collectText(node.children)
                is BibleHtmlNode.Italic -> collectText(node.children)
                is BibleHtmlNode.Bold -> collectText(node.children)
                is BibleHtmlNode.Font -> collectText(node.children)
                is BibleHtmlNode.Unknown -> collectText(node.children)
            }
        }
}
