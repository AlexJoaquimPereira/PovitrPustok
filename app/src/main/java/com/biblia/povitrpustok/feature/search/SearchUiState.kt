package com.biblia.povitrpustok.feature.search

import com.biblia.povitrpustok.domain.model.SearchResult

/**
 * Immutable search state (implementation plan §23) exposed via StateFlow.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    /** Non-blocking partial-result notice (SEARCH-02); null when complete. */
    val warning: String? = null,
    val selectedBookId: String? = null,
    val resultCount: Int? = null
)
