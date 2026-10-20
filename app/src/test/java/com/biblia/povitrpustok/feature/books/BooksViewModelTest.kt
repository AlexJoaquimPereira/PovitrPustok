package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F-03: the Books screen must never hang on a full-screen spinner. Title
 * failures degrade to catalog names plus a retryable banner, cancellation is
 * never swallowed, and duplicate scans are avoided.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BooksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBookTitleRepository(
        var names: Map<String, String> = emptyMap(),
        var failure: Exception? = null,
        var gate: CompletableDeferred<Unit>? = null
    ) : BookTitleRepository {
        var callCount = 0

        override suspend fun getDisplayName(bookId: String): String? = names[bookId]

        override suspend fun loadAllDisplayNames(): Map<String, String> {
            callCount++
            gate?.await()
            failure?.let { throw it }
            return names
        }
    }

    @Test
    fun successful_load_publishes_names_without_error() = runTest(dispatcher.scheduler) {
        val repository = FakeBookTitleRepository(names = mapOf("jakob" to "JAKOBACHEM POTR"))
        val viewModel = BooksViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.titlesFailed)
        assertEquals("JAKOBACHEM POTR", state.displayNames["jakob"])
        assertEquals(1, repository.callCount)
    }

    @Test
    fun failed_load_keeps_catalog_visible_and_flags_error() = runTest(dispatcher.scheduler) {
        val repository = FakeBookTitleRepository(
            failure = BibleDatabaseAccessException("corpus unavailable")
        )
        val viewModel = BooksViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("a failed scan must never leave a spinner", state.isLoading)
        assertTrue(state.titlesFailed)
        assertTrue("catalog names remain the fallback", state.displayNames.isEmpty())
    }

    @Test
    fun retry_after_failure_recovers_and_clears_the_banner() = runTest(dispatcher.scheduler) {
        val repository = FakeBookTitleRepository(
            failure = BibleDatabaseAccessException("corpus unavailable")
        )
        val viewModel = BooksViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.titlesFailed)

        repository.failure = null
        repository.names = mapOf("Esther" to "ESTHER")
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.titlesFailed)
        assertEquals("ESTHER", state.displayNames["Esther"])
        assertEquals(2, repository.callCount)
    }

    @Test
    fun retry_while_a_scan_is_in_flight_does_not_start_another_scan() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeBookTitleRepository(gate = gate)
        val viewModel = BooksViewModel(repository)
        runCurrent() // the initial scan starts and suspends on the gate

        viewModel.retry()
        viewModel.retry()
        runCurrent()
        assertEquals("duplicate in-flight scans are not allowed", 1, repository.callCount)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.callCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
