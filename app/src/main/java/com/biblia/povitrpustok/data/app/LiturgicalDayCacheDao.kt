package com.biblia.povitrpustok.data.app

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

/** A cached liturgical day with its normalized reading rows. */
data class LiturgicalDayCacheWithReadings(
    @Embedded val day: LiturgicalDayCacheEntity,
    @Relation(parentColumn = "id", entityColumn = "day_id")
    val readings: List<LiturgicalReadingEntity>
)

/**
 * Date-keyed liturgical cache (corrective phase Issue 10). The `date`
 * column is UNIQUE, so a row for today can never be satisfied by another
 * date's entry.
 */
@Dao
interface LiturgicalDayCacheDao {

    @Transaction
    @Query("SELECT * FROM liturgical_day_cache WHERE date = :date")
    suspend fun getByDateWithReadings(date: String): LiturgicalDayCacheWithReadings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: LiturgicalDayCacheEntity): Long

    @Query("DELETE FROM liturgical_reading WHERE day_id = :dayId")
    suspend fun deleteReadings(dayId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<LiturgicalReadingEntity>)

    @Query("DELETE FROM liturgical_day_cache")
    suspend fun clearAll()
}
