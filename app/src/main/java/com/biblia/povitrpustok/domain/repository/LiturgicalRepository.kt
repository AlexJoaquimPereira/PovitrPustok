package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalDayResult
import java.time.LocalDate

/**
 * Domain boundary for liturgical data (implementation plan §25, §31).
 *
 * The external API is a source of liturgical metadata and Scripture
 * *references*; the application's local Bible databases remain the
 * authoritative Bible text source. No HTTP/DTO/JSON/URL types cross this
 * interface, so a future localized calendar provider (e.g. the Liturgical
 * Calendar API) can be added behind the same boundary without changing
 * the Daily Reading UI or domain models.
 */
interface LiturgicalRepository {

    /**
     * Returns the liturgical day for [date] (celebration + readings),
     * composed by the implementation from its external resources.
     */
    suspend fun getLiturgicalDay(date: LocalDate): LiturgicalDayResult
}
