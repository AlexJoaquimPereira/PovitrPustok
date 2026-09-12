package com.biblia.povitrpustok.data.app

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted bookmark (implementation plan §28, §3–§5).
 *
 * Stores references to immutable Bible content, never copies of it: no
 * verse text, HTML, or parsed documents. Identity is the stable internal
 * location (bookId + chapterId + verseId), enforced by a unique index —
 * the same exact verse cannot create unlimited duplicates. The
 * Job duplicate-chapter case stays unambiguous because each source
 * chapter record has a distinct [chapterId].
 *
 * [chapterNumber], [verseStart] and [verseEnd] are display-convenience
 * copies of source values captured at creation time; they are never used
 * for identity or uniqueness.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId", "chapterId", "verseId"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterId: Long,
    val chapterPosition: Int,
    val verseId: Long,
    val chapterNumber: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val createdAt: Long
)
