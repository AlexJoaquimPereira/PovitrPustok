package com.biblia.povitrpustok.feature.dailyreading

import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalDayResult
import com.biblia.povitrpustok.domain.model.LiturgicalErrorKind
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.domain.model.ReadingType
import com.biblia.povitrpustok.domain.model.ResolvedScriptureReference
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.LiturgicalRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Daily Reading ViewModel tests (Phase 10 §12, §14, §27): today's local
 * date via an injectable clock, loading/success/error, and reading ->
 * resolver -> ReaderDestination navigation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyReadingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeLiturgicalRepository(
        var result: LiturgicalDayResult = LiturgicalDayResult.Success(
            LiturgicalDay(LocalDate.of(2026, 8, 15), "Ordinary Time", null, null, null, null, emptyList())
        )
    ) : LiturgicalRepository {
        val requestedDates = mutableListOf<LocalDate>()
        val forcedRefreshes = mutableListOf<Boolean>()

        override suspend fun getLiturgicalDay(
            date: LocalDate,
            forceRefresh: Boolean
        ): LiturgicalDayResult {
            requestedDates += date
            forcedRefreshes += forceRefresh
            return result
        }
    }

    private class FakeResolver(var resolution: ScriptureResolution = ScriptureResolution.Invalid("none")) :
        ScriptureReferenceResolver {
        val requestedReferences = mutableListOf<String>()

        override suspend fun resolve(reference: String, contextBookId: String?): ScriptureResolution {
            requestedReferences += reference
            return resolution
        }
    }

    @Test
    fun load_uses_the_injectable_local_date() = runTest(dispatcher.scheduler) {
        val repository = FakeLiturgicalRepository()
        val viewModel = DailyReadingViewModel(
            repository = repository,
            referenceResolver = FakeResolver(),
            dateProvider = { LocalDate.of(2026, 8, 15) }
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(LocalDate.of(2026, 8, 15)), repository.requestedDates)
        assertTrue(viewModel.uiState.value is DailyReadingUiState.Success)
    }

    @Test
    fun resume_refreshes_only_when_the_local_date_changes() = runTest(dispatcher.scheduler) {
        var today = LocalDate.of(2026, 8, 15)
        val repository = FakeLiturgicalRepository()
        val viewModel = DailyReadingViewModel(repository, FakeResolver()) { today }

        viewModel.load()
        advanceUntilIdle()
        assertEquals(listOf(today), repository.requestedDates)

        viewModel.refreshIfDateChanged()
        advanceUntilIdle()
        assertEquals("same-day resume must not reload", 1, repository.requestedDates.size)

        today = LocalDate.of(2026, 8, 16)
        viewModel.refreshIfDateChanged()
        advanceUntilIdle()
        assertEquals(
            listOf(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)),
            repository.requestedDates
        )
        assertTrue(viewModel.uiState.value is DailyReadingUiState.Success)
    }

    @Test
    fun date_change_during_load_supersedes_the_old_request() = runTest(dispatcher.scheduler) {
        val firstDate = LocalDate.of(2026, 8, 15)
        val secondDate = LocalDate.of(2026, 8, 16)
        var today = firstDate
        val gate = CompletableDeferred<Unit>()

        val repository = object : LiturgicalRepository {
            val requested = mutableListOf<LocalDate>()

            override suspend fun getLiturgicalDay(
                date: LocalDate,
                forceRefresh: Boolean
            ): LiturgicalDayResult {
                requested += date
                if (date == firstDate) gate.await()
                return LiturgicalDayResult.Success(
                    LiturgicalDay(date, "Ordinary Time", null, null, null, null, emptyList())
                )
            }
        }

        val viewModel = DailyReadingViewModel(repository, FakeResolver()) { today }
        viewModel.load()
        runCurrent() // first request is suspended on the gate
        assertEquals(listOf(firstDate), repository.requested)

        today = secondDate
        viewModel.refreshIfDateChanged()
        advanceUntilIdle() // the first load is cancelled; the second completes

        assertEquals(listOf(firstDate, secondDate), repository.requested)
        val state = viewModel.uiState.value
        assertTrue(state is DailyReadingUiState.Success)
        assertEquals(secondDate, (state as DailyReadingUiState.Success).day.date)
    }

    @Test
    fun previous_and_next_day_load_that_date_and_today_returns() =
        runTest(dispatcher.scheduler) {
            val today = LocalDate.of(2026, 8, 15)
            val repository = FakeLiturgicalRepository()
            val viewModel = DailyReadingViewModel(repository, FakeResolver()) { today }
            viewModel.load()
            advanceUntilIdle()

            viewModel.previousDay()
            advanceUntilIdle()
            assertEquals(today.minusDays(1), viewModel.selectedDate.value)
            assertTrue(!viewModel.isToday())

            viewModel.nextDay()
            advanceUntilIdle()
            assertEquals(today, viewModel.selectedDate.value)
            assertTrue(viewModel.isToday())

            viewModel.previousDay()
            advanceUntilIdle()
            viewModel.goToToday()
            advanceUntilIdle()

            assertEquals(
                listOf(today, today.minusDays(1), today, today.minusDays(1), today),
                repository.requestedDates
            )
        }

    @Test
    fun refresh_bypasses_the_cache_and_reports_freshness() =
        runTest(dispatcher.scheduler) {
            val today = LocalDate.of(2026, 8, 15)
            val cachedAt = 1_760_000_000_000L
            val repository = FakeLiturgicalRepository(
                LiturgicalDayResult.Success(
                    LiturgicalDay(today, "Ordinary Time", "Assumption", null, null, null, emptyList()),
                    cachedAt = cachedAt
                )
            )
            val viewModel = DailyReadingViewModel(repository, FakeResolver()) { today }
            viewModel.load()
            advanceUntilIdle()

            val cached = viewModel.uiState.value as DailyReadingUiState.Success
            assertEquals("cached payloads expose when they were saved", cachedAt, cached.cachedAt)

            viewModel.refresh()
            advanceUntilIdle()
            assertEquals("first load uses the cache", false, repository.forcedRefreshes.first())
            assertEquals("refresh must bypass the cache", true, repository.forcedRefreshes.last())
        }

    @Test
    fun resume_does_not_reload_a_past_date() = runTest(dispatcher.scheduler) {
        var today = LocalDate.of(2026, 8, 15)
        val repository = FakeLiturgicalRepository()
        val viewModel = DailyReadingViewModel(repository, FakeResolver()) { today }
        viewModel.load()
        advanceUntilIdle()
        viewModel.previousDay()
        advanceUntilIdle()
        val requests = repository.requestedDates.size

        today = LocalDate.of(2026, 8, 16)
        viewModel.refreshIfDateChanged()
        advanceUntilIdle()

        assertEquals("a past day must not jump to today on resume", requests, repository.requestedDates.size)
        assertEquals(LocalDate.of(2026, 8, 14), viewModel.selectedDate.value)
    }

    @Test
    fun the_date_label_is_pinned_to_english() {
        assertEquals("Sat, Aug 15, 2026", DailyReadingDates.format(LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun load_error_surfaces_as_error_state() = runTest(dispatcher.scheduler) {
        val repository = FakeLiturgicalRepository(
            LiturgicalDayResult.Error(LiturgicalErrorKind.NETWORK, "No network.")
        )
        val viewModel = DailyReadingViewModel(repository, FakeResolver()) { LocalDate.now() }

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DailyReadingUiState.Error)
        assertEquals(
            "the reason must be typed, not English",
            LiturgicalErrorKind.NETWORK,
            (state as DailyReadingUiState.Error).kind
        )
    }

    @Test
    fun a_day_without_data_reports_no_data() = runTest(dispatcher.scheduler) {
        val repository = FakeLiturgicalRepository(
            LiturgicalDayResult.Error(LiturgicalErrorKind.NO_DATA, "No liturgical data available.")
        )
        val viewModel = DailyReadingViewModel(repository, FakeResolver()) { LocalDate.now() }

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            LiturgicalErrorKind.NO_DATA,
            (state as DailyReadingUiState.Error).kind
        )
    }

    @Test
    fun resolved_reading_produces_a_reader_destination() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver(
            ScriptureResolution.Resolved(
                ResolvedScriptureReference(
                    bookId = "jakob", chapterId = 1, chapterNumber = 1, chapterPosition = 1,
                    verseId = 5, verseStart = 5, verseEnd = 8
                )
            )
        )
        val viewModel = DailyReadingViewModel(FakeLiturgicalRepository(), resolver) { LocalDate.now() }

        viewModel.openReading(LiturgicalReading(ReadingType.GOSPEL, "James 1:5-8"))
        advanceUntilIdle()

        assertEquals(listOf("James 1:5-8"), resolver.requestedReferences)
        val destination = viewModel.pendingNavigation.value
        assertNotNull(destination)
        assertEquals("jakob", destination!!.bookId)
        assertEquals(1, destination.chapterPosition)
        assertEquals(5L, destination.verseId)

        viewModel.consumeNavigation()
        assertNull(viewModel.pendingNavigation.value)
    }

    @Test
    fun unresolvable_reference_surfaces_a_notice_not_a_crash() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver(ScriptureResolution.Invalid("Unsupported reference."))
        val viewModel = DailyReadingViewModel(FakeLiturgicalRepository(), resolver) { LocalDate.now() }
        viewModel.load()
        advanceUntilIdle()

        viewModel.openReading(LiturgicalReading(ReadingType.FIRST_READING, "1 Kings 99:1"))
        advanceUntilIdle()

        assertEquals(DailyNotice.Invalid("1 Kings 99:1"), viewModel.navigationNotice.value)
        assertNull(viewModel.pendingNavigation.value)
        assertTrue(viewModel.uiState.value is DailyReadingUiState.Success) // screen unaffected

        viewModel.consumeNotice()
        assertNull(viewModel.navigationNotice.value)
    }

    @Test
    fun ambiguous_reference_surfaces_a_notice() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver(
            ScriptureResolution.Ambiguous("Jobachem_Pustok", 26, emptyList())
        )
        val viewModel = DailyReadingViewModel(FakeLiturgicalRepository(), resolver) { LocalDate.now() }

        viewModel.openReading(LiturgicalReading(ReadingType.GOSPEL, "Job 26:5"))
        advanceUntilIdle()

        assertEquals(DailyNotice.AmbiguousChapter("Job 26:5"), viewModel.navigationNotice.value)
        assertNull(viewModel.pendingNavigation.value)
    }

    @Test
    fun missing_reference_surfaces_a_not_found_notice() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver(ScriptureResolution.NotFound("Unknown book: 'Genesis'."))
        val viewModel = DailyReadingViewModel(FakeLiturgicalRepository(), resolver) { LocalDate.now() }

        viewModel.openReading(LiturgicalReading(ReadingType.FIRST_READING, "Genesis 99:1"))
        advanceUntilIdle()

        assertEquals(DailyNotice.NotFound("Genesis 99:1"), viewModel.navigationNotice.value)
        assertNull(viewModel.pendingNavigation.value)
    }

    @Test
    fun reading_destination_carries_all_reader_arguments() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver(
            ScriptureResolution.Resolved(
                ResolvedScriptureReference(
                    bookId = "Utpoti", chapterId = 17, chapterNumber = 17, chapterPosition = 17,
                    verseId = 777, verseStart = 11, verseEnd = 12
                )
            )
        )
        val viewModel = DailyReadingViewModel(FakeLiturgicalRepository(), resolver) { LocalDate.now() }

        viewModel.openReading(LiturgicalReading(ReadingType.FIRST_READING, "Genesis 17:11-12"))
        advanceUntilIdle()

        val destination = viewModel.pendingNavigation.value!!
        assertEquals("Utpoti", destination.bookId)
        assertEquals(17, destination.chapterPosition)
        assertEquals(777L, destination.verseId)
    }
}
