package com.biblia.povitrpustok.data.liturgy

import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReadingType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Goan particular-calendar corrections (1.2.2). The feed is US-calendar;
 * these tests pin the dates where it mislabels or omits a Goan celebration or
 * imports US readings.
 */
class GoaCalendarOverridesTest {

    private fun day(
        date: LocalDate,
        name: String? = "Feria",
        type: String? = "FERIA",
        readings: List<LiturgicalReading> = listOf(
            LiturgicalReading(ReadingType.GOSPEL, "Matthew 7:21, 24-27")
        )
    ) = LiturgicalDay(
        date = date,
        season = "Ordinary Time",
        celebrationName = name,
        celebrationType = type,
        celebrationDescription = null,
        celebrationQuote = null,
        readings = readings
    )

    @Test
    fun saint_francis_xavier_is_a_solemnity_and_never_uses_the_ferial_readings() {
        val result = GoaCalendarOverrides.apply(day(LocalDate.of(2026, 12, 3), "Saint Francis Xavier, Priest", "MEMORIAL"))

        assertEquals("Saint Francis Xavier, Patron of India, Priest", result.celebrationName)
        assertEquals("SOLEMNITY", result.celebrationType)
        assertTrue(result.properReadingsUnavailable)
        assertTrue(result.readings.isEmpty())
    }

    @Test
    fun the_fixed_goan_celebrations_are_corrected() {
        val josephVaz = GoaCalendarOverrides.apply(day(LocalDate.of(2027, 1, 16)))
        assertEquals("Saint Joseph Vaz, Patron of the Archdiocese", josephVaz.celebrationName)
        assertEquals("SOLEMNITY", josephVaz.celebrationType)
        assertTrue(josephVaz.properReadingsUnavailable)

        val deBritto = GoaCalendarOverrides.apply(day(LocalDate.of(2026, 2, 4)))
        assertEquals("Saint John de Britto, Martyr", deBritto.celebrationName)
        assertEquals("FEAST", deBritto.celebrationType)
        assertTrue(deBritto.properReadingsUnavailable)

        val cathedral = GoaCalendarOverrides.apply(day(LocalDate.of(2026, 2, 6)))
        assertEquals("Dedication of the Cathedral", cathedral.celebrationName)
        assertEquals("FEAST", cathedral.celebrationType)
        assertTrue(cathedral.properReadingsUnavailable)

        val crucifix = GoaCalendarOverrides.apply(day(LocalDate.of(2026, 11, 27)))
        assertEquals("The Holy Crucifix", crucifix.celebrationName)
        assertEquals("CELEBRATION", crucifix.celebrationType)
        assertTrue(crucifix.properReadingsUnavailable)
    }

    @Test
    fun saint_francis_xavier_is_corrected_in_2027_too() {
        val result = GoaCalendarOverrides.apply(day(LocalDate.of(2027, 12, 3)))

        assertEquals("Saint Francis Xavier, Patron of India, Priest", result.celebrationName)
        assertEquals("SOLEMNITY", result.celebrationType)
        assertTrue(result.properReadingsUnavailable)
    }

    @Test
    fun thanksgiving_day_is_not_imported_as_a_goan_celebration() {
        val result = GoaCalendarOverrides.apply(
            day(LocalDate.of(2026, 11, 26), "Thanksgiving Day", "MEMORIAL")
        )

        assertEquals("Thursday in Ordinary Time", result.celebrationName)
        assertEquals("FERIA", result.celebrationType)
        assertTrue(result.readings.isEmpty())
        assertTrue(result.properReadingsUnavailable)
    }

    @Test
    fun saint_thomas_is_a_solemnity_and_keeps_his_proper_readings() {
        val proper = listOf(
            LiturgicalReading(ReadingType.FIRST_READING, "Ephesians 2:19-22"),
            LiturgicalReading(ReadingType.GOSPEL, "John 20:24-29")
        )
        val result = GoaCalendarOverrides.apply(day(LocalDate.of(2026, 7, 3), "Saint Thomas the Apostle", "FEAST", proper))

        assertEquals("Saint Thomas, Apostle of India", result.celebrationName)
        assertEquals("SOLEMNITY", result.celebrationType)
        assertEquals(proper, result.readings)
        assertFalse(result.properReadingsUnavailable)
    }

    @Test
    fun duplicated_ascension_readings_are_suppressed_on_the_seventh_sunday() {
        val result = GoaCalendarOverrides.apply(
            day(LocalDate.of(2026, 5, 17), "7th Sunday of Easter", "SUNDAY")
        )

        assertEquals("7th Sunday of Easter", result.celebrationName)
        assertEquals("SUNDAY", result.celebrationType)
        assertTrue(result.readings.isEmpty())
        assertTrue(result.properReadingsUnavailable)
    }

    @Test
    fun us_thanksgiving_readings_are_suppressed_in_goa() {
        val result = GoaCalendarOverrides.apply(
            day(LocalDate.of(2026, 11, 26), "Thursday of the 34th week of Ordinary Time", "FERIA")
        )

        assertEquals("Thursday in Ordinary Time", result.celebrationName)
        assertEquals("FERIA", result.celebrationType)
        assertTrue(result.readings.isEmpty())
        assertTrue(result.properReadingsUnavailable)
    }

    @Test
    fun years_outside_the_feed_range_are_not_guessed() {
        val original = day(LocalDate.of(2028, 12, 3), "Saint Francis Xavier, Priest", "MEMORIAL")
        val result = GoaCalendarOverrides.apply(original)

        assertEquals(original, result)
    }

    @Test
    fun normal_days_are_unchanged() {
        val original = day(LocalDate.of(2026, 12, 4), "Friday of the 1st week of Advent", "FERIA")
        val result = GoaCalendarOverrides.apply(original)

        assertEquals(original, result)
    }
}
