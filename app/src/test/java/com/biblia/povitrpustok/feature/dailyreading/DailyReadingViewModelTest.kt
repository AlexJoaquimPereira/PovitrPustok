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

        override suspend fun getLiturgicalDay(date: LocalDate): LiturgicalDayResult {
            requestedDates += date
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
    fun load_error_surfaces_as_error_state() = runTest(dispatcher.scheduler) {
        val repository = FakeLiturgicalRepository(
            LiturgicalDayResult.Error(LiturgicalErrorKind.NETWORK, "No network.")
        )
        val viewModel = DailyReadingViewModel(repository, FakeResolver()) { LocalDate.now() }

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DailyReadingUiState.Error)
        assertEquals("No network.", (state as DailyReadingUiState.Error).message)
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

        assertNotNull(viewModel.navigationNotice.value)
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

        assertNotNull(viewModel.navigationNotice.value)
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
