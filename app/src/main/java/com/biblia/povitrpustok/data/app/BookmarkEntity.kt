package com.biblia.povitrpustok.data.app

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted bookmark (implementation plan §28, §3–§5).
 *
 * [id] is an app-owned UUID; the stable content identity is the anchor
 * columns (book id + chapter position + source verse range + offsets),
 * enforced by a unique index. No corpus row ids are stored: they change
 * when the prepared assets are regenerated (BOOKMARK-01).
 *
 * [chapterNumber] is a display copy captured at creation time; [updatedAt]
 * and [deletedAt] support future export/merge and tombstones.
 */
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(
            name = "index_bookmarks_anchor",
            value = [
                "bookId",
                "chapterPosition",
                "verseStart",
                "verseEnd",
                "startOffset",
                "endOffset"
            ],
            unique = true
        )
    ]
)
data class BookmarkEntity(
    @PrimaryKey
    val id: String,
    val bookId: String,
    val chapterPosition: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val startOffset: Int,
    val endOffset: Int,
    val chapterNumber: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
