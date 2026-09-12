package com.biblia.povitrpustok.data.liturgy

import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReadingType
import java.time.LocalDate

/**
 * Maps the documented API DTOs to domain models (Phase 10, plan §18).
 *
 * A reading is added only when its source field is present and meaningful;
 * empty strings are treated as absent. The raw reference string is kept
 * verbatim on the domain model — it is converted through the existing
 * ScriptureReferenceResolver only when the user navigates.
 */
object CatholicReadingsMapper {

    fun toLiturgicalDay(
        date: LocalDate,
        readingsDto: ReadingsDto,
        calendarDto: CalendarDto
    ): LiturgicalDay {
        val celebration = calendarDto.celebration
        return LiturgicalDay(
            date = date,
            season = readingsDto.season?.takeIf { it.isNotBlank() }
                ?: calendarDto.season?.takeIf { it.isNotBlank() },
            celebrationName = celebration?.name?.takeIf { it.isNotBlank() },
            celebrationType = celebration?.type?.takeIf { it.isNotBlank() },
            celebrationDescription = celebration?.description?.takeIf { it.isNotBlank() },
            celebrationQuote = celebration?.quote?.takeIf { it.isNotBlank() },
            readings = mapReadings(readingsDto.readings)
        )
    }

    private fun mapReadings(fields: ReadingsFieldsDto?): List<LiturgicalReading> {
        if (fields == null) return emptyList()
        return buildList {
            fields.firstReading?.takeIf { it.isNotBlank() }?.let {
                add(LiturgicalReading(ReadingType.FIRST_READING, it))
            }
            fields.psalm?.takeIf { it.isNotBlank() }?.let {
                add(LiturgicalReading(ReadingType.RESPONSORIAL_PSALM, it))
            }
            fields.secondReading?.takeIf { it.isNotBlank() }?.let {
                add(LiturgicalReading(ReadingType.SECOND_READING, it))
            }
            fields.gospel?.takeIf { it.isNotBlank() }?.let {
                add(LiturgicalReading(ReadingType.GOSPEL, it))
            }
        }
    }
}
