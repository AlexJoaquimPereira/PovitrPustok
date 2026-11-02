package com.biblia.povitrpustok.core.html

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle

/**
 * Rendered output plus the source→rendered offset map (S1-01).
 *
 * [sourceSpans] has one entry per character of [annotated].text. Each entry is
 * the raw-HTML range that produced that character, or `null` for a newline the
 * renderer inserted between blocks. [sourceToRendered] resolves a raw-HTML
 * range to the rendered character range that displays it, or `null` when the
 * range contains only tags, attributes or whitespace the parser dropped.
 *
 * The map is what later features anchor to: word-level TTS highlighting,
 * inline cross-references, and text-range notes all need to turn a source
 * position into a position in the displayed [AnnotatedString].
 */
data class RenderedText(
    val annotated: AnnotatedString,
    val sourceSpans: List<IntRange?>
) {

    /** The rendered characters, without Compose style metadata. */
    val text: String get() = annotated.text

    /**
     * Resolves a raw-HTML range to the rendered characters it produced.
     *
     * The returned range is inclusive. A source range that spans dropped
     * markup only (no text) returns `null` rather than an arbitrary neighbour,
     * so callers can skip it explicitly.
     */
    fun sourceToRendered(source: IntRange): IntRange? {
        if (source.isEmpty()) return null
        var first = -1
        var last = -1
        sourceSpans.forEachIndexed { index, span ->
            if (span != null && span.first <= source.last && span.last >= source.first) {
                if (first < 0) first = index
                last = index
            }
        }
        return if (first < 0) null else first..last
    }
}

/**
 * Renders a parsed [BibleHtmlDocument] into an [AnnotatedString] that a
 * Compose `Text` can display (implementation plan §18; Phase 3 Part D).
 *
 * Only data-structure transformation: no layout, no Compose UI, no
 * database access — unit-testable on the JVM. The future reader applies
 * its own typography on top; this renderer contributes the formatting
 * carried by the HTML (italic, bold, font color, alignment, line breaks).
 *
 * Fidelity rules:
 *  - [BibleHtmlNode.Break] becomes `\n` (poetic line structure);
 *  - paragraphs/headings become paragraphs with their alignment;
 *  - malformed colors are ignored (no color span), never crash;
 *  - [BibleHtmlNode.Unknown] content is still rendered (never dropped);
 *  - every rendered character keeps the raw-HTML range that produced it
 *    (S1-01), so callers can address positions in the source.
 */
object BibleHtmlRenderer {

    fun render(document: BibleHtmlDocument): RenderedText {
        val builder = AnnotatedString.Builder()
        val spans = mutableListOf<IntRange?>()
        renderNodes(builder, document.children, SpanStyle(), RenderState(spans))
        return builder.toAnnotatedString().trimNewlineEdges(spans)
    }

    private class RenderState(val spans: MutableList<IntRange?>) {
        var hasContent: Boolean = false
        var endsWithNewline: Boolean = false
    }

    private fun renderNodes(
        builder: AnnotatedString.Builder,
        nodes: List<BibleHtmlNode>,
        style: SpanStyle,
        state: RenderState
    ) {
        for (node in nodes) {
            when (node) {
                is BibleHtmlNode.Text -> {
                    builder.withStyle(style) {
                        append(builder, state, node.text, node.sourceSpans)
                    }
                    state.hasContent = true
                    state.endsWithNewline = false
                }

                BibleHtmlNode.Break -> {
                    append(builder, state, "\n", null)
                    state.hasContent = true
                    state.endsWithNewline = true
                }

                is BibleHtmlNode.Paragraph -> {
                    ensureBlockSeparator(builder, state)
                    // Only an explicit alignment emits a ParagraphStyle;
                    // unaligned paragraphs inherit the Text-level alignment
                    // (used by the Psalm poetic mode, Issue 8).
                    if (node.alignment != null) {
                        builder.withStyle(ParagraphStyle(textAlign = node.alignment.toTextAlign() ?: TextAlign.Unspecified)) {
                            renderNodes(builder, node.children, style, state)
                        }
                    } else {
                        renderNodes(builder, node.children, style, state)
                    }
                    ensureBlockSeparator(builder, state)
                }

                is BibleHtmlNode.Heading -> {
                    ensureBlockSeparator(builder, state)
                    if (node.alignment != null) {
                        builder.withStyle(ParagraphStyle(textAlign = node.alignment.toTextAlign() ?: TextAlign.Unspecified)) {
                            renderNodes(builder, node.children, style, state)
                        }
                    } else {
                        renderNodes(builder, node.children, style, state)
                    }
                    ensureBlockSeparator(builder, state)
                }

                is BibleHtmlNode.Italic ->
                    renderNodes(builder, node.children, style.merge(SpanStyle(fontStyle = FontStyle.Italic)), state)

                is BibleHtmlNode.Bold ->
                    renderNodes(builder, node.children, style.merge(SpanStyle(fontWeight = FontWeight.Bold)), state)

                is BibleHtmlNode.Font -> {
                    val color = parseColor(node.color)
                    val merged = if (color != null) style.merge(SpanStyle(color = color)) else style
                    renderNodes(builder, node.children, merged, state)
                }

                is BibleHtmlNode.Unknown ->
                    // Content preserved; formatting of unknown tags is not interpreted.
                    renderNodes(builder, node.children, style, state)
            }
        }
    }

    /**
     * Appends [text] and one map entry per character. Text nodes carry their
     * parser-recorded spans; synthetic separators pass `null`.
     */
    private fun append(
        builder: AnnotatedString.Builder,
        state: RenderState,
        text: String,
        sourceSpans: IntArray?
    ) {
        builder.append(text)
        if (sourceSpans != null && sourceSpans.size == text.length * 2) {
            for (index in text.indices) {
                state.spans.add(sourceSpans[index * 2]..sourceSpans[index * 2 + 1])
            }
        } else {
            repeat(text.length) { state.spans.add(null) }
        }
    }

    private fun ensureBlockSeparator(builder: AnnotatedString.Builder, state: RenderState) {
        if (state.hasContent && !state.endsWithNewline) {
            append(builder, state, "\n", null)
            state.endsWithNewline = true
        }
    }

    private fun AnnotatedString.trimNewlineEdges(spans: List<IntRange?>): RenderedText {
        var start = 0
        while (start < text.length && text[start] == '\n') start++
        var end = text.length
        while (end > start && text[end - 1] == '\n') end--
        val trimmed = if (start == 0 && end == text.length) this else subSequence(start, end)
        return RenderedText(trimmed, spans.subList(start, end).toList())
    }

    private fun BibleHtmlAlignment?.toTextAlign(): TextAlign? = when (this) {
        BibleHtmlAlignment.LEFT -> TextAlign.Left
        BibleHtmlAlignment.CENTER -> TextAlign.Center
        BibleHtmlAlignment.RIGHT -> TextAlign.Right
        BibleHtmlAlignment.JUSTIFY -> TextAlign.Justify
        null -> null
    }

    /** Accepts `#RRGGBB`; malformed corpus values (e.g. `ff#0000`) return null. */
    private fun parseColor(value: String?): Color? {
        if (value == null) return null
        val hex = value.trim().removePrefix("#")
        if (hex.length != 6) return null
        val parsed = hex.toLongOrNull(16) ?: return null
        return Color(0xFF000000L or parsed)
    }
}
