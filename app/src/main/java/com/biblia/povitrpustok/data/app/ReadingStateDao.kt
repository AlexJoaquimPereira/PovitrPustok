package com.biblia.povitrpustok.data.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStateDao {

    @Query("SELECT * FROM reading_state WHERE id = 1")
    fun observe(): Flow<ReadingStateEntity?>

    @Query("SELECT * FROM reading_state WHERE id = 1")
    suspend fun get(): ReadingStateEntity?

    /** Upserts the single reading-state row (REPLACE keeps id = 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReadingStateEntity)

    @Query("DELETE FROM reading_state WHERE id = 1")
    suspend fun clear()
}
