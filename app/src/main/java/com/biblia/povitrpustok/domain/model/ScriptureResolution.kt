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
        val candidates: List<Chapter>
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
