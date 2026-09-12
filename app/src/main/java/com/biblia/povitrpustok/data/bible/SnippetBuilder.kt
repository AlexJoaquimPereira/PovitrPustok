package com.biblia.povitrpustok.data.bible

import java.text.Normalizer
import java.util.Locale

/**
 * Portable plain-text result snippet (corrective phase Issue 1).
 *
 * Replaces the FTS5 `snippet()` SQL function: `verses_fts` is an
 * external-content table whose single FTS column (`text_plain`) does not
 * mirror the `verses` content-table schema, and FTS5 auxiliary functions
 * over such mismatched external-content tables are not portable across
 * SQLite builds (the device-side "SELECT query fails" source). The FTS5
 * MATCH itself stays in SQL; only the context window is computed here,
 * from the already-fetched `text_plain` value — no HTML, no extra query.
 *
 * Matching mirrors FTS5 unicode61 semantics: case-insensitive and
 * diacritic-insensitive (remove_diacritics), so accented and unaccented
 * queries locate the same window.
 */
object SnippetBuilder {

    /**
     * @param plainText   the verse's `text_plain` content (may be empty).
     * @param ftsExpression the FTS5 MATCH expression the search used
     *   (e.g. `"subhagi" "modem"`); its quoted terms locate the window.
     * @param maxLength   maximum snippet length in characters.
     */
    fun build(plainText: String, ftsExpression: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        val text = plainText.trim()
        if (text.isEmpty()) return ""
        val terms = termsFrom(ftsExpression)
        if (terms.isEmpty()) return abbreviate(text, maxLength)
        val matchStart = findFirstMatch(text, terms) ?: return abbreviate(text, maxLength)
        return window(text, matchStart, maxLength)
    }

    /** The words inside the quoted phrases of a parser-produced expression. */
    internal fun termsFrom(expression: String): List<String> =
        expression
            .split('"')
            .filter { it.isNotBlank() }
            .flatMap { it.split(WHITESPACE) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Earliest match start (original-text index) of any term. */
    internal fun findFirstMatch(text: String, terms: List<String>): Int? {
        var best: Int? = null
        for (term in terms) {
            val foldedTerm = fold(term)
            if (foldedTerm.isEmpty()) continue
            val index = indexOfFold(text, foldedTerm) ?: continue
            if (best == null || index < best) best = index
        }
        return best
    }

    /** Case/diacritic-insensitive indexOf returning the ORIGINAL text index. */
    internal fun indexOfFold(text: String, foldedTerm: String): Int? {
        var start = 0
        while (start <= text.length) {
            var cursor = start
            val foldedWindow = StringBuilder()
            while (foldedWindow.length < foldedTerm.length && cursor < text.length) {
                foldedWindow.append(fold(text[cursor].toString()))
                cursor++
            }
            if (foldedWindow.toString() == foldedTerm) return start
            start++
        }
        return null
    }

    /** Lowercase + strip combining marks (mirrors FTS5 remove_diacritics). */
    internal fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)

    /** A ~[maxLength]-char window around the match, broken at word boundaries. */
    internal fun window(text: String, matchStart: Int, maxLength: Int): String {
        if (text.length <= maxLength) return text
        val half = maxLength / 2
        var start = (matchStart - half).coerceAtLeast(0)
        var end = (start + maxLength).coerceAtMost(text.length)
        if (start > 0) {
            val space = text.indexOf(' ', start)
            if (space in start until end) start = space + 1
        }
        if (end < text.length) {
            val space = text.lastIndexOf(' ', end)
            if (space > start) end = space
        }
        val prefix = if (start > 0) ELLIPSIS else ""
        val suffix = if (end < text.length) ELLIPSIS else ""
        val body = text.substring(start, end).replace(WHITESPACE, " ").trim()
        return prefix + body + suffix
    }

    private fun abbreviate(text: String, maxLength: Int): String =
        if (text.length <= maxLength) {
            text
        } else {
            text.take(maxLength - ELLIPSIS.length).trimEnd() + ELLIPSIS
        }

    private const val DEFAULT_MAX_LENGTH = 120
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")
    private val COMBINING_MARKS = Regex("\\p{M}+")
}
