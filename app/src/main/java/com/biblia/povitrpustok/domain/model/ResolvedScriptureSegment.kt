package com.biblia.povitrpustok.domain.model

/** One resolved chapter/verse segment of a Scripture reference. */
data class ResolvedScriptureSegment(
    val bookId: String,
    val chapterId: Long,
    val chapterNumber: Int,
    val chapterPosition: Int,
    val verseId: Long? = null,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
    val openEnd: Boolean = false
)
