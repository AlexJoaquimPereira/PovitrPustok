package com.biblia.povitrpustok.feature.dailyreading

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One date format for the Daily Reading header (F-18).
 *
 * Pinned to English rather than the default locale: the liturgical data and
 * the reference parsing are English, and a mixed-locale device should not
 * produce a half-translated date. A deliberate localization pass can replace
 * this object without touching the screen.
 */
object DailyReadingDates {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

    fun format(date: LocalDate): String = dateFormatter.format(date)

    /**
     * When the cached payload was saved (F-18): the time alone when it was
     * saved today, otherwise the date and time — a past day's cache can be
     * days old, and "updated 23:39" alone would imply today.
     */
    fun formatFreshness(
        cachedAt: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val saved = Instant.ofEpochMilli(cachedAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (saved.toLocalDate() == today) {
            timeFormatter.format(saved)
        } else {
            dateTimeFormatter.format(saved)
        }
    }
}
