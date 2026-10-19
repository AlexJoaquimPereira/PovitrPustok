package com.biblia.povitrpustok.domain.model

/**
 * A book whose index could not be searched (missing/corrupt/unsupported
 * FTS table, etc.). [reason] is a low-level diagnostic intended for
 * logs/tests only — it may contain SQLite/query detail and must never be
 * shown to the user (SEARCH-02).
 */
data class SearchFailure(
    val bookId: String,
    val reason: String
)

/**
 * Search results plus per-book diagnostics (SEARCH-02).
 *
 * [results] always contains every hit from the books that could be
 * searched; [failures] lists the books that were skipped so callers can
 * surface a partial-result warning instead of discarding valid results.
 */
data class SearchOutcome(
    val results: List<SearchResult>,
    val failures: List<SearchFailure> = emptyList()
)
