package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.SearchResult

/**
 * Full-text search over the imported per-book FTS5 indexes
 * (implementation plan §15).
 *
 * [query] is expected to be a safe FTS5 expression prepared by
 * BibleSearchQueryParser; the implementation guards blank input and never
 * concatenates raw user input into MATCH expressions itself.
 *
 * Results are ordered deterministically: canonical book order (BibleCatalog
 * position), then chapter.position, then verseStart, then verseId.
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
}
