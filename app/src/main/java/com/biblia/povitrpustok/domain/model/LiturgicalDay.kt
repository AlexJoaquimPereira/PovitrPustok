package com.biblia.povitrpustok.domain.model

import java.time.LocalDate

/**
 * A liturgical day (implementation plan §5, §19): celebration metadata
 * plus an ordered list of readings. Optional fields reflect the fact that
 * not every day has the same set of readings or celebration data.
 *
 * The API provides references only — never Bible text; the local corpus
 * remains the authoritative Bible source.
 */
data class LiturgicalDay(
    val date: LocalDate,
    val season: String?,
    val celebrationName: String?,
    val celebrationType: String?,
    val celebrationDescription: String?,
    val celebrationQuote: String?,
    val readings: List<LiturgicalReading>
)
