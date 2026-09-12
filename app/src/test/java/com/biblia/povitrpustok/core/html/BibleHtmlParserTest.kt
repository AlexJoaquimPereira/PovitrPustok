package com.biblia.povitrpustok.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for BibleHtmlParser using real HTML extracted verbatim
 * from the Bible databases (Phase 3 Part F). Fixtures live in
 * app/src/test/resources/html/.
 *
 * Assertions verify semantic structure preservation, not serialization.
 */
class BibleHtmlParserTest {

    private val parser = BibleHtmlParser()

    private fun fixture(name: String): String {
        val stream = javaClass.getResourceAsStream("/html/$name")
            ?: error("missing fixture $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    // James 1:8 — ordinary prose with an inline <i> span
    @Test
    fun james_prose_with_italic_parses_to_italic_and_text() {
        val doc = parser.parse(fixture("james-1-8.html"))
        assertTrue("no diagnostics expected", doc.diagnostics.isEmpty())
        assertEquals(2, doc.children.size)

        val italic = doc.children[0] as BibleHtmlNode.Italic
        assertEquals(1, italic.children.size)
        assertEquals(
            "“Tuzo môg kortai tosoch tujea peleacho kôr!”",
            (italic.children[0] as BibleHtmlNode.Text).text
        )

        val tail = doc.children[1] as BibleHtmlNode.Text
        assertEquals(
            " – ho Povitr Pustokant dil’lo Devachea Rajeacho mukhêl kaido tumi samballxat zalear, borem kortat.",
            tail.text
        )
    }

    // Job 4:2 — poetic text: centered paragraph with <br/> line breaks.
    // The source verse leaves <p> unclosed — the parser must report that.
    @Test
    fun job_poetic_paragraph_preserves_breaks_and_alignment() {
        val doc = parser.parse(fixture("job-4-2-poetic.html"))
        assertTrue(
            "expected UNCLOSED_TAG (source <p> is unclosed), got ${doc.diagnostics.map { it.kind }}",
            doc.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.UNCLOSED_TAG }
        )

        val paragraph = doc.children.first { it is BibleHtmlNode.Paragraph } as BibleHtmlNode.Paragraph
        assertEquals(BibleHtmlAlignment.CENTER, paragraph.alignment)

        val breaks = paragraph.children.count { it is BibleHtmlNode.Break }
        assertTrue("expected multiple line breaks, got $breaks", breaks >= 4)
        assertEquals(
            "Ami tuka kiteim sanglear",
            (paragraph.children.first() as BibleHtmlNode.Text).text
        )
        // line structure: text, Break, text, Break, ...
        val alternating = paragraph.children.zipWithNext().all { (a, b) ->
            (a is BibleHtmlNode.Text && b is BibleHtmlNode.Break) ||
                (a is BibleHtmlNode.Break && b is BibleHtmlNode.Text)
        }
        assertTrue("poetic lines must alternate text and Break", alternating)
    }

    // Esther intro — centered h2 with colored font
    @Test
    fun esther_intro_heading_preserves_alignment_and_color() {
        val doc = parser.parse(fixture("esther-intro.html"))
        assertTrue("no diagnostics expected", doc.diagnostics.isEmpty())
        assertEquals(1, doc.children.size)

        val heading = doc.children[0] as BibleHtmlNode.Heading
        assertEquals(2, heading.level)
        assertEquals(BibleHtmlAlignment.CENTER, heading.alignment)
        val font = heading.children[0] as BibleHtmlNode.Font
        assertEquals("#ff0000", font.color)
        assertEquals("ESTHER", (font.children[0] as BibleHtmlNode.Text).text)
    }

    // Leviticus — three separate centered headings, third nests <i> inside <font>
    @Test
    fun leviticus_multiple_headings_remain_separate_and_ordered() {
        val doc = parser.parse(fixture("leviticus-heading.html"))
        assertTrue("no diagnostics expected", doc.diagnostics.isEmpty())

        val headings = doc.children.filterIsInstance<BibleHtmlNode.Heading>()
        assertEquals(3, headings.size)
        assertTrue(headings.all { it.alignment == BibleHtmlAlignment.CENTER })
        assertTrue(headings.all { it.level == 3 })

        val third = headings[2]
        val italic = third.children[0] as BibleHtmlNode.Italic
        val font = italic.children[0] as BibleHtmlNode.Font
        assertEquals("#0000ff", font.color)
        assertEquals("Hom’-Bhettô", (font.children[0] as BibleHtmlNode.Text).text)
    }

    // Jeremiah 1:15 — CSS style alignment, <br />, entities (&lsquo; &rsquo; &nbsp; &atilde; &mdash;).
    // The source verse has no opening <p> for its closing </p> and ends with
    // an unclosed <p style="text-align:center"> — both must be diagnosed, not
    // silently swallowed.
    @Test
    fun jeremias_decodes_entities_and_css_alignment() {
        val doc = parser.parse(fixture("jeremias-1-15.html"))

        val kinds = doc.diagnostics.map { it.kind }
        assertTrue("expected UNBALANCED_CLOSE, got $kinds", kinds.contains(BibleHtmlDiagnosticKind.UNBALANCED_CLOSE))
        assertTrue("expected UNCLOSED_TAG, got $kinds", kinds.contains(BibleHtmlDiagnosticKind.UNCLOSED_TAG))

        // poetic content lives at root level (the source has no opening <p>)
        assertTrue(doc.children.any { it is BibleHtmlNode.Break })
        // trailing <p style="text-align:center"> parsed with CSS alignment
        val trailing = doc.children.filterIsInstance<BibleHtmlNode.Paragraph>().single()
        assertEquals(BibleHtmlAlignment.CENTER, trailing.alignment)

        val text = doc.children.joinToString("") { collectText(it) }
        assertTrue("&lsquo; must decode to ‘", text.contains("\u2018Kiteak, polle,"))
        assertTrue("&rsquo; must decode to ’", text.contains("Ut’torechea"))
        assertTrue("&nbsp; must decode to a non-breaking space", text.contains("\u00A0"))
        assertTrue("&atilde; must decode to ã", text.contains("Tãnnim"))
        assertTrue("&mdash; must decode to —", text.contains("— hea tanchea"))
        assertTrue("no raw entities may remain", !text.contains('&'))
    }

    // Psalms intro — nested <i> with inline <b>
    @Test
    fun psalms_intro_preserves_bold_inside_italic() {
        val doc = parser.parse(fixture("psalms-intro.html"))
        assertTrue("no diagnostics expected", doc.diagnostics.isEmpty())

        val italic = doc.children.first { it is BibleHtmlNode.Italic } as BibleHtmlNode.Italic
        val bold = italic.children.first { it is BibleHtmlNode.Bold } as BibleHtmlNode.Bold
        val boldText = (bold.children[0] as BibleHtmlNode.Text).text
        assertEquals("Iave (Sorvespora), Tum amcho mogacho Dev.", boldText)
    }

    // 1 Kings verse 132 — malformed source: truncated "</p><" at end
    @Test
    fun malformed_truncated_close_is_kept_literal_and_diagnosed() {
        val doc = parser.parse(fixture("ikings-verse-132-malformed.html"))

        val kinds = doc.diagnostics.map { it.kind }
        assertTrue("expected UNBALANCED_CLOSE, got $kinds", kinds.contains(BibleHtmlDiagnosticKind.UNBALANCED_CLOSE))
        assertTrue("expected MALFORMED_TAG, got $kinds", kinds.contains(BibleHtmlDiagnosticKind.MALFORMED_TAG))

        val last = doc.children.last()
        assertEquals("stray '<' must survive as literal text", BibleHtmlNode.Text("<"), last)
        // source text remains recoverable
        val text = doc.children.joinToString("") { collectText(it) }
        assertTrue(text.startsWith("Ahishar, Rajmahalacho zobabdar aslo"))
        assertTrue(text.endsWith("zôdd vavr korpi chakrancho zobabdar.\n<"))
    }

    // Genesis 17:11-12 — ordinary prose, no markup
    @Test
    fun genesis_plain_prose_is_a_single_text_node() {
        val doc = parser.parse(fixture("genesis-17-11-12-range.html"))
        assertTrue("no diagnostics expected", doc.diagnostics.isEmpty())
        assertEquals(1, doc.children.size)
        val text = doc.children[0] as BibleHtmlNode.Text
        assertTrue(text.text.startsWith("Ani atanch thaun ho kaido lagu zata:"))
        assertTrue(text.text.contains("sunôt kortoleat;"))
    }

    // Robustness: unsupported tag becomes Unknown + diagnostic; content kept
    @Test
    fun unsupported_tag_becomes_unknown_node_with_diagnostic() {
        val doc = parser.parse("before <div class='x'>middle</div> after")
        assertTrue(
            doc.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.UNKNOWN_TAG }
        )
        val unknown = doc.children.filterIsInstance<BibleHtmlNode.Unknown>().single()
        assertEquals("div", unknown.tag)
        assertEquals(mapOf("class" to "x"), unknown.attributes)
        val rendered = doc.children.joinToString("") { collectText(it) }
        assertEquals("before middle after", rendered)
    }

    // Robustness: whitespace runs collapse; unclosed italic is diagnosed
    @Test
    fun whitespace_collapses_and_unclosed_tags_are_diagnosed() {
        val doc = parser.parse("a  \n  b <i>c")
        assertTrue(doc.diagnostics.any { it.kind == BibleHtmlDiagnosticKind.UNCLOSED_TAG })
        val text = doc.children.joinToString("") { collectText(it) }
        assertEquals("a b c", text)
    }

    private fun collectText(node: BibleHtmlNode): String = when (node) {
        is BibleHtmlNode.Text -> node.text
        is BibleHtmlNode.Break -> "\n"
        is BibleHtmlNode.Paragraph -> node.children.joinToString("") { collectText(it) }
        is BibleHtmlNode.Heading -> node.children.joinToString("") { collectText(it) }
        is BibleHtmlNode.Italic -> node.children.joinToString("") { collectText(it) }
        is BibleHtmlNode.Bold -> node.children.joinToString("") { collectText(it) }
        is BibleHtmlNode.Font -> node.children.joinToString("") { collectText(it) }
        is BibleHtmlNode.Unknown -> node.children.joinToString("") { collectText(it) }
    }
}
