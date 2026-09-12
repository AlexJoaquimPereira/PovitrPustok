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
    val verseEnd: Int?
) {

    /** Whether the reference names a specific verse (or range). */
    val hasVerse: Boolean get() = verseStart != null
}
