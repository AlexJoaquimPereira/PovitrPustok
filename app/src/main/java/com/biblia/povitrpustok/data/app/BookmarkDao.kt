package com.biblia.povitrpustok.data.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    /** Returns the new row id, or -1 when the same anchor already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "DELETE FROM bookmarks WHERE bookId = :bookId AND chapterPosition = :chapterPosition " +
            "AND verseStart = :verseStart AND verseEnd = :verseEnd " +
            "AND startOffset = :startOffset AND endOffset = :endOffset"
    )
    suspend fun deleteByAnchor(
        bookId: String,
        chapterPosition: Int,
        verseStart: Int,
        verseEnd: Int,
        startOffset: Int,
        endOffset: Int
    )

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterPosition = :chapterPosition " +
            "ORDER BY verseStart ASC, verseEnd ASC"
    )
    fun observeForChapter(bookId: String, chapterPosition: Int): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM bookmarks WHERE bookId = :bookId " +
            "AND chapterPosition = :chapterPosition AND verseStart = :verseStart " +
            "AND verseEnd = :verseEnd AND startOffset = :startOffset AND endOffset = :endOffset)"
    )
    suspend fun isBookmarked(
        bookId: String,
        chapterPosition: Int,
        verseStart: Int,
        verseEnd: Int,
        startOffset: Int,
        endOffset: Int
    ): Boolean
}
