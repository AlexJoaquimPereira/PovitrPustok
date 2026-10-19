package com.biblia.povitrpustok.domain.model

/**
 * Result of resolving a Scripture reference (implementation plan §16, §21).
 *
 * Normal user-input conditions (unknown book/chapter/verse, ambiguous
 * chapter, malformed input) are represented here, never thrown. Only
 * actual database failures are exceptions.
 */
sealed interface ScriptureResolution {

    /** Unique chapter found; [reference] carries the stable location. */
    data class Resolved(
        val reference: ResolvedScriptureReference
    ) : ScriptureResolution

    /**
     * Multiple chapter records share the requested chapter.number.
     * [candidates] lists every matching [Chapter] so callers can
     * disambiguate — the resolver never silently picks one (Job is the
     * mandatory regression case).
     */
    data class Ambiguous(
        val bookId: String,
        val chapterNumber: Int,
        val candidates: List<Chapter>,
        /** REF-04: requested verse/range, preserved through chapter choice. */
        val verseStart: Int? = null,
        val verseEnd: Int? = null
    ) : ScriptureResolution

    /**
     * Multiple source records cover the requested verse number (duplicate
     * source verse numbering, e.g. Esther's Hebrew and Greek-addition
     * blocks). [candidates] lists every covering record so callers can
     * disambiguate — never a silent arbitrary pick.
     */
    data class AmbiguousVerse(
        val bookId: String,
        val chapterId: Long,
        val chapterNumber: Int,
        val chapterPosition: Int,
        val verseNumber: Int,
        val candidates: List<Verse>
    ) : ScriptureResolution

    /** Well-formed reference that does not match the corpus. */
    data class NotFound(
        val reason: String
    ) : ScriptureResolution

    /** Malformed or unsupported reference (empty, reversed range, etc.). */
    data class Invalid(
        val reason: String
    ) : ScriptureResolution
}

/**
 * REF-04: destination for a chosen ambiguous chapter, preserving the
 * originally requested verse/range as a passage target.
 */
fun ScriptureResolution.Ambiguous.toReaderDestination(chapter: Chapter): ReaderDestination =
    ReaderDestination(
        bookId = bookId,
        chapterPosition = chapter.position,
        verseStart = verseStart,
        verseEnd = verseEnd
    )
