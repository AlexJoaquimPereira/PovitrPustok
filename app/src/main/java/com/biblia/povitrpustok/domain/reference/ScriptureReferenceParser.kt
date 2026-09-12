package com.biblia.povitrpustok.domain.reference

import com.biblia.povitrpustok.domain.model.ScriptureReference

/**
 * Stage A: pure Scripture reference parser (implementation plan §16;
 * expanded in Phase 10 §7/§33).
 *
 * Accepts (after trimming and whitespace collapsing):
 *  - `Book Chapter`            -> chapter only (James 1)
 *  - `Book Chapter:Verse`      -> single verse (James 1:5)
 *  - `Book Chapter:Verse-Verse` -> verse range (James 1:5-8)
 *  - multi-word / digit-prefixed book names (1 Kings 3:16, 2 Chronicles 7:14,
 *    1 John 4:8, 1 Corinthians 15:20-27) — the last whitespace token is the
 *    chapter/verse locator, everything before it is the book name;
 *  - comma-separated verse enumerations (Psalm 112:1-2, 5-6, 7-8, 9) mapped
 *    to a span (first..last verse) under the existing range semantics;
 *  - bookless `Chapter[:Verse[-Verse]]` (3:16), needs book context to resolve.
 *
 * The parser knows nothing about SQLite ids, the catalog, or the corpus:
 * it only produces a structured [ScriptureReference]. Cross-chapter
 * references (e.g. "John 3:16-4:2") still parse as Invalid.
 */
object ScriptureReferenceParser {

    sealed interface Result {
        data class Valid(val reference: ScriptureReference) : Result
        data class Invalid(val reason: String) : Result
    }

    private val WHITESPACE = Regex("\\s+")
    private val AND_WORD = Regex("\\band\\b", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): Result {
        val collapsed = WHITESPACE.replace(rawInput.trim(), " ").trim()
        if (collapsed.isEmpty()) {
            return Result.Invalid("Reference is empty.")
        }

        val tokens = collapsed.split(' ')
        // The locator starts at the first token containing ':' (verse
        // enumerations may span spaces: "45:10, 11, 12, 16"). For
        // no-colon references, a token containing '-' or ',' marks the start
        // of a chapter-less verse enumeration (Philemon 9-10, 12-17);
        // otherwise the last token is a plain chapter number, which keeps
        // digit-prefixed book names ("1 Kings 1") intact.
        val colonIndex = tokens.indexOfFirst { it.contains(':') }
        val book: String?
        val locator: String
        if (colonIndex >= 0) {
            val bookTokens = tokens.take(colonIndex)
            book = if (bookTokens.isEmpty()) null else bookTokens.joinToString(" ")
            locator = tokens.drop(colonIndex).joinToString(" ")
        } else {
            val enumerationIndex = tokens.indexOfFirst { it.contains('-') || it.contains(',') }
            if (enumerationIndex >= 0) {
                val bookTokens = tokens.take(enumerationIndex)
                book = if (bookTokens.isEmpty()) null else bookTokens.joinToString(" ")
                locator = tokens.drop(enumerationIndex).joinToString(" ")
            } else if (tokens.size == 1) {
                book = null
                locator = tokens[0]
            } else {
                book = tokens.dropLast(1).joinToString(" ")
                locator = tokens.last()
            }
        }

        return parseLocator(book, locator)
    }

    private fun parseLocator(book: String?, locator: String): Result {
        val colonParts = locator.split(':')
        if (colonParts.size > 2) {
            return Result.Invalid("Malformed reference: '$locator'.")
        }

        val chapterToken = colonParts[0].trim()
        if (colonParts.size == 1) {
            // No colon: either a plain chapter number, or — for single-chapter
            // books such as Philemon — a verse enumeration ("Philemon 9-10,
            // 12-17" is verses 9-17 of chapter 1, not a chapter locator).
            val chapterOnly = chapterToken.toIntOrNull()
            if (chapterOnly != null) {
                if (chapterOnly < 1) {
                    return Result.Invalid("Chapter must be at least 1: $chapterOnly.")
                }
                return Result.Valid(
                    ScriptureReference(book = book, chapter = chapterOnly, verseStart = null, verseEnd = null)
                )
            }
            return parseVersePart(book, 1, chapterToken)
        }

        val chapter = chapterToken.toIntOrNull()
            ?: return Result.Invalid("Chapter is not a number: '$chapterToken'.")
        if (chapter < 1) {
            return Result.Invalid("Chapter must be at least 1: $chapter.")
        }

        return parseVersePart(book, chapter, colonParts[1])
    }

    private fun parseVersePart(book: String?, chapter: Int, versePartRaw: String): Result {
        // "14 and 17" is an enumeration separator equivalent to a comma.
        val versePart = AND_WORD.replace(versePartRaw, ",")
        // Groups separated by commas; each group is "n" or "n-m".
        val groups = versePart.split(',')
        if (groups.isEmpty()) {
            return Result.Invalid("Malformed verse range: '$versePartRaw'.")
        }

        var firstStart: Int? = null
        var lastEnd: Int? = null
        for (group in groups) {
            val trimmed = group.trim()
            if (trimmed.isEmpty()) {
                return Result.Invalid("Malformed verse range: '$versePartRaw'.")
            }
            val dashParts = trimmed.split('-')
            if (dashParts.size > 2) {
                return Result.Invalid("Malformed verse range: '$versePartRaw'.")
            }

            val start = parseVerseToken(dashParts[0])
                ?: return Result.Invalid("Verse is not a number: '${dashParts[0]}'.")
            if (start < 1) {
                return Result.Invalid("Verse must be at least 1: $start.")
            }

            val end = if (dashParts.size == 2) {
                val e = parseVerseToken(dashParts[1])
                    ?: return Result.Invalid("Verse range end is not a number: '${dashParts[1]}'.")
                if (e < 1) {
                    return Result.Invalid("Verse range end must be at least 1: $e.")
                }
                if (e < start) {
                    return Result.Invalid("Reversed verse range: $start-$e.")
                }
                e
            } else {
                start
            }

            if (firstStart == null) firstStart = start
            lastEnd = end
        }

        val verseStart = firstStart!!
        val verseEnd = lastEnd!!
        if (verseEnd < verseStart) {
            return Result.Invalid("Reversed verse range: $verseStart-$verseEnd.")
        }

        return Result.Valid(
            ScriptureReference(book = book, chapter = chapter, verseStart = verseStart, verseEnd = verseEnd)
        )
    }

    /**
     * Parses the leading integer of a verse token, ignoring a trailing
     * letter suffix. Verse suffixes ("18b", "13a") are liturgical markers
     * for a portion of a verse; the project's range model represents the
     * numeric verse (the raw reference string is preserved on the domain
     * reading). Returns null when the token has no leading digits.
     */
    private fun parseVerseToken(token: String): Int? {
        var index = 0
        while (index < token.length && token[index].isDigit()) index++
        if (index == 0) return null
        return token.substring(0, index).toIntOrNull()
    }
}
