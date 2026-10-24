package com.biblia.povitrpustok.feature.search

import com.biblia.povitrpustok.domain.model.SearchResult

/**
 * Search failure presentation, mapped to resources by the screen.
 *
 * [Failed] blocks the list (no book could be searched); [Partial] is a
 * non-blocking notice over healthy rows (SEARCH-02).
 */
sealed interface SearchIssue {
    data object Failed : SearchIssue
    data class Partial(val failedBooks: Int) : SearchIssue
}

/**
 * Immutable search state (implementation plan §23) exposed via StateFlow.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val issue: SearchIssue? = null,
    val selectedBookId: String? = null,
    val resultCount: Int? = null,
    /**
     * Resolved display names for the result book ids (F-12), keyed by book
     * id. Kept in state so rows never resolve/block on a name themselves.
     */
    val bookNames: Map<String, String> = emptyMap()
)
