package com.biblia.povitrpustok.data.liturgy

import com.google.gson.Gson
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTO -> domain mapping tests using the actual documented JSON shapes
 * (Phase 10 §18, §23). No network.
 */
class CatholicReadingsMapperTest {

    private val gson = Gson()

    private fun readingsJson(inner: String) =
        """{"date":"2026-08-10","monthDay":"8/10","season":"Ordinary Time","readings":{$inner},"usccbLink":"https://example.org","apiEndpoint":"https://example.org/readings.json"}"""

    @Test
    fun normal_weekday_maps_first_psalm_and_gospel() {
        val dto = gson.fromJson(
            readingsJson(
                """"firstReading":"2 Corinthians 9:6-10","psalm":"Psalm 112:1-2, 5-6, 7-8, 9","gospel":"John 12:24-26""""
            ),
            ReadingsDto::class.java
        )
        val calendar = gson.fromJson(
            """{"date":"2026-08-10","season":"Ordinary Time"}""",
            CalendarDto::class.java
        )

        val day = CatholicReadingsMapper.toLiturgicalDay(LocalDate.of(2026, 8, 10), dto, calendar)

        assertEquals(LocalDate.of(2026, 8, 10), day.date)
        assertEquals("Ordinary Time", day.season)
        assertEquals(3, day.readings.size)
        assertEquals(com.biblia.povitrpustok.domain.model.ReadingType.FIRST_READING, day.readings[0].type)
        assertEquals("2 Corinthians 9:6-10", day.readings[0].reference)
        assertEquals(com.biblia.povitrpustok.domain.model.ReadingType.RESPONSORIAL_PSALM, day.readings[1].type)
        assertEquals("Psalm 112:1-2, 5-6, 7-8, 9", day.readings[1].reference)
        assertEquals(com.biblia.povitrpustok.domain.model.ReadingType.GOSPEL, day.readings[2].type)
        assertEquals("John 12:24-26", day.readings[2].reference)
        assertNull(day.celebrationName)
    }

    @Test
    fun solemnity_without_first_reading_maps_optional_fields() {
        val dto = gson.fromJson(
            readingsJson(
                """"psalm":"Psalm 45:10, 11, 12, 16","secondReading":"1 Corinthians 15:20-27","gospel":"Luke 1:39-56""""
            ),
            ReadingsDto::class.java
        )
        val calendar = gson.fromJson(
            """{"date":"2026-08-15","season":"Ordinary Time","celebration":{"name":"The Assumption of the Blessed Virgin Mary","type":"SOLEMNITY","quote":"","description":""}}""",
            CalendarDto::class.java
        )

        val day = CatholicReadingsMapper.toLiturgicalDay(LocalDate.of(2026, 8, 15), dto, calendar)

        assertEquals(3, day.readings.size)
        // no first reading on this solemnity
        assertTrue(day.readings.none { it.type == com.biblia.povitrpustok.domain.model.ReadingType.FIRST_READING })
        assertEquals("1 Corinthians 15:20-27", day.readings.first { it.type == com.biblia.povitrpustok.domain.model.ReadingType.SECOND_READING }.reference)
        // celebration mapped; empty quote/description become null
        assertEquals("The Assumption of the Blessed Virgin Mary", day.celebrationName)
        assertEquals("SOLEMNITY", day.celebrationType)
        assertNull(day.celebrationQuote)
        assertNull(day.celebrationDescription)
    }

    @Test
    fun missing_readings_object_yields_empty_list() {
        val dto = gson.fromJson(
            """{"date":"2026-08-10","season":"Lent"}""",
            ReadingsDto::class.java
        )
        val calendar = gson.fromJson(
            """{"date":"2026-08-10","season":"Lent","celebration":{"name":"A Day"}}""",
            CalendarDto::class.java
        )

        val day = CatholicReadingsMapper.toLiturgicalDay(LocalDate.of(2026, 8, 10), dto, calendar)
        assertTrue(day.readings.isEmpty())
        assertEquals("A Day", day.celebrationName)
    }

    @Test
    fun blank_readings_are_treated_as_absent() {
        val dto = gson.fromJson(
            readingsJson(""""firstReading":"","psalm":"   ","gospel":"John 3:16""""),
            ReadingsDto::class.java
        )
        val calendar = gson.fromJson("{}", CalendarDto::class.java)

        val day = CatholicReadingsMapper.toLiturgicalDay(LocalDate.of(2026, 8, 10), dto, calendar)
        assertEquals(1, day.readings.size)
        assertEquals(com.biblia.povitrpustok.domain.model.ReadingType.GOSPEL, day.readings[0].type)
    }

    @Test
    fun season_falls_back_from_readings_to_calendar() {
        val dto = gson.fromJson("""{"readings":{"gospel":"John 3:16"}}""", ReadingsDto::class.java)
        val calendar = gson.fromJson("""{"season":"Advent"}""", CalendarDto::class.java)

        val day = CatholicReadingsMapper.toLiturgicalDay(LocalDate.of(2026, 12, 1), dto, calendar)
        assertEquals("Advent", day.season)
    }
}
