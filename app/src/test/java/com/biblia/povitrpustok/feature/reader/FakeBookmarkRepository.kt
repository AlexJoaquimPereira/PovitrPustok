package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory BookmarkRepository for reader tests where bookmark behavior is
 * not under test (targeting tests only need a non-crashing collaborator).
 */
class FakeBookmarkRepository : BookmarkRepository {

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    override fun observeBookmarks(): Flow<List<Bookmark>> = _bookmarks

    override fun observeBookmarksForChapter(bookId: String, chapterId: Long): Flow<List<Bookmark>> =
        _bookmarks.map { bookmarks ->
            bookmarks.filter { it.bookId == bookId && it.chapterId == chapterId }
        }

    override suspend fun isBookmarked(bookId: String, chapterId: Long, verseId: Long): Boolean =
        _bookmarks.value.any { it.bookId == bookId && it.chapterId == chapterId && it.verseId == verseId }

    override suspend fun addBookmark(
        bookId: String,
        chapterId: Long,
        chapterPosition: Int,
        chapterNumber: Int,
        verseId: Long,
        verseStart: Int,
        verseEnd: Int
    ): Bookmark? {
        val bookmark = Bookmark(
            id = _bookmarks.value.size + 1L,
            bookId = bookId,
            chapterId = chapterId,
            chapterPosition = chapterPosition,
            verseId = verseId,
            chapterNumber = chapterNumber,
            verseStart = verseStart,
            verseEnd = verseEnd,
            createdAt = System.currentTimeMillis()
        )
        _bookmarks.value = _bookmarks.value + bookmark
        return bookmark
    }

    override suspend fun removeBookmark(bookId: String, chapterId: Long, verseId: Long) {
        _bookmarks.value = _bookmarks.value.filterNot {
            it.bookId == bookId && it.chapterId == chapterId && it.verseId == verseId
        }
    }

    override suspend fun removeBookmarkById(id: Long) {
        _bookmarks.value = _bookmarks.value.filterNot { it.id == id }
    }

    private val _readingState = MutableStateFlow<ReadingState?>(null)

    override fun observeReadingState(): Flow<ReadingState?> = _readingState

    override suspend fun updateReadingState(bookId: String, chapterPosition: Int) {
        _readingState.value = ReadingState(bookId, chapterPosition, System.currentTimeMillis())
    }

    override suspend fun clearReadingState() {
        _readingState.value = null
    }

    /** Current persisted reading state (test helper). */
    fun readingStateValue(): ReadingState = checkNotNull(_readingState.value)
}
