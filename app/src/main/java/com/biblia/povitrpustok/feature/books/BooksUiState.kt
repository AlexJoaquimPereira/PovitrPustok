package com.biblia.povitrpustok.feature.books

/**
 * Immutable state for the Books screen (corrective phase Issue 1):
 * database-sourced display names, loaded once and cached.
 */
data class BooksUiState(
    val displayNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true
)
