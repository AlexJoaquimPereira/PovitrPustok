package com.biblia.povitrpustok.feature.search

import com.biblia.povitrpustok.testutil.FakeBookNameRepository

import androidx.lifecycle.SavedStateHandle
import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import com.biblia.povitrpustok.domain.model.SearchFailure
import com.biblia.povitrpustok.domain.model.SearchOutcome
import com.biblia.povitrpustok.domain.model.SearchResult
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SearchViewModel pipeline tests (Phase 5 §21): debounce, cancellation,
 * empty-query, success, error, book filter — pure JVM with a fake
 * repository, deterministic via the test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSearchRepository : BibleSearchRepository {
        var callCount = 0
        var lastQuery: String? = null
        var lastBookIds: List<String>? = null
        var gate: CompletableDeferred<Unit>? = null
        var results: List<SearchResult> = emptyList()
        var failures: List<SearchFailure> = emptyList()
        var failure: Exception? = null

        override suspend fun search(
            query: String,
            bookIds: List<String>?,
            limit: Int
        ): List<SearchResult> = searchWithDiagnostics(query, bookIds, limit).results

        override suspend fun searchWithDiagnostics(
            query: String,
            bookIds: List<String>?,
            limit: Int
        ): SearchOutcome {
            callCount++
            lastQuery = query
            lastBookIds = bookIds
            gate?.await()
            failure?.let { throw it }
            return SearchOutcome(results = results, failures = failures)
        }
    }

    private fun result(bookId: String = "jakob", position: Int = 1) = SearchResult(
        bookId = bookId,
        chapterId = position.toLong(),
        chapterNumber = 1,
        chapterPosition = position,
        verseId = position * 10L,
        verseStart = 1,
        verseEnd = 1,
        snippet = "snippet"
    )

    @Test
    fun query_is_parsed_and_debounced_before_search() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository().apply {
            results = listOf(result())
        }
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent() // start the debounce collector before emitting

        // rapid keystrokes: only the last query is searched, after debounce
        viewModel.onQueryChange("s")
        viewModel.onQueryChange("su")
        viewModel.onQueryChange("sub")
        viewModel.onQueryChange("subhagi")
        assertEquals("no search before the debounce window", 0, repository.callCount)

        advanceTimeBy(300)
        runCurrent()

        assertEquals(1, repository.callCount)
        assertEquals("\"subhagi\"", repository.lastQuery)
        assertNull(repository.lastBookIds)

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertEquals(1, state.resultCount)
        assertNull(state.error)
    }

    @Test
    fun empty_query_never_touches_the_repository() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository()
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("   ")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals("empty query must not search any database", 0, repository.callCount)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun no_results_yields_empty_state() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository()
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("zzzqqqxxx")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue(repository.callCount >= 1)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals(0, viewModel.uiState.value.resultCount)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun repository_failure_surfaces_as_controlled_error() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository().apply {
            failure = BibleDatabaseAccessException("boom")
        }
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("dev")
        advanceTimeBy(300)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertNotNull("expected a controlled error state", state.error)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun book_filter_restricts_the_search() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository().apply {
            results = listOf(result())
        }
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("dev")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(null, repository.lastBookIds)

        viewModel.onBookFilterChange("jakob")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("jakob"), repository.lastBookIds)
        assertEquals("jakob", viewModel.uiState.value.selectedBookId)
    }

    @Test
    fun partial_book_failures_surface_a_warning_with_healthy_results() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository().apply {
            results = listOf(result())
            failures = listOf(SearchFailure("Amos", "bad index"))
        }
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("dev")
        advanceTimeBy(300)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertNull(state.error)
        assertNotNull("expected a partial-result warning", state.warning)
    }

    @Test
    fun all_books_failing_yields_a_controlled_error() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository().apply {
            failures = listOf(SearchFailure("Amos", "bad index"), SearchFailure("Esther", "bad index"))
        }
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("dev")
        advanceTimeBy(300)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertNotNull("expected a controlled error when every book fails", state.error)
        assertNull(state.warning)
    }

    @Test
    fun newer_query_cancels_the_inflight_search() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeSearchRepository().apply {
            this.gate = gate
            results = listOf(result(bookId = "jakob", position = 2))
        }
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), SavedStateHandle(), FakeBookNameRepository())
        runCurrent()

        viewModel.onQueryChange("first")
        advanceTimeBy(300)
        runCurrent() // search "first" starts and suspends on the gate

        viewModel.onQueryChange("second")
        advanceTimeBy(300)
        runCurrent() // collectLatest cancels "first", starts "second" (suspended too)

        gate.complete(Unit)
        advanceUntilIdle()

        // only the last query's search completes and publishes results
        assertEquals("\"second\"", repository.lastQuery)
        assertEquals(2, viewModel.uiState.value.results.first().chapterPosition)
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun query_and_filter_restore_from_saved_state_and_updates_are_persisted() = runTest(dispatcher.scheduler) {
        val handle = SavedStateHandle(
            mapOf("search_query" to "subhagi", "search_book_filter" to "jakob")
        )
        val repository = FakeSearchRepository()
        val viewModel = SearchViewModel(repository, BibleSearchQueryParser(), handle, FakeBookNameRepository())
        runCurrent()

        assertEquals("subhagi", viewModel.uiState.value.query)
        assertEquals("jakob", viewModel.uiState.value.selectedBookId)

        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(
            "the restored query must re-run with the restored book filter",
            listOf("jakob"),
            repository.lastBookIds
        )

        viewModel.onQueryChange("dev")
        viewModel.onBookFilterChange(null)
        assertEquals("dev", handle.get<String>("search_query"))
        assertEquals(null, handle.get<String>("search_book_filter"))
    }

    @Test
    fun book_names_cover_result_ids_with_a_catalog_fallback() = runTest(dispatcher.scheduler) {
        val repository = FakeSearchRepository().apply {
            results = listOf(result(bookId = "jakob"))
        }
        val viewModel = SearchViewModel(
            repository,
            BibleSearchQueryParser(),
            SavedStateHandle(),
            FakeBookNameRepository()
        )
        runCurrent()

        viewModel.onQueryChange("subhagi")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals("Jakobachem", viewModel.uiState.value.bookNames["jakob"])
    }
}
