package com.biblia.povitrpustok.domain.model

/**
 * One verse record (implementation plan §13, §8, §9).
 *
 * [html] is the canonical formatted content — never stripped or
 * normalized; it is the rendering source. [plainText] is the search
 * representation only and must never be used for display.
 *
 * [verseStart]/[verseEnd] encode source verse ranges (equal for a plain
 * verse, start < end for a range); ranges are never split artificially.
 */
data class Verse(
    val id: Long,
    val chapterId: Long,
    val chapterNumber: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val html: String,
    val plainText: String
)
