package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow

/**
 * Bookmark + reading-state repository (implementation plan §6, §19).
 *
 * Exposes domain models only — Room entities never leave the data layer.
 * Bookmark operations touch only the mutable application database and
 * never the immutable Bible databases.
 */
interface BookmarkRepository {

    fun observeBookmarks(): Flow<List<Bookmark>>

    /** Bookmarks within one chapter — the chapter-scoped query the Reader uses. */
    fun observeBookmarksForChapter(bookId: String, chapterId: Long): Flow<List<Bookmark>>

    suspend fun isBookmarked(bookId: String, chapterId: Long, verseId: Long): Boolean

    /**
     * Adds a bookmark for the exact location. Adding the same location
     * again is a no-op (returns null) — duplicates are not created.
     */
    suspend fun addBookmark(
        bookId: String,
        chapterId: Long,
        chapterPosition: Int,
        chapterNumber: Int,
        verseId: Long,
        verseStart: Int,
        verseEnd: Int
    ): Bookmark?

    suspend fun removeBookmark(bookId: String, chapterId: Long, verseId: Long)

    suspend fun removeBookmarkById(id: Long)

    fun observeReadingState(): Flow<ReadingState?>

    suspend fun updateReadingState(bookId: String, chapterPosition: Int)

    suspend fun clearReadingState()
}
