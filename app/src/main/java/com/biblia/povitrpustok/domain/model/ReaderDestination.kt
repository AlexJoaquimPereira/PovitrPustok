package com.biblia.povitrpustok.domain.model

/**
 * Chapter-navigation destination for the existing reader route
 * `reader/{bookId}/{chapterPosition}` (implementation plan §14;
 * corrective phase Issue 5).
 *
 * [chapterPosition] is the navigation identity; [verseId] is an optional
 * verse target kept separate from chapter navigation. [verseStart]/
 * [verseEnd] are an optional *requested passage*: when present, the Reader
 * displays only that verse range (targeted passage mode) instead of the
 * whole chapter. Nothing large is ever carried through navigation.
 */
data class ReaderDestination(
    val bookId: String,
    val chapterPosition: Int,
    val verseId: Long? = null,
    val verseStart: Int? = null,
    val verseEnd: Int? = null
)
