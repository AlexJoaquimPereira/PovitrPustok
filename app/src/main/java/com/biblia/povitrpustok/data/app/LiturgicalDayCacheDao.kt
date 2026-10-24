package com.biblia.povitrpustok.data.app

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import java.time.LocalDate

/**
 * P2-2: how many days of cached days are kept behind the day being written.
 * Bounds the cache without a production "clear everything" caller.
 */
private const val RETENTION_DAYS = 30L

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

    /**
     * Atomically replaces the cached day and its readings (LITURGY-01).
     *
     * The day upsert (REPLACE) and the reading delete/insert run in one
     * transaction, so a crash/process death can never expose a cached day
     * with missing or mixed readings. Reading rows are re-pointed at the
     * (possibly new) day id returned by the upsert.
     *
     * P2-2: the same transaction then prunes every day older than
     * [RETENTION_DAYS] before [day]'s date, so the cache is bounded without
     * a read-path side effect. `liturgical_reading.day_id` declares
     * `ON DELETE CASCADE`, so deleting the day rows removes their readings
     * too.
     */
    @Transaction
    suspend fun replaceDay(
        day: LiturgicalDayCacheEntity,
        readings: List<LiturgicalReadingEntity>
    ): Long {
        val dayId = upsertDay(day)
        deleteReadings(dayId)
        insertReadings(readings.map { it.copy(dayId = dayId) })
        deleteDaysBefore(LocalDate.parse(day.date).minusDays(RETENTION_DAYS).toString())
        return dayId
    }

    /** P2-2: deletes day rows strictly older than the ISO `yyyy-MM-dd` [cutoff]. */
    @Query("DELETE FROM liturgical_day_cache WHERE date < :cutoff")
    suspend fun deleteDaysBefore(cutoff: String)

    @Query("DELETE FROM liturgical_day_cache")
    suspend fun clearAll()
}
