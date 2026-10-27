package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.model.VerseAnchor
import kotlinx.coroutines.flow.Flow

/**
 * Bookmark + reading-state repository (implementation plan §6, §19).
 *
 * Bookmark identity is the stable [VerseAnchor], never a corpus row id.
 * Reading state already uses the stable book id + chapter position.
 */
interface BookmarkRepository {

    fun observeBookmarks(): Flow<List<Bookmark>>

    /** Bookmarks within one chapter position — the chapter-scoped Reader query. */
    fun observeBookmarksForChapter(bookId: String, chapterPosition: Int): Flow<List<Bookmark>>

    suspend fun isBookmarked(anchor: VerseAnchor): Boolean

    /**
     * Adds a bookmark for the exact anchor. Adding the same anchor again is
     * a no-op (returns null) — duplicates are not created.
     */
    suspend fun addBookmark(anchor: VerseAnchor, chapterNumber: Int): Bookmark?

    suspend fun removeBookmark(anchor: VerseAnchor)

    suspend fun removeBookmarkById(id: String)

    fun observeReadingState(): Flow<ReadingState?>

    suspend fun updateReadingState(bookId: String, chapterPosition: Int)

    suspend fun clearReadingState()
}
