package com.biblia.povitrpustok.domain.model

/**
 * One chapter heading/title record (implementation plan §13).
 *
 * A chapter may contain zero, one, or many headings with different
 * formatting; each heading is an independent record ordered by [position].
 * [beforeVerse] is the approximate verse boundary before which the
 * heading renders. [html] is the canonical source HTML — never stripped.
 */
data class Heading(
    val id: Long,
    val chapterId: Long,
    val position: Int,
    val beforeVerse: Int,
    val html: String
)
