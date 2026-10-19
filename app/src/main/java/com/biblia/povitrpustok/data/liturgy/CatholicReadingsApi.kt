package com.biblia.povitrpustok.data.liturgy

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Catholic Readings API client (Phase 10, plan §2).
 *
 * https://cpbjr.github.io/catholic-readings-api/
 */
interface CatholicReadingsApi {

    @GET("readings/{year}/{monthDay}.json")
    suspend fun getReadings(
        @Path("year") year: Int,
        @Path("monthDay") monthDay: String
    ): ReadingsDto?

    @GET("liturgical-calendar/{year}/{monthDay}.json")
    suspend fun getCalendar(
        @Path("year") year: Int,
        @Path("monthDay") monthDay: String
    ): CalendarDto?
}
