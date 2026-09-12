package com.biblia.povitrpustok.core.html

/**
 * Explicit, small node model for the Bible corpus HTML (implementation
 * plan §18). Node types cover exactly the constructs observed in the
 * corpus plus a deterministic [Unknown] fallback — never a general web
 * HTML tree.
 *
 * The parser and this model are pure Kotlin: no Android, Room, or Compose
 * types, so they are unit-testable on the JVM. The future reader renders
 * a [BibleHtmlDocument] through the Compose-facing renderer.
 */
sealed class BibleHtmlNode {

    /** Plain text with whitespace runs collapsed; entities decoded. */
    data class Text(val text: String) : BibleHtmlNode()

    /** A line break — `<br>` / `<br/>` — preserves poetic line structure. */
    data object Break : BibleHtmlNode()

    /** `<p>`; [alignment] from `align` or `style="text-align:..."`. */
    data class Paragraph(
        val alignment: BibleHtmlAlignment?,
        val children: List<BibleHtmlNode>
    ) : BibleHtmlNode()

    /** `<h2>`…`<h6>` (the corpus uses levels 2 and 3). */
    data class Heading(
        val level: Int,
        val alignment: BibleHtmlAlignment?,
        val children: List<BibleHtmlNode>
    ) : BibleHtmlNode()

    /** `<i>` and `<em>`. */
    data class Italic(val children: List<BibleHtmlNode>) : BibleHtmlNode()

    /** `<b>` (and `<strong>` if ever present). */
    data class Bold(val children: List<BibleHtmlNode>) : BibleHtmlNode()

    /**
     * `<font color="...">`. [color] is the raw attribute value (the corpus
     * contains malformed values such as `ff#0000`; they are preserved and
     * the renderer decides whether it can use them).
     */
    data class Font(
        val color: String?,
        val children: List<BibleHtmlNode>
    ) : BibleHtmlNode()

    /**
     * A well-formed tag outside the supported vocabulary. Content is kept,
     * not discarded, and a diagnostic is emitted by the parser.
     */
    data class Unknown(
        val tag: String,
        val attributes: Map<String, String>,
        val children: List<BibleHtmlNode>
    ) : BibleHtmlNode()
}

/** Block alignment (from `align=` or CSS `text-align:`). */
enum class BibleHtmlAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

/** Parsed result: ordered node tree plus parser diagnostics. */
data class BibleHtmlDocument(
    val children: List<BibleHtmlNode>,
    val diagnostics: List<BibleHtmlDiagnostic>
)
