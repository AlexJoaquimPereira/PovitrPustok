package com.biblia.povitrpustok.data.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    /** Returns the new row id, or -1 when the same location already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND chapterId = :chapterId AND verseId = :verseId")
    suspend fun deleteByLocation(bookId: String, chapterId: Long, verseId: Long)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterId = :chapterId ORDER BY verseStart ASC")
    fun observeForChapter(bookId: String, chapterId: Long): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM bookmarks WHERE bookId = :bookId AND chapterId = :chapterId AND verseId = :verseId)"
    )
    suspend fun isBookmarked(bookId: String, chapterId: Long, verseId: Long): Boolean
}
