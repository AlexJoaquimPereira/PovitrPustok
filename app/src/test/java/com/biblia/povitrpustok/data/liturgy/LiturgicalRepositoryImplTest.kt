package com.biblia.povitrpustok.data.liturgy

import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.domain.model.LiturgicalDayResult
import com.biblia.povitrpustok.domain.model.LiturgicalErrorKind
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider

/**
 * Repository composition and controlled error mapping (Phase 10 §19, §24)
 * with a fake API client — no network.
 */
@RunWith(RobolectricTestRunner::class)
class LiturgicalRepositoryImplTest {

    private val date = LocalDate.of(2026, 8, 15)

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

    private fun newRepository(api: CatholicReadingsApi): LiturgicalRepositoryImpl =
        LiturgicalRepositoryImpl(api, database)

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

    @Test
    fun successful_combined_response_maps_to_a_day() = runBlocking {
        val api = FakeApi(
            readings = ReadingsDto(
                season = "Ordinary Time",
                readings = ReadingsFieldsDto(
                    firstReading = "1 Corinthians 15:20-27",
                    psalm = "Psalm 45:10, 11, 12, 16",
                    gospel = "Luke 1:39-56"
                )
            ),
            calendar = CalendarDto(
                season = "Ordinary Time",
                celebration = CelebrationDto(name = "Assumption", type = "SOLEMNITY")
            )
        )
        val repository = newRepository(api)

        val result = repository.getLiturgicalDay(date)

        assertTrue(result is LiturgicalDayResult.Success)
        val day = (result as LiturgicalDayResult.Success).day
        assertEquals(date, day.date)
        assertEquals("Assumption", day.celebrationName)
        assertEquals(3, day.readings.size)
        assertEquals(1, api.readingsCalls)
        assertEquals(1, api.calendarCalls)
    }

    @Test
    fun calendar_failure_is_a_controlled_error() = runBlocking {
        val api = FakeApi(
            readings = ReadingsDto(readings = ReadingsFieldsDto(gospel = "John 3:16")),
            calendarFailure = IOException("calendar down")
        )
        val repository = newRepository(api)

        val result = repository.getLiturgicalDay(date)

        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.NETWORK, (result as LiturgicalDayResult.Error).kind)
    }

    @Test
    fun readings_failure_is_a_controlled_error() = runBlocking {
        val api = FakeApi(
            readingsFailure = IOException("readings down"),
            calendar = CalendarDto(celebration = CelebrationDto(name = "Assumption"))
        )
        val repository = newRepository(api)

        val result = repository.getLiturgicalDay(date)

        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.NETWORK, (result as LiturgicalDayResult.Error).kind)
    }

    @Test
    fun http_failure_is_a_controlled_http_error() = runBlocking {
        val http = HttpException(Response.error<Any>(503, "down".toResponseBody(null)))
        val api = FakeApi(readingsFailure = http)
        val repository = newRepository(api)

        val result = repository.getLiturgicalDay(date)

        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.HTTP, (result as LiturgicalDayResult.Error).kind)
    }

    @Test
    fun malformed_response_is_a_controlled_error() = runBlocking {
        val api = FakeApi(calendarFailure = JsonSyntaxException("bad json"))
        val repository = newRepository(api)

        val result = repository.getLiturgicalDay(date)

        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.MALFORMED_RESPONSE, (result as LiturgicalDayResult.Error).kind)
    }

    @Test
    fun empty_day_without_readings_or_celebration_is_no_data() = runBlocking {
        val api = FakeApi(readings = ReadingsDto(), calendar = CalendarDto())
        val repository = newRepository(api)

        val result = repository.getLiturgicalDay(date)

        assertTrue(result is LiturgicalDayResult.Error)
        assertEquals(LiturgicalErrorKind.NO_DATA, (result as LiturgicalDayResult.Error).kind)
    }
}
