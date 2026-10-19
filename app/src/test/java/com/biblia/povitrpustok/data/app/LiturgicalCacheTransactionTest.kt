package com.biblia.povitrpustok.data.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * LITURGY-01: replacing a cached liturgical day must be atomic — readers
 * observe either the complete old day or the complete new day, never a day
 * with missing/mixed readings.
 */
@RunWith(RobolectricTestRunner::class)
class LiturgicalCacheTransactionTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacing_a_date_leaves_only_the_new_day_and_readings() = runBlocking {
        val dao = database.liturgicalDayCacheDao()
        val date = "2026-01-01"

        dao.replaceDay(
            day = day(date, celebration = "Old celebration", fetchedAt = 1L),
            readings = listOf(reading("Old first", 0), reading("Old psalm", 1))
        )
        val before = dao.getByDateWithReadings(date)!!
        assertEquals("Old celebration", before.day.celebrationName)
        assertEquals(
            listOf("Old first", "Old psalm"),
            before.readings.sortedBy { it.position }.map { it.reference }
        )

        dao.replaceDay(
            day = day(date, celebration = "New celebration", fetchedAt = 2L),
            readings = listOf(reading("New gospel", 0))
        )

        val after = dao.getByDateWithReadings(date)!!
        assertEquals("New celebration", after.day.celebrationName)
        assertEquals(2L, after.day.fetchedAt)
        assertEquals(
            "no old reading may survive a replacement",
            listOf("New gospel"),
            after.readings.map { it.reference }
        )
    }

    @Test
    fun celebration_only_day_is_cached_completely_with_zero_readings() = runBlocking {
        val dao = database.liturgicalDayCacheDao()
        val date = "2026-01-02"

        dao.replaceDay(
            day = day(date, celebration = "Saint of the day", fetchedAt = 3L),
            readings = emptyList()
        )

        val cached = dao.getByDateWithReadings(date)!!
        assertEquals("Saint of the day", cached.day.celebrationName)
        assertEquals("a deliberate zero-reading celebration is complete, not torn", 0, cached.readings.size)
    }

    private fun day(date: String, celebration: String, fetchedAt: Long) = LiturgicalDayCacheEntity(
        date = date,
        season = "Ordinary Time",
        celebrationName = celebration,
        celebrationType = null,
        celebrationDescription = null,
        celebrationQuote = null,
        fetchedAt = fetchedAt
    )

    private fun reading(reference: String, position: Int) = LiturgicalReadingEntity(
        dayId = 0L,
        type = "FIRST_READING",
        reference = reference,
        displayLabel = null,
        position = position
    )
}
