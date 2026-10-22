package com.biblia.povitrpustok.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search state holder (implementation plan §23).
 *
 * Pipeline: query change -> debounce -> parse/normalize -> search
 * repository -> results. [collectLatest] cancels any in-flight search when
 * a newer query arrives, so rapid typing never launches uncancelled
 * database searches; no GlobalScope is used. Query input is a
 * latest-wins [StateFlow] (never drops the newest keystroke).
 *
 * An empty/whitespace query is parsed to `null` and never reaches the
 * database (it must never become `MATCH *`). All BibleDataExceptions
 * surface as a controlled error state.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: BibleSearchRepository,
    private val queryParser: BibleSearchQueryParser,
    private val savedStateHandle: SavedStateHandle,
    private val bookNames: BookNameRepository
) : ViewModel() {

    // DIST-01: the query and book filter survive process recreation; the
    // debounced pipeline re-runs the search against the restored query.
    private val _uiState = MutableStateFlow(
        SearchUiState(
            query = savedStateHandle[KEY_QUERY] ?: "",
            selectedBookId = savedStateHandle[KEY_BOOK_FILTER]
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryInput = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY) ?: "")
    private val bookFilter = MutableStateFlow(savedStateHandle.get<String>(KEY_BOOK_FILTER))

    init {
        viewModelScope.launch {
            combine(
                queryInput.debounce(DEBOUNCE_MILLIS),
                bookFilter
            ) { query, filter -> query to filter }
                .distinctUntilChanged()
                .collectLatest { (query, filter) ->
                    runSearch(query, filter)
                }
        }
    }

    fun onQueryChange(rawQuery: String) {
        savedStateHandle[KEY_QUERY] = rawQuery
        _uiState.update { it.copy(query = rawQuery) }
        queryInput.value = rawQuery
    }

    fun onBookFilterChange(bookId: String?) {
        savedStateHandle[KEY_BOOK_FILTER] = bookId
        _uiState.update { it.copy(selectedBookId = bookId) }
        bookFilter.value = bookId
    }

    /** Re-runs the current query after a controlled error (Day 4 retry). */
    fun retry() {
        viewModelScope.launch {
            runSearch(_uiState.value.query, _uiState.value.selectedBookId)
        }
    }

    private suspend fun runSearch(rawQuery: String, bookId: String?) {
        val expression = queryParser.parse(rawQuery)
        _uiState.update { it.copy(isSearching = true, error = null, warning = null) }
        if (expression == null) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    bookNames = emptyMap(),
                    isSearching = false,
                    resultCount = null,
                    warning = null
                )
            }
            return
        }
        try {
            val outcome = searchRepository.searchWithDiagnostics(
                query = expression,
                bookIds = bookId?.let { listOf(it) }
            )
            val partial = outcome.results.isNotEmpty() && outcome.failures.isNotEmpty()
            // Emit results first: the rows fall back to catalog names, so the
            // list never waits for corpus name lookups (F-12, S5).
            _uiState.update {
                it.copy(
                    results = outcome.results,
                    bookNames = emptyMap(),
                    isSearching = false,
                    resultCount = outcome.results.size,
                    error = if (outcome.results.isEmpty() && outcome.failures.isNotEmpty()) {
                        "Search failed for ${outcome.failures.size} book(s)."
                    } else {
                        null
                    },
                    warning = if (partial) {
                        "Partial results: ${outcome.failures.size} book(s) could not be searched."
                    } else {
                        null
                    }
                )
            }
            if (outcome.results.isNotEmpty()) {
                // Refinement is best-effort: a name failure must not escape
                // and a superseded run must not publish a stale map.
                try {
                    val names = bookNames.displayNames(outcome.results.map { it.bookId }.distinct())
                    _uiState.update { state ->
                        if (state.results == outcome.results) {
                            state.copy(bookNames = names)
                        } else {
                            state
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Catalog fallbacks already render; nothing else to do.
                }
            }
        } catch (e: BibleDataException) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    bookNames = emptyMap(),
                    isSearching = false,
                    error = e.message ?: "Search failed.",
                    warning = null,
                    resultCount = null
                )
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val KEY_QUERY = "search_query"
        const val KEY_BOOK_FILTER = "search_book_filter"
    }
}
