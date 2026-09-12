package com.biblia.povitrpustok.data.liturgy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.domain.model.LiturgicalDayResult
import com.biblia.povitrpustok.domain.model.LiturgicalErrorKind
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Daily Reading cache policy (corrective phase Issue 10): date-keyed
 * cache-first behavior, offline fallback, malformed-response protection,
 * and persistence across database recreation.
 */
@RunWith(RobolectricTestRunner::class)
class LiturgicalCacheTest {

    private val today = LocalDate.of(2026, 8, 15)
    private val yesterday = LocalDate.of(2026, 8, 14)

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    private class FakeApi(
        var readings: ReadingsDto = ReadingsDto(season = "Ordinary Time"),
        var calendar: CalendarDto = CalendarDto(season = "Ordinary Time"),
        var readingsFailure: Exception? = null,
        var calendarFailure: Exception? = null
    ) : CatholicReadingsApi {
        var readingsCalls = 0
        var calendarCalls = 0

        override suspend fun getReadings(year: Int, monthDay: String): ReadingsDto {
            readingsCalls++
            readingsFailure?.let { throw it }
            return readings
        }

        override suspend fun getCalendar(year: Int, monthDay: String): CalendarDto {
            calendarCalls++
            calendarFailure?.let { throw it }
            return calendar
        }
    }

    private fun successFixture() = Pair(
        ReadingsDto(
            season = "Ordinary Time",
            readings = ReadingsFieldsDto(
                firstReading = "1 Corinthians 15:20-27",
                psalm = "Psalm 45:10, 11, 12, 16",
                gospel = "Luke 1:39-56"
            )
        ),
        CalendarDto(season = "Ordinary Time", celebration = CelebrationDto(name = "Assumption", type = "SOLEMNITY"))
    )

    private fun repository(api: CatholicReadingsApi, db: AppDatabase = database): LiturgicalRepositoryImpl =
        LiturgicalRepositoryImpl(api, db)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun first_request_fetches_and_stores() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val repository = repository(api)

        val result = repository.getLiturgicalDay(today)

        assertTrue(result is LiturgicalDayResult.Success)
        assertEquals("API must have been called once", 1, api.readingsCalls)
        val cached = database.liturgicalDayCacheDao().getByDateWithReadings(today.toString())
        assertTrue("result must be stored", cached != null)
        assertEquals("Assumption", cached!!.day.celebrationName)
        assertEquals(3, cached.readings.size)
    }

    @Test
    fun second_request_same_date_uses_cache_only() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val repository = repository(api)

        repository.getLiturgicalDay(today)
        val second = repository.getLiturgicalDay(today)

        assertTrue(second is LiturgicalDayResult.Success)
        assertEquals("cache hit must not touch the API", 1, api.readingsCalls)
        assertEquals(1, api.calendarCalls)
    }

    @Test
    fun network_unavailable_with_cache_returns_cached() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val repository = repository(api)
        repository.getLiturgicalDay(today)

        // now the network goes down
        api.readingsFailure = IOException("offline")
        api.calendarFailure = IOException("offline")

        val result = repository.getLiturgicalDay(today)
        assertTrue("offline with cache must return the cached day", result is LiturgicalDayResult.Success)
        assertEquals("Assumption", (result as LiturgicalDayResult.Success).day.celebrationName)
        assertEquals(3, result.day.readings.size)
    }

    @Test
    fun network_unavailable_without_cache_returns_error() = runBlocking {
        val api = FakeApi(readingsFailure = IOException("offline"), calendarFailure = IOException("offline"))
        val repository = repository(api)

        val result = repository.getLiturgicalDay(today)
        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.NETWORK, (result as LiturgicalDayResult.Error).kind)
    }

    @Test
    fun new_date_triggers_a_new_request() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val repository = repository(api)

        repository.getLiturgicalDay(yesterday)
        repository.getLiturgicalDay(today)

        assertEquals("each uncached date must hit the API", 2, api.readingsCalls)
        // yesterday's row still exists, today's is separate
        assertTrue(database.liturgicalDayCacheDao().getByDateWithReadings(yesterday.toString()) != null)
        assertTrue(database.liturgicalDayCacheDao().getByDateWithReadings(today.toString()) != null)
    }

    @Test
    fun cached_yesterday_does_not_satisfy_today() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val repository = repository(api)

        repository.getLiturgicalDay(yesterday)
        // today must NOT reuse yesterday's entry
        val todayResult = repository.getLiturgicalDay(today)
        assertTrue(todayResult is LiturgicalDayResult.Success)
        assertEquals(2, api.readingsCalls)
    }

    @Test
    fun malformed_response_does_not_overwrite_valid_cache() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val repository = repository(api)
        repository.getLiturgicalDay(today)
        val storedBefore = database.liturgicalDayCacheDao().getByDateWithReadings(today.toString())

        // API becomes malformed; the cache must win and remain intact
        api.readingsFailure = JsonSyntaxException("bad json")
        api.calendarFailure = JsonSyntaxException("bad json")
        val result = repository.getLiturgicalDay(today)
        assertTrue(result is LiturgicalDayResult.Success)
        val storedAfter = database.liturgicalDayCacheDao().getByDateWithReadings(today.toString())
        assertEquals(storedBefore!!.day.celebrationName, storedAfter!!.day.celebrationName)
        assertEquals(storedBefore.readings.size, storedAfter.readings.size)
    }

    @Test
    fun malformed_first_request_stores_nothing() = runBlocking {
        val api = FakeApi(calendarFailure = JsonSyntaxException("bad json"))
        val repository = repository(api)
        val result = repository.getLiturgicalDay(today)
        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.MALFORMED_RESPONSE, (result as LiturgicalDayResult.Error).kind)
        assertTrue(database.liturgicalDayCacheDao().getByDateWithReadings(today.toString()) == null)
    }

    @Test
    fun readings_and_celebration_survive_database_recreation() = runBlocking {
        val (readings, calendar) = successFixture()
        val api = FakeApi(readings = readings, calendar = calendar)
        val dbPath = File(context.filesDir, "cache_persist_test.db")
        dbPath.delete()

        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbPath.name).build()
        try {
            repository(api, db1).getLiturgicalDay(today)
        } finally {
            db1.close()
        }

        // "restart": a fresh database instance over the same file
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbPath.name).build()
        try {
            val cached = db2.liturgicalDayCacheDao().getByDateWithReadings(today.toString())
            assertTrue("cache must survive restart", cached != null)
            assertEquals("Assumption", cached!!.day.celebrationName)
            assertEquals("Ordinary Time", cached.day.season)
            assertEquals(3, cached.readings.size)
            assertEquals("Psalm 45:10, 11, 12, 16", cached.readings.single { it.type == "RESPONSORIAL_PSALM" }.reference)
        } finally {
            db2.close()
            dbPath.delete()
        }
    }
}
