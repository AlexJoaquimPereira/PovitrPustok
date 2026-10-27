package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.model.VerseAnchor
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import java.util.UUID
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

    override fun observeBookmarksForChapter(
        bookId: String,
        chapterPosition: Int
    ): Flow<List<Bookmark>> =
        _bookmarks.map { bookmarks ->
            bookmarks.filter {
                it.anchor.bookId == bookId && it.anchor.chapterPosition == chapterPosition
            }
        }

    override suspend fun isBookmarked(anchor: VerseAnchor): Boolean =
        _bookmarks.value.any { it.anchor == anchor }

    override suspend fun addBookmark(anchor: VerseAnchor, chapterNumber: Int): Bookmark? {
        if (_bookmarks.value.any { it.anchor == anchor }) return null
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            anchor = anchor,
            chapterNumber = chapterNumber,
            createdAt = System.currentTimeMillis()
        )
        _bookmarks.value = _bookmarks.value + bookmark
        return bookmark
    }

    override suspend fun removeBookmark(anchor: VerseAnchor) {
        _bookmarks.value = _bookmarks.value.filterNot { it.anchor == anchor }
    }

    override suspend fun removeBookmarkById(id: String) {
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
