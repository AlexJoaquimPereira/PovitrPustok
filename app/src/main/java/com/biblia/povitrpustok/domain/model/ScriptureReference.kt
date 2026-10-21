package com.biblia.povitrpustok.domain.model

/**
 * Stage A output: a parsed Scripture reference (implementation plan §16).
 *
 * Pure parsing result — no SQLite ids, no database knowledge. [book] is the
 * raw book token (case-preserved) or null for a bookless reference such as
 * "3:16" (which requires a book context to resolve).
 */
data class ScriptureReference(
    val book: String?,
    val chapter: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    /**
     * Every referenced portion in source order. A non-continuous reference
     * such as "Psalm 90:3-4, 5-6, 17" must never collapse into one span,
     * and a cross-chapter reference carries one segment per chapter.
     * [chapter]/[verseStart]/[verseEnd] mirror the first segment for
     * compatibility with single-segment callers.
     */
    val segments: List<ScriptureSegment> = emptyList()
) {

    /** Whether the reference names a specific verse (or range). */
    val hasVerse: Boolean get() = verseStart != null

    /** All segments, including the compatibility fields for old callers. */
    val allSegments: List<ScriptureSegment>
        get() = segments.ifEmpty {
            listOf(ScriptureSegment(chapter, verseStart, verseEnd))
        }
}
