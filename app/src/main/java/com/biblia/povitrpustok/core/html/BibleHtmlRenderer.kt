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
 *  - [BibleHtmlNode.Unknown] content is still rendered (never dropped).
 */
object BibleHtmlRenderer {

    fun render(document: BibleHtmlDocument): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val state = RenderState()
        renderNodes(builder, document.children, SpanStyle(), state)
        return builder.toAnnotatedString().trimNewlineEdges()
    }

    private class RenderState {
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
                    builder.withStyle(style) { builder.append(node.text) }
                    state.hasContent = true
                    state.endsWithNewline = false
                }

                BibleHtmlNode.Break -> {
                    builder.append("\n")
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

    private fun ensureBlockSeparator(builder: AnnotatedString.Builder, state: RenderState) {
        if (state.hasContent && !state.endsWithNewline) {
            builder.append("\n")
            state.endsWithNewline = true
        }
    }

    private fun AnnotatedString.trimNewlineEdges(): AnnotatedString {
        var start = 0
        while (start < text.length && text[start] == '\n') start++
        var end = text.length
        while (end > start && text[end - 1] == '\n') end--
        return if (start == 0 && end == text.length) this else subSequence(start, end)
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
