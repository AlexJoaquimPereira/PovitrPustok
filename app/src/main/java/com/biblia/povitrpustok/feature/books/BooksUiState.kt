package com.biblia.povitrpustok.feature.books

/**
 * Immutable state for the Books screen (corrective phase Issue 1; F-03):
 * database-sourced display names, loaded once and cached.
 *
 * The screen is never blocked by title loading: [isLoading] defaults to
 * `false` because the static 73-book catalog can always be rendered. A
 * failed title scan only raises [titlesFailed] (never raw exception text);
 * the UI then keeps showing catalog names and offers Retry.
 */
data class BooksUiState(
    val displayNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val titlesFailed: Boolean = false
)
