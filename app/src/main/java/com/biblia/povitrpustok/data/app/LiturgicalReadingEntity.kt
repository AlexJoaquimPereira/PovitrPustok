package com.biblia.povitrpustok.data.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One normalized reading row of a cached liturgical day (corrective
 * phase Issue 10). Stores the domain [com.biblia.povitrpustok.domain.model.LiturgicalReading]
 * values (type as its stable name), never DTO/HTTP types.
 */
@Entity(
    tableName = "liturgical_reading",
    foreignKeys = [
        ForeignKey(
            entity = LiturgicalDayCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["day_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("day_id")]
)
data class LiturgicalReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "day_id")
    val dayId: Long,
    /** [com.biblia.povitrpustok.domain.model.ReadingType.name] */
    val type: String,
    val reference: String,
    @ColumnInfo(name = "display_label")
    val displayLabel: String?,
    /** Source order within the day's reading list. */
    val position: Int
)
