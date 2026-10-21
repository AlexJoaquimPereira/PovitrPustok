package com.biblia.povitrpustok.domain.model

/** One comma/semicolon-separated portion of a Scripture reference. */
data class ScriptureSegment(
    val chapter: Int,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
    /** True for the first half of "3:16-4:2": continue to chapter end. */
    val openEnd: Boolean = false
)
