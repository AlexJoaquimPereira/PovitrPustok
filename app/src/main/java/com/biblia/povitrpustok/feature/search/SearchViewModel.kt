package com.biblia.povitrpustok.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
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
    private val queryParser: BibleSearchQueryParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryInput = MutableStateFlow("")
    private val bookFilter = MutableStateFlow<String?>(null)

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
        _uiState.update { it.copy(query = rawQuery) }
        queryInput.value = rawQuery
    }

    fun onBookFilterChange(bookId: String?) {
        _uiState.update { it.copy(selectedBookId = bookId) }
        bookFilter.value = bookId
    }

    private suspend fun runSearch(rawQuery: String, bookId: String?) {
        val expression = queryParser.parse(rawQuery)
        _uiState.update { it.copy(isSearching = true, error = null, warning = null) }
        if (expression == null) {
            _uiState.update {
                it.copy(results = emptyList(), isSearching = false, resultCount = null, warning = null)
            }
            return
        }
        try {
            val outcome = searchRepository.searchWithDiagnostics(
                query = expression,
                bookIds = bookId?.let { listOf(it) }
            )
            val partial = outcome.results.isNotEmpty() && outcome.failures.isNotEmpty()
            _uiState.update {
                it.copy(
                    results = outcome.results,
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
        } catch (e: BibleDataException) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
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
    }
}
