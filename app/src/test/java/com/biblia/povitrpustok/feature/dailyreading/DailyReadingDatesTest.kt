package com.biblia.povitrpustok.feature.dailyreading

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-18: the Daily Reading date and cache timestamps are pinned to English and
 * the freshness label never implies "today" for an older cache entry.
 */
class DailyReadingDatesTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun millis(dateTime: String): Long =
        LocalDateTime.parse(dateTime).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun the_day_label_is_english_and_stable() {
        assertEquals("Sat, Aug 15, 2026", DailyReadingDates.format(LocalDate.of(2026, 8, 15)))
        assertEquals("Thu, Jan 1, 2026", DailyReadingDates.format(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun a_cache_saved_today_shows_only_the_time() {
        assertEquals(
            "14:05",
            DailyReadingDates.formatFreshness(
                cachedAt = millis("2026-10-23T14:05:00"),
                now = millis("2026-10-23T21:30:00"),
                zone = utc
            )
        )
    }

    @Test
    fun an_older_cache_shows_the_date_it_was_saved() {
        assertEquals(
            "21 Oct, 14:05",
            DailyReadingDates.formatFreshness(
                cachedAt = millis("2026-10-21T14:05:00"),
                now = millis("2026-10-23T09:00:00"),
                zone = utc
            )
        )
    }

    @Test
    fun a_cache_saved_in_the_future_still_renders_a_date() {
        // A device clock restored backwards must not produce a bare time that
        // looks like today's data.
        assertEquals(
            "25 Oct, 08:00",
            DailyReadingDates.formatFreshness(
                cachedAt = millis("2026-10-25T08:00:00"),
                now = millis("2026-10-23T09:00:00"),
                zone = utc
            )
        )
    }
}
