package com.biblia.povitrpustok.data.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimum reading state: where the user was last reading
 * (implementation plan §12–§13).
 *
 * A single row (fixed [id] = 1) holding the last opened book and chapter
 * position. This is reading state, not history: no list of past locations
 * is kept.
 */
@Entity(tableName = "reading_state")
data class ReadingStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val bookId: String,
    val chapterPosition: Int,
    val updatedAt: Long
)
