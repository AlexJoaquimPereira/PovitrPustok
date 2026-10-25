package com.biblia.povitrpustok.data.liturgy

import com.biblia.povitrpustok.data.app.LiturgicalDayCacheEntity
import com.biblia.povitrpustok.data.app.LiturgicalDayCacheWithReadings
import com.biblia.povitrpustok.data.app.LiturgicalReadingEntity
import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReadingType
import java.time.LocalDate

/**
 * Maps between the domain [LiturgicalDay] and the Room cache entities
 * (corrective phase Issue 10). Only domain values are stored — never
 * Retrofit DTOs. Reading order is preserved via the position column.
 */
object LiturgicalCacheMapper {

    fun toDayEntity(day: LiturgicalDay, fetchedAt: Long): LiturgicalDayCacheEntity =
        LiturgicalDayCacheEntity(
            date = day.date.toString(),
            season = day.season,
            celebrationName = day.celebrationName,
            celebrationType = day.celebrationType,
            celebrationDescription = day.celebrationDescription,
            celebrationQuote = day.celebrationQuote,
            fetchedAt = fetchedAt
        )

    fun toReadingEntities(dayId: Long, readings: List<LiturgicalReading>): List<LiturgicalReadingEntity> =
        readings.mapIndexed { index, reading ->
            LiturgicalReadingEntity(
                dayId = dayId,
                type = reading.type.name,
                reference = reading.reference,
                displayLabel = reading.displayLabel,
                position = index
            )
        }

    fun toDomain(cached: LiturgicalDayCacheWithReadings): LiturgicalDay {
        val day = cached.day
        return GoaCalendarOverrides.apply(LiturgicalDay(
            date = LocalDate.parse(day.date),
            season = day.season,
            celebrationName = day.celebrationName,
            celebrationType = day.celebrationType,
            celebrationDescription = day.celebrationDescription,
            celebrationQuote = day.celebrationQuote,
            readings = cached.readings
                .sortedBy { it.position }
                .map { reading ->
                    LiturgicalReading(
                        type = readingTypeOrOther(reading.type),
                        reference = reading.reference,
                        displayLabel = reading.displayLabel
                    )
                }
        ))
    }

    /** Unknown stored type names fall back to OTHER (forward-compatible). */
    private fun readingTypeOrOther(name: String): ReadingType =
        runCatching { ReadingType.valueOf(name) }.getOrDefault(ReadingType.OTHER)
}
