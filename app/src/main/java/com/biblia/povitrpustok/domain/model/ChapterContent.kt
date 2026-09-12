package com.biblia.povitrpustok.domain.model

/**
 * The complete content of one chapter (implementation plan §14):
 * the chapter itself plus its headings and verses in source order.
 */
data class ChapterContent(
    val chapter: Chapter,
    val headings: List<Heading>,
    val verses: List<Verse>
)
