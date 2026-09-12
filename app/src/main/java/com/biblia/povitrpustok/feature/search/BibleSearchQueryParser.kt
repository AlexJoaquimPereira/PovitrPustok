package com.biblia.povitrpustok.feature.search

import javax.inject.Inject

/**
 * Prepares raw user input for FTS5 MATCH (implementation plan §24).
 *
 * Exact transformation:
 *  1. trim surrounding whitespace;
 *  2. collapse repeated whitespace to single spaces;
 *  3. blank input yields `null` — the caller must not run a database
 *     search (empty queries must never become `MATCH *`);
 *  4. split into whitespace terms; each term is stripped of any embedded
 *     double quotes (they would break the FTS5 expression) and wrapped as
 *     a double-quoted FTS5 phrase; terms are joined with a space, which
 *     FTS5 interprets as AND.
 *
 * This makes malformed FTS syntax from user input impossible: stray
 * quotes, `*`, `:`, parentheses, `^` and `-` lose their special meaning
 * inside phrase quotes and cannot crash the application. Ordinary words,
 * phrases, and multi-term queries keep their natural meaning; `James 1:5`
 * is deliberately treated as an ordinary multi-term query (no reference
 * parsing in this phase).
 *
 * No custom Unicode normalization: the corpus FTS5 build uses unicode61
 * with remove_diacritics, so accented and unaccented terms already match
 * interchangeably (verified against the real databases).
 */
class BibleSearchQueryParser @Inject constructor() {

    /**
     * @return a safe FTS5 expression, or null for a blank/unsafe query.
     */
    fun parse(rawQuery: String): String? {
        val collapsed = WHITESPACE_REGEX.replace(rawQuery.trim(), " ").trim()
        if (collapsed.isEmpty()) return null

        val terms = collapsed.split(' ').mapNotNull { term ->
            val cleaned = term.replace("\"", "").trim()
            if (cleaned.isEmpty()) null else "\"$cleaned\""
        }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ")
    }

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
