package com.biblia.povitrpustok.data.app

import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
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

    override fun observeBookmarksForChapter(bookId: String, chapterId: Long): Flow<List<Bookmark>> =
        bookmarkDao.observeForChapter(bookId, chapterId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun isBookmarked(bookId: String, chapterId: Long, verseId: Long): Boolean =
        withContext(Dispatchers.IO) {
            bookmarkDao.isBookmarked(bookId, chapterId, verseId)
        }

    override suspend fun addBookmark(
        bookId: String,
        chapterId: Long,
        chapterPosition: Int,
        chapterNumber: Int,
        verseId: Long,
        verseStart: Int,
        verseEnd: Int
    ): Bookmark? = withContext(Dispatchers.IO) {
        val createdAt = System.currentTimeMillis()
        val rowId = bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                chapterId = chapterId,
                chapterPosition = chapterPosition,
                verseId = verseId,
                chapterNumber = chapterNumber,
                verseStart = verseStart,
                verseEnd = verseEnd,
                createdAt = createdAt
            )
        )
        if (rowId == -1L) {
            null
        } else {
            Bookmark(
                id = rowId,
                bookId = bookId,
                chapterId = chapterId,
                chapterPosition = chapterPosition,
                verseId = verseId,
                chapterNumber = chapterNumber,
                verseStart = verseStart,
                verseEnd = verseEnd,
                createdAt = createdAt
            )
        }
    }

    override suspend fun removeBookmark(bookId: String, chapterId: Long, verseId: Long) =
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteByLocation(bookId, chapterId, verseId)
        }

    override suspend fun removeBookmarkById(id: Long) =
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

    private fun BookmarkEntity.toDomain(): Bookmark =
        Bookmark(
            id = id,
            bookId = bookId,
            chapterId = chapterId,
            chapterPosition = chapterPosition,
            verseId = verseId,
            chapterNumber = chapterNumber,
            verseStart = verseStart,
            verseEnd = verseEnd,
            createdAt = createdAt
        )

    private fun ReadingStateEntity.toDomain(): ReadingState =
        ReadingState(
            bookId = bookId,
            chapterPosition = chapterPosition,
            updatedAt = updatedAt
        )
}
