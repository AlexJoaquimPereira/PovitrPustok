package com.biblia.povitrpustok.data.liturgy

import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.data.app.LiturgicalDayCacheDao
import com.biblia.povitrpustok.domain.model.LiturgicalDayResult
import com.biblia.povitrpustok.domain.model.LiturgicalErrorKind
import com.biblia.povitrpustok.domain.repository.LiturgicalRepository
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * [LiturgicalRepository] over the Catholic Readings API
 * (implementation plan §8, §19, §16).
 *
 * The repository owns composition of the two external resources (readings
 * endpoint + calendar endpoint) and maps failures to controlled
 * [LiturgicalDayResult.Error] states — no HTTP/DTO/JSON types reach the
 * domain. Both resources are required for a complete day (§19); either
 * failure is a controlled error, never a crash.
 */
@Singleton
class LiturgicalRepositoryImpl @Inject constructor(
    private val api: CatholicReadingsApi,
    private val appDatabase: AppDatabase
) : LiturgicalRepository {

    /**
     * Date-keyed cache policy (corrective phase Issue 10):
     *  - cached row for this date        -> return cached (no network);
     *  - no cache                        -> fetch, save on success, return;
     *  - fetch failed + cache appeared   -> return cached (robustness);
     *  - fetch failed + no cache         -> the network error state.
     *
     * Only successful responses are ever stored, so a malformed response
     * can never overwrite a valid cache entry. The key is the local
     * calendar date, so yesterday's entry never satisfies today. No
     * polling: the caller (Daily Reading screen) fetches at most once per
     * day per app use.
     */
    override suspend fun getLiturgicalDay(
        date: LocalDate,
        forceRefresh: Boolean
    ): LiturgicalDayResult =
        withContext(Dispatchers.IO) {
            val dateKey = date.toString()
            if (!forceRefresh) {
                cacheDao.getByDateWithReadings(dateKey)?.let { cached ->
                    return@withContext LiturgicalDayResult.Success(
                        day = LiturgicalCacheMapper.toDomain(cached),
                        // F-18: the cache is not fresh data; say when it was saved.
                        cachedAt = cached.day.fetchedAt
                    )
                }
            }

            fetchMutex.withLock {
                // LITURGY-04: another load may have populated the cache while
                // this coroutine waited; re-check before touching the network.
                // A forced refresh skips this so the user can actually retry.
                if (!forceRefresh) {
                    cacheDao.getByDateWithReadings(dateKey)?.let { cached ->
                        return@withLock LiturgicalDayResult.Success(
                            day = LiturgicalCacheMapper.toDomain(cached),
                            cachedAt = cached.day.fetchedAt
                        )
                    }
                }

                when (val network = fetchFromNetwork(date)) {
                    is LiturgicalDayResult.Success -> {
                        // LITURGY-01: one transaction — day + readings are
                        // replaced atomically, so process death can never leave a
                        // cached day with missing/mixed readings.
                        cacheDao.replaceDay(
                            day = LiturgicalCacheMapper.toDayEntity(network.day, System.currentTimeMillis()),
                            readings = LiturgicalCacheMapper.toReadingEntities(
                                // The DAO re-points rows at the real day id.
                                dayId = 0L,
                                readings = network.day.readings
                            )
                        )
                        network
                    }
                    is LiturgicalDayResult.Error -> {
                        val lateCache = cacheDao.getByDateWithReadings(dateKey)
                        if (lateCache != null) {
                            LiturgicalDayResult.Success(
                                day = LiturgicalCacheMapper.toDomain(lateCache),
                                cachedAt = lateCache.day.fetchedAt
                            )
                        } else {
                            network
                        }
                    }
                }
            }
        }

    private suspend fun fetchFromNetwork(date: LocalDate): LiturgicalDayResult =
        try {
            coroutineScope {
                val readingsDeferred = async { api.getReadings(date.year, date.monthDayPath()) }
                val calendarDeferred = async { api.getCalendar(date.year, date.monthDayPath()) }
                val readingsDto = readingsDeferred.await()
                val calendarDto = calendarDeferred.await()

                // LITURGY-04: Retrofit/Gson can yield a null body despite the
                // declared type (e.g. 200 with an empty payload). Guard before
                // dereferencing so the UI gets a controlled error, not an NPE.
                if (readingsDto == null || calendarDto == null) {
                    return@coroutineScope LiturgicalDayResult.Error(
                        LiturgicalErrorKind.MALFORMED_RESPONSE,
                        "The liturgical service returned an empty response."
                    )
                }

                val day = CatholicReadingsMapper.toLiturgicalDay(date, readingsDto, calendarDto)
                if (day.readings.isEmpty() && day.celebrationName == null) {
                    LiturgicalDayResult.Error(
                        LiturgicalErrorKind.NO_DATA,
                        "No liturgical data available for $date."
                    )
                } else {
                    LiturgicalDayResult.Success(day)
                }
            }
        } catch (e: HttpException) {
            LiturgicalDayResult.Error(
                LiturgicalErrorKind.HTTP,
                "The liturgical service returned an error (${e.code()})."
            )
        } catch (e: IOException) {
            LiturgicalDayResult.Error(
                LiturgicalErrorKind.NETWORK,
                "Unable to reach the liturgical service. Check your connection."
            )
        } catch (e: JsonSyntaxException) {
            LiturgicalDayResult.Error(
                LiturgicalErrorKind.MALFORMED_RESPONSE,
                "The liturgical service returned malformed data."
            )
        } catch (e: IllegalArgumentException) {
            LiturgicalDayResult.Error(
                LiturgicalErrorKind.MALFORMED_RESPONSE,
                "The liturgical service returned unreadable data."
            )
        }

    private val cacheDao: LiturgicalDayCacheDao
        get() = appDatabase.liturgicalDayCacheDao()

    /** Serializes cache-miss fetches so concurrent loads cannot duplicate network work (LITURGY-04). */
    private val fetchMutex = Mutex()

    private fun LocalDate.monthDayPath(): String =
        // LITURGY-03: API paths require ASCII digits; the default locale can
        // produce non-Latin numerals (e.g. Arabic-Indic) and cause 404s.
        format(MONTH_DAY_FORMAT)

    private companion object {
        /** Immutable/thread-safe; pins ASCII digits and MM-DD order. */
        val MONTH_DAY_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT)
    }
}
