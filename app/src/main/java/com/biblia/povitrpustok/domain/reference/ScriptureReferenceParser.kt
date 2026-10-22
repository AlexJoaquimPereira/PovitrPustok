package com.biblia.povitrpustok.domain.reference

import com.biblia.povitrpustok.domain.model.ScriptureReference
import com.biblia.povitrpustok.domain.model.ScriptureSegment

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
        // REF-03: lectionary feeds often use en/em/minus dashes for ranges.
        val normalizedDashes = rawInput
            .replace('\u2010', '-') // hyphen
            .replace('\u2011', '-') // non-breaking hyphen
            .replace('\u2013', '-') // en dash
            .replace('\u2014', '-') // em dash
            .replace('\u2212', '-') // minus sign
        val collapsed = WHITESPACE.replace(normalizedDashes.trim(), " ").trim()
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

    private val BARE_CHAPTER_RANGE = Regex("""^\d+-\d+[A-Za-z]*$""")

    /** Books whose whole text is one chapter (bare ranges are verses there). */
    private val SINGLE_CHAPTER_BOOKS = setOf("Obadia", "filemonak", "juanv2", "juanv3", "judas")

    private fun parseLocator(book: String?, locator: String): Result {
        // Semicolons are another enumeration separator in lectionary feeds.
        // Groups can change chapter: "John 3:16, 4:2" is two segments.
        val normalized = AND_WORD.replace(locator, ",").replace(';', ',')
        val groups = normalized.split(',').map { it.trim() }
        if (groups.isEmpty() || groups.any { it.isEmpty() }) {
            return Result.Invalid("Malformed reference: '$locator'.")
        }

        var currentChapter: Int? = null
        val segments = mutableListOf<ScriptureSegment>()

        for (group in groups) {
            // "John 3:16-4:2" means from 3:16 through 4:2, including the
            // remainder of chapter 3. Keep open ends in the segments; the
            // reader fills them with the chapter's first/last verse.
            val crossChapter = Regex("""^(\d+):([0-9]+[A-Za-z]*)-([0-9]+):([0-9]+[A-Za-z]*)$""")
                .matchEntire(group)
            if (crossChapter != null) {
                val firstChapter = crossChapter.groupValues[1].toInt()
                val firstVerse = parseVerseToken(crossChapter.groupValues[2])
                val secondChapter = crossChapter.groupValues[3].toInt()
                val secondVerse = parseVerseToken(crossChapter.groupValues[4])
                if (firstChapter < 1 || secondChapter <= firstChapter ||
                    firstVerse == null || firstVerse < 1 || secondVerse == null || secondVerse < 1
                ) {
                    return Result.Invalid("Malformed cross-chapter range: '$group'.")
                }
                currentChapter = secondChapter
                segments += ScriptureSegment(firstChapter, firstVerse, null, openEnd = true)
                // A range spanning more than two chapters includes the whole
                // intermediate chapters; they must never be silently skipped.
                for (chapter in (firstChapter + 1) until secondChapter) {
                    segments += ScriptureSegment(chapter, null, null)
                }
                segments += ScriptureSegment(secondChapter, null, secondVerse)
                continue
            }

            val colonParts = group.split(':')
            if (colonParts.size > 2) {
                return Result.Invalid("Malformed reference: '$group'.")
            }

            if (colonParts.size == 2) {
                val chapter = colonParts[0].toIntOrNull()
                    ?: return Result.Invalid("Chapter is not a number: '${colonParts[0]}'.")
                if (chapter < 1) {
                    return Result.Invalid("Chapter must be at least 1: $chapter.")
                }
                currentChapter = chapter
                val (start, end) = parseVerseGroup(colonParts[1])
                    ?: return Result.Invalid("Malformed or reversed verse range: '${colonParts[1]}'.")
                segments += ScriptureSegment(chapter, start, end)
            } else {
                if (currentChapter == null) {
                    val chapterOnly = group.toIntOrNull()
                    if (chapterOnly != null) {
                        if (chapterOnly < 1) {
                            return Result.Invalid("Chapter must be at least 1: $chapterOnly.")
                        }
                        currentChapter = chapterOnly
                        segments += ScriptureSegment(chapterOnly, null, null)
                        continue
                    }
                    if (BARE_CHAPTER_RANGE.matches(group)) {
                        // "Philemon 9-10" is a verse range in its only
                        // chapter, but "John 1-3" is a chapter range that
                        // must not silently become chapter 1, verses 1-3.
                        val knownBook = book?.let { BookNameMatcher.match(it) }?.id
                        if (knownBook != null && knownBook !in SINGLE_CHAPTER_BOOKS) {
                            return Result.Invalid("Chapter ranges are not supported: '$group'.")
                        }
                    }
                    // Bare verse enumeration for a single-chapter book.
                    currentChapter = 1
                }
                val (start, end) = parseVerseGroup(group)
                    ?: return Result.Invalid("Malformed or reversed verse range: '$group'.")
                segments += ScriptureSegment(currentChapter, start, end)
            }
        }

        for (i in 1 until segments.size) {
            val previous = segments[i - 1]
            val current = segments[i]
            val previousEnd = previous.verseEnd ?: previous.verseStart ?: 0
            val currentStart = current.verseStart ?: 0
            val outOfOrder = current.chapter < previous.chapter ||
                (current.chapter == previous.chapter && currentStart < previousEnd)
            if (outOfOrder) {
                return Result.Invalid("Reversed verse enumeration: '$locator'.")
            }
        }

        val first = segments.first()
        return Result.Valid(
            ScriptureReference(
                book = book,
                chapter = first.chapter,
                verseStart = first.verseStart,
                verseEnd = segments.last().verseEnd,
                // Keep the compatibility fields alone for single-group
                // references; only multi-segment references need the list.
                segments = if (segments.size > 1) segments else emptyList()
            )
        )
    }

    /** Parses one "n" or "n-m" group; returns null for a malformed token. */
    private fun parseVerseGroup(raw: String): Pair<Int, Int?>? {
        val dashParts = raw.trim().split('-')
        if (dashParts.size > 2) return null
        val start = parseVerseToken(dashParts[0]) ?: return null
        if (start < 1) return null
        val end = if (dashParts.size == 2) {
            val parsedEnd = parseVerseToken(dashParts[1]) ?: return null
            if (parsedEnd < 1 || parsedEnd < start) return null
            parsedEnd
        } else {
            start
        }
        return start to end
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
