package com.biblia.povitrpustok.data.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached liturgical day (corrective phase Issue 10).
 *
 * One row per local calendar date (unique) — a request for today never
 * returns yesterday's row. Only domain data is stored (no Retrofit DTOs);
 * readings live in the normalized [LiturgicalReadingEntity] rows.
 */
@Entity(
    tableName = "liturgical_day_cache",
    indices = [Index(value = ["date"], unique = true)]
)
data class LiturgicalDayCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Local calendar date as ISO `yyyy-MM-dd`; the cache key. */
    val date: String,
    val season: String?,
    @ColumnInfo(name = "celebrationName")
    val celebrationName: String?,
    @ColumnInfo(name = "celebrationType")
    val celebrationType: String?,
    @ColumnInfo(name = "celebrationDescription")
    val celebrationDescription: String?,
    @ColumnInfo(name = "celebrationQuote")
    val celebrationQuote: String?,
    @ColumnInfo(name = "fetchedAt")
    val fetchedAt: Long
)
