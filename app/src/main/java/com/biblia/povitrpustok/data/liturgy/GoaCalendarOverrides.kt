/*
 * Copyright 2026 Alex Pereira
 * SPDX-License-Identifier: Apache-2.0
 */
package com.biblia.povitrpustok.data.liturgy

import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import java.time.LocalDate

/**
 * Goan particular-calendar corrections for the Catholic Readings API.
 *
 * The feed follows the US calendar. On Goan solemnities and feasts it can
 * report a ferial memorial (e.g. St Francis Xavier on 3 December) and the
 * ferial readings, or omit the celebration entirely (St Joseph Vaz on
 * 16 January). These overrides correct the celebration and, where the feed's
 * readings are not the Goan proper, suppress them instead of presenting
 * them as the day's readings.
 *
 * Applied to the network result in the repository and to cache reads in
 * [LiturgicalCacheMapper], so an already cached day is corrected without a
 * database migration and the cache can keep the raw feed.
 *
 * Within 2026–2027, fixed celebrations apply every year; the movable US
 * anomalies are year-specific. The feed currently covers only those two years,
 * and the 2026–2027 fixed feasts do not fall on a Sunday. If the feed is
 * extended, add the new year's Ascension Sunday and US Thanksgiving, and
 * re-check Sunday precedence before widening the year guard.
 */
object GoaCalendarOverrides {

    fun apply(day: LiturgicalDay): LiturgicalDay {
        val override = overrideFor(day.date) ?: return day
        return day.copy(
            celebrationName = override.name ?: day.celebrationName,
            celebrationType = override.type ?: day.celebrationType,
            season = override.season ?: day.season,
            readings = override.readings ?: day.readings,
            properReadingsUnavailable = override.readingsUnavailable
        )
    }

    private data class Override(
        val name: String? = null,
        val type: String? = null,
        val season: String? = null,
        /** null keeps the feed's readings; empty suppresses them. */
        val readings: List<LiturgicalReading>? = null,
        val readingsUnavailable: Boolean = false
    )

    private fun overrideFor(date: LocalDate): Override? = when {
        // The feed publishes 2026-2027. Outside that range the table has not
        // been validated against the Goan ordo, so do not guess.
        date.year !in 2026..2027 -> null

        date.monthValue == 1 && date.dayOfMonth == 16 -> Override(
            name = "Saint Joseph Vaz, Patron of the Archdiocese",
            type = "SOLEMNITY",
            readings = emptyList(),
            readingsUnavailable = true
        )

        date.monthValue == 2 && date.dayOfMonth == 4 -> Override(
            name = "Saint John de Britto, Martyr",
            type = "FEAST",
            readings = emptyList(),
            readingsUnavailable = true
        )

        date.monthValue == 2 && date.dayOfMonth == 6 -> Override(
            name = "Dedication of the Cathedral",
            type = "FEAST",
            readings = emptyList(),
            readingsUnavailable = true
        )

        date.monthValue == 7 && date.dayOfMonth == 3 -> Override(
            name = "Saint Thomas, Apostle of India",
            type = "SOLEMNITY"
            // The feed already carries the apostle's proper readings; keep them.
        )

        date.monthValue == 11 && date.dayOfMonth == 27 -> Override(
            name = "The Holy Crucifix",
            // Universalis Goa shows this celebration without a rank; do not
            // invent one.
            type = "CELEBRATION",
            readings = emptyList(),
            readingsUnavailable = true
        )

        date.monthValue == 12 && date.dayOfMonth == 3 -> Override(
            name = "Saint Francis Xavier, Patron of India, Priest",
            type = "SOLEMNITY",
            readings = emptyList(),
            readingsUnavailable = true
        )

        // US Ascension is transferred to Sunday, so the feed repeats the
        // Ascension readings on the Seventh Sunday of Easter. India keeps
        // Ascension Thursday; suppress the duplicated readings.
        date == LocalDate.of(2026, 5, 17) ||
            date == LocalDate.of(2027, 5, 9) -> Override(
            readings = emptyList(),
            readingsUnavailable = true
        )

        // The feed carries US Thanksgiving readings on the fourth Thursday of
        // November. Goa keeps the weekday; suppress the imported readings.
        date == LocalDate.of(2026, 11, 26) ||
            date == LocalDate.of(2027, 11, 25) -> Override(
            name = "Thursday in Ordinary Time",
            type = "FERIA",
            season = "Ordinary Time",
            readings = emptyList(),
            readingsUnavailable = true
        )

        else -> null
    }
}
