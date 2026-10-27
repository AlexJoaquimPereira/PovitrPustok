package com.biblia.povitrpustok.data.app

import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.model.VerseAnchor
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [BookmarkRepository] over the mutable [AppDatabase]
 * (implementation plan §6, §15, §18). Room entities are mapped to domain
 * models here and never exposed further; database work runs off the main
 * thread.
 */
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : BookmarkRepository {

    private val bookmarkDao = database.bookmarkDao()
    private val readingStateDao = database.readingStateDao()

    override fun observeBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeBookmarksForChapter(
        bookId: String,
        chapterPosition: Int
    ): Flow<List<Bookmark>> =
        bookmarkDao.observeForChapter(bookId, chapterPosition)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun isBookmarked(anchor: VerseAnchor): Boolean =
        withContext(Dispatchers.IO) {
            bookmarkDao.isBookmarked(
                bookId = anchor.bookId,
                chapterPosition = anchor.chapterPosition,
                verseStart = anchor.verseStart,
                verseEnd = anchor.verseEnd,
                startOffset = anchor.startOffset,
                endOffset = anchor.endOffset
            )
        }

    override suspend fun addBookmark(
        anchor: VerseAnchor,
        chapterNumber: Int
    ): Bookmark? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            anchor = anchor,
            chapterNumber = chapterNumber,
            createdAt = now,
            updatedAt = now
        )
        val rowId = bookmarkDao.insert(bookmark.toEntity())
        if (rowId == -1L) null else bookmark
    }

    override suspend fun removeBookmark(anchor: VerseAnchor) =
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteByAnchor(
                bookId = anchor.bookId,
                chapterPosition = anchor.chapterPosition,
                verseStart = anchor.verseStart,
                verseEnd = anchor.verseEnd,
                startOffset = anchor.startOffset,
                endOffset = anchor.endOffset
            )
        }

    override suspend fun removeBookmarkById(id: String) =
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteById(id)
        }

    override fun observeReadingState(): Flow<ReadingState?> =
        readingStateDao.observe().map { entity -> entity?.toDomain() }

    override suspend fun updateReadingState(bookId: String, chapterPosition: Int) =
        withContext(Dispatchers.IO) {
            readingStateDao.upsert(
                ReadingStateEntity(
                    bookId = bookId,
                    chapterPosition = chapterPosition,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

    override suspend fun clearReadingState() =
        withContext(Dispatchers.IO) {
            readingStateDao.clear()
        }

    private fun Bookmark.toEntity(): BookmarkEntity =
        BookmarkEntity(
            id = id,
            bookId = anchor.bookId,
            chapterPosition = anchor.chapterPosition,
            verseStart = anchor.verseStart,
            verseEnd = anchor.verseEnd,
            startOffset = anchor.startOffset,
            endOffset = anchor.endOffset,
            chapterNumber = chapterNumber,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt
        )

    private fun BookmarkEntity.toDomain(): Bookmark =
        Bookmark(
            id = id,
            anchor = VerseAnchor(
                bookId = bookId,
                chapterPosition = chapterPosition,
                verseStart = verseStart,
                verseEnd = verseEnd,
                startOffset = startOffset,
                endOffset = endOffset
            ),
            chapterNumber = chapterNumber,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt
        )

    private fun ReadingStateEntity.toDomain(): ReadingState =
        ReadingState(
            bookId = bookId,
            chapterPosition = chapterPosition,
            updatedAt = updatedAt
        )
}
