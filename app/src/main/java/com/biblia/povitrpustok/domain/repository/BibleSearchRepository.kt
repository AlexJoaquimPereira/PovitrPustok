package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.core.result.BibleEditionNotFoundException
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.SearchOutcome
import com.biblia.povitrpustok.domain.model.SearchResult

/**
 * Full-text search over the imported per-book FTS5 indexes
 * (implementation plan §15).
 *
 * [query] is expected to be a safe FTS5 expression prepared by
 * BibleSearchQueryParser; the implementation guards blank input and never
 * concatenates raw user input into MATCH expressions itself.
 *
 * Results follow the SEARCH-01 contract: within each book hits are ordered
 * by FTS5 relevance (`rank`/BM25); books are then merged by rank position in
 * canonical book order, and the global [search] limit is applied after the
 * merge. Ties use chapter.position, verseStart, verseId.
 */
interface BibleSearchRepository {

    /**
     * Searches the whole corpus, or only [bookIds] when provided.
     *
     * @param query the prepared FTS5 expression; blank input returns no results
     * @param bookIds optional filter by BibleBook.id; null searches all books
     * @param limit maximum number of results returned
     */
    suspend fun search(
        query: String,
        bookIds: List<String>? = null,
        limit: Int = 50
    ): List<SearchResult>

    /** Edition-aware [search]; only the shipped Konkani edition is available. */
    suspend fun search(
        query: String,
        bookIds: List<String>? = null,
        limit: Int = 50,
        editionId: String
    ): List<SearchResult> = defaultEdition(editionId).let { search(query, bookIds, limit) }

    /**
     * SEARCH-02: like [search], but a failure in one book's index does not
     * erase results from the rest of the corpus. [SearchOutcome.failures]
     * retains the skipped books (and a reason) for diagnostics.
     *
     * Implementations must isolate per-book data failures; cancellation
     * must always propagate.
     */
    suspend fun searchWithDiagnostics(
        query: String,
        bookIds: List<String>? = null,
        limit: Int = 50
    ): SearchOutcome

    /** Edition-aware [searchWithDiagnostics]; only Konkani is available. */
    suspend fun searchWithDiagnostics(
        query: String,
        bookIds: List<String>? = null,
        limit: Int = 50,
        editionId: String
    ): SearchOutcome = defaultEdition(editionId).let {
        searchWithDiagnostics(query, bookIds, limit)
    }

    private fun defaultEdition(editionId: String) {
        if (editionId != BibleEditions.KONKANI_ID) {
            throw BibleEditionNotFoundException(editionId)
        }
    }
}
