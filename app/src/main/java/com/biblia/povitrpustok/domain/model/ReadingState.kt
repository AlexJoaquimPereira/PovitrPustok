package com.biblia.povitrpustok.domain.model

/**
 * Minimum reading state (implementation plan §12–§13): the last opened
 * book and chapter position. Not a history log.
 */
data class ReadingState(
    val bookId: String,
    val chapterPosition: Int,
    val updatedAt: Long
)
