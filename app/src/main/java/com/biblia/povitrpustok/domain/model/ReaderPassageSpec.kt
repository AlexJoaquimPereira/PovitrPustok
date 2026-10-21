package com.biblia.povitrpustok.domain.model

/**
 * One chapter segment of a requested Reader passage. A null start/end means
 * the open end of the chapter (used by cross-chapter references such as
 * "John 3:16-4:2").
 */
data class ReaderPassageSpec(
    val chapterPosition: Int,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
    val openEnd: Boolean = false
)
