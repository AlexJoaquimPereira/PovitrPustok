package com.biblia.povitrpustok.core.html

import kotlin.math.min

/**
 * Deterministic parser for the Bible corpus HTML vocabulary
 * (implementation plan §18; Phase 3 Part C).
 *
 * Supported constructs (all verified against the real databases): text,
 * `<br>`/`<br/>`, `<p>` with `align=`/`style="text-align:..."`, `<h2>`–`<h6>`,
 * `<font color="...">`, `<i>`, `<em>`, `<b>`/`<strong>`, HTML entities, and
 * nested combinations. Attributes may be single- or double-quoted, and tags
 * may carry trailing whitespace (`<h3 align=center >`).
 *
 * Robustness rules (nothing is silently discarded):
 *  - a well-formed tag outside the vocabulary becomes [BibleHtmlNode.Unknown]
 *    plus a [BibleHtmlDiagnosticKind.UNKNOWN_TAG] diagnostic;
 *  - unclosed tags are closed at end of input (UNCLOSED_TAG diagnostic);
 *  - closing tags without an open match are reported (UNBALANCED_CLOSE);
 *  - a `<` sequence that cannot form a valid tag is kept as literal text
 *    (MALFORMED_TAG diagnostic) — the corpus contains such stray characters;
 *  - unknown entities are kept verbatim (UNKNOWN_ENTITY diagnostic).
 *
 * Whitespace semantics: runs of whitespace (space/newline/tab) inside text
 * collapse to a single space; poetic line structure is carried exclusively
 * by [BibleHtmlNode.Break] nodes, matching the corpus (content newlines
 * only occur adjacent to tags).
 */
class BibleHtmlParser {

    fun parse(html: String): BibleHtmlDocument {
        val diagnostics = mutableListOf<BibleHtmlDiagnostic>()
        val root = Builder(BuilderKind.ROOT)
        val stack = ArrayDeque<Builder>().apply { addLast(root) }

        var i = 0
        while (i < html.length) {
            when (html[i]) {
                '<' -> {
                    val tag = parseTag(html, i)
                    if (tag == null) {
                        diagnostics += BibleHtmlDiagnostic(
                            BibleHtmlDiagnosticKind.MALFORMED_TAG,
                            "stray '<' at offset $i is not a valid tag",
                            i
                        )
                        stack.last().appendText("<")
                        i += 1
                    } else {
                        applyTag(tag, stack, diagnostics)
                        i = tag.endOffset
                    }
                }
                '&' -> {
                    val decoded = BibleHtmlEntities.decode(html, i)
                    if (decoded != null) {
                        stack.last().appendText(decoded)
                        i = html.indexOf(';', i) + 1
                    } else {
                        val semi = html.indexOf(';', i)
                        val looksLikeEntity = semi in (i + 2)..(i + 12) &&
                            html.substring(i + 1, semi).all { it.isLetterOrDigit() || it == '#' }
                        if (looksLikeEntity) {
                            diagnostics += BibleHtmlDiagnostic(
                                BibleHtmlDiagnosticKind.UNKNOWN_ENTITY,
                                "unknown entity '${html.substring(i, min(semi + 1, html.length))}' at offset $i",
                                i
                            )
                        }
                        stack.last().appendText("&")
                        i += 1
                    }
                }
                else -> {
                    val start = i
                    while (i < html.length && html[i] != '<' && html[i] != '&') i++
                    stack.last().appendText(html.substring(start, i))
                }
            }
        }

        // Close any tags left open at end of input.
        while (stack.size > 1) {
            val open = stack.removeLast()
            diagnostics += BibleHtmlDiagnostic(
                BibleHtmlDiagnosticKind.UNCLOSED_TAG,
                "<${open.tagName}> left unclosed at end of input",
                open.startOffset
            )
            stack.last().addChild(open.build())
        }
        return BibleHtmlDocument(root.finish(), diagnostics)
    }

    // ------------------------------------------------------------------
    // Tag application
    // ------------------------------------------------------------------

    private fun applyTag(
        tag: ParsedTag,
        stack: ArrayDeque<Builder>,
        diagnostics: MutableList<BibleHtmlDiagnostic>
    ) {
        if (tag.closing) {
            var index = stack.size - 1
            while (index > 0 && stack[index].tagName != tag.name) index--
            if (index == 0) {
                diagnostics += BibleHtmlDiagnostic(
                    BibleHtmlDiagnosticKind.UNBALANCED_CLOSE,
                    "closing </${tag.name}> without a matching open tag",
                    tag.startOffset
                )
                return
            }
            // Implicitly close any tags nested above the match.
            while (stack.size - 1 > index) {
                val nested = stack.removeLast()
                diagnostics += BibleHtmlDiagnostic(
                    BibleHtmlDiagnosticKind.UNCLOSED_TAG,
                    "<${nested.tagName}> implicitly closed by </${tag.name}>",
                    nested.startOffset
                )
                stack.last().addChild(nested.build())
            }
            val closed = stack.removeLast()
            stack.last().addChild(closed.build())
            return
        }

        when (tag.name) {
            "br" -> stack.last().addChild(BibleHtmlNode.Break)
            "p" -> stack.addLast(
                Builder(BuilderKind.PARAGRAPH).apply {
                    tagName = tag.name
                    alignment = alignmentFrom(tag.attributes)
                    startOffset = tag.startOffset
                }
            )
            in HEADING_TAGS -> {
                if (tag.selfClosing) {
                    stack.last().addChild(
                        BibleHtmlNode.Heading(
                            level = tag.name.drop(1).toIntOrNull() ?: 2,
                            alignment = alignmentFrom(tag.attributes),
                            children = emptyList()
                        )
                    )
                } else {
                    stack.addLast(
                        Builder(BuilderKind.HEADING).apply {
                            tagName = tag.name
                            level = tag.name.drop(1).toIntOrNull() ?: 2
                            alignment = alignmentFrom(tag.attributes)
                            startOffset = tag.startOffset
                        }
                    )
                }
            }
            "i", "em" -> stack.addLast(
                Builder(BuilderKind.ITALIC).apply {
                    tagName = tag.name
                    startOffset = tag.startOffset
                }
            )
            "b", "strong" -> stack.addLast(
                Builder(BuilderKind.BOLD).apply {
                    tagName = tag.name
                    startOffset = tag.startOffset
                }
            )
            "font" -> {
                if (tag.selfClosing) {
                    stack.last().addChild(BibleHtmlNode.Font(tag.attributes["color"], emptyList()))
                } else {
                    stack.addLast(
                        Builder(BuilderKind.FONT).apply {
                            tagName = tag.name
                            color = tag.attributes["color"]
                            startOffset = tag.startOffset
                        }
                    )
                }
            }
            else -> {
                diagnostics += BibleHtmlDiagnostic(
                    BibleHtmlDiagnosticKind.UNKNOWN_TAG,
                    "unsupported tag <${tag.name}> at offset ${tag.startOffset}",
                    tag.startOffset
                )
                if (tag.selfClosing) {
                    stack.last().addChild(BibleHtmlNode.Unknown(tag.name, tag.attributes, emptyList()))
                } else {
                    stack.addLast(
                        Builder(BuilderKind.UNKNOWN).apply {
                            tagName = tag.name
                            attributes = tag.attributes
                            startOffset = tag.startOffset
                        }
                    )
                }
            }
        }
    }

    private fun alignmentFrom(attributes: Map<String, String>): BibleHtmlAlignment? {
        attributes["align"]?.let { alignmentValue(it)?.let { return it } }
        attributes["style"]?.let { style ->
            STYLE_ALIGN_REGEX.find(style)?.groupValues?.get(1)?.let {
                alignmentValue(it)?.let { a -> return a }
            }
        }
        return null
    }

    private fun alignmentValue(value: String): BibleHtmlAlignment? = when (value.lowercase()) {
        "left" -> BibleHtmlAlignment.LEFT
        "center" -> BibleHtmlAlignment.CENTER
        "right" -> BibleHtmlAlignment.RIGHT
        "justify" -> BibleHtmlAlignment.JUSTIFY
        else -> null
    }

    // ------------------------------------------------------------------
    // Tokenizer
    // ------------------------------------------------------------------

    private fun parseTag(html: String, start: Int): ParsedTag? {
        var i = start + 1
        if (i >= html.length) return null
        val closing = html[i] == '/'
        if (closing) i++

        val nameStart = i
        while (i < html.length && html[i].isLetterOrDigit()) i++
        if (i == nameStart) return null
        val name = html.substring(nameStart, i)

        if (closing) {
            while (i < html.length && html[i] != '>') i++
            if (i >= html.length) return null
            return ParsedTag(
                name, closing = true, selfClosing = false, attributes = emptyMap(),
                startOffset = start, endOffset = i + 1
            )
        }

        val attributes = linkedMapOf<String, String>()
        while (true) {
            while (i < html.length && html[i].isWhitespace()) i++
            if (i >= html.length) return null
            when {
                html[i] == '>' ->
                    return ParsedTag(name, false, false, attributes, start, i + 1)
                html[i] == '/' && i + 1 < html.length && html[i + 1] == '>' ->
                    return ParsedTag(name, false, true, attributes, start, i + 2)
                html[i] == '<' ->
                    return null
                html[i] == '/' -> {
                    // HTML-01: a lone '/' that is not part of '/>' cannot
                    // start an attribute. Consume it so the tokenizer always
                    // makes progress instead of looping forever.
                    i++
                }
                else -> {
                    val attrNameStart = i
                    while (i < html.length &&
                        !html[i].isWhitespace() && html[i] != '=' && html[i] != '>' && html[i] != '/'
                    ) {
                        i++
                    }
                    val attrName = html.substring(attrNameStart, i)

                    var j = i
                    while (j < html.length && html[j].isWhitespace()) j++
                    var value = ""
                    if (j < html.length && html[j] == '=') {
                        j++
                        while (j < html.length && html[j].isWhitespace()) j++
                        if (j < html.length && (html[j] == '"' || html[j] == '\'')) {
                            val quote = html[j]
                            val valueStart = j + 1
                            val quoteEnd = html.indexOf(quote, valueStart)
                            if (quoteEnd < 0) return null
                            value = html.substring(valueStart, quoteEnd)
                            i = quoteEnd + 1
                        } else {
                            val valueStart = j
                            while (j < html.length && !html[j].isWhitespace() && html[j] != '>' && html[j] != '/') j++
                            value = html.substring(valueStart, j)
                            i = j
                        }
                    } else {
                        i = j
                    }
                    attributes[attrName.lowercase()] = value
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal model
    // ------------------------------------------------------------------

    private enum class BuilderKind { ROOT, PARAGRAPH, HEADING, ITALIC, BOLD, FONT, UNKNOWN }

    private class Builder(val kind: BuilderKind) {
        var tagName: String = ""
        var alignment: BibleHtmlAlignment? = null
        var level: Int = 0
        var color: String? = null
        var attributes: Map<String, String> = emptyMap()
        var startOffset: Int = 0

        private val children = mutableListOf<BibleHtmlNode>()
        private val text = StringBuilder()

        /** Appends a built node, flushing any pending text first. */
        fun addChild(node: BibleHtmlNode) {
            flushText()
            if (node is BibleHtmlNode.Break) {
                trimTrailingText()
            }
            children.add(node)
        }

        fun appendText(piece: String) {
            text.append(piece)
        }

        /**
         * Flushes pending text with corpus whitespace semantics:
         *  - runs of whitespace collapse to a single space;
         *  - runs consisting only of whitespace are dropped;
         *  - a leading space is kept only between inline siblings (Text /
         *    Italic / Bold / Font / Unknown) — never at container start or
         *    after a line break / block element;
         *  - a trailing space is kept until the container is closed
         *    (trimmed in [build]).
         */
        fun flushText() {
            if (text.isEmpty()) return
            var cleaned = WHITESPACE_REGEX.replace(text, " ")
            text.clear()
            if (cleaned.isBlank()) return
            val previousInline = children.lastOrNull()?.let { it::class in INLINE_KINDS } ?: false
            if (children.isEmpty() || !previousInline) {
                cleaned = cleaned.trimStart()
            }
            children.add(BibleHtmlNode.Text(cleaned))
        }

        /** Flushes pending text and trims trailing whitespace — for the root. */
        fun finish(): List<BibleHtmlNode> {
            flushText()
            trimTrailingText()
            return children.toList()
        }

        private fun trimTrailingText() {
            val lastIndex = children.lastIndex
            if (lastIndex >= 0) {
                val last = children[lastIndex]
                if (last is BibleHtmlNode.Text) {
                    val trimmed = last.text.trimEnd()
                    if (trimmed != last.text) {
                        children[lastIndex] = BibleHtmlNode.Text(trimmed)
                    }
                }
            }
        }

        fun build(): BibleHtmlNode {
            flushText()
            trimTrailingText()
            return when (kind) {
                BuilderKind.ROOT -> error("root builder is not a node")
                BuilderKind.PARAGRAPH -> BibleHtmlNode.Paragraph(alignment, children.toList())
                BuilderKind.HEADING -> BibleHtmlNode.Heading(level, alignment, children.toList())
                BuilderKind.ITALIC -> BibleHtmlNode.Italic(children.toList())
                BuilderKind.BOLD -> BibleHtmlNode.Bold(children.toList())
                BuilderKind.FONT -> BibleHtmlNode.Font(color, children.toList())
                BuilderKind.UNKNOWN -> BibleHtmlNode.Unknown(tagName, attributes, children.toList())
            }
        }
    }

    private data class ParsedTag(
        val name: String,
        val closing: Boolean,
        val selfClosing: Boolean,
        val attributes: Map<String, String>,
        val startOffset: Int,
        val endOffset: Int
    )

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
        val STYLE_ALIGN_REGEX = Regex("text-align\\s*:\\s*(left|center|right|justify)", RegexOption.IGNORE_CASE)
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        // Node kinds whose content flows inline; a space before the next text
        // after one of these is significant.
        val INLINE_KINDS = setOf(
            BibleHtmlNode.Text::class,
            BibleHtmlNode.Italic::class,
            BibleHtmlNode.Bold::class,
            BibleHtmlNode.Font::class,
            BibleHtmlNode.Unknown::class
        )
    }
}
