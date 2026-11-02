package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.BibleEditions

/**
 * Single source of human-readable book display names (F-12).
 *
 * Screens must not each combine corpus titles and catalog names on their
 * own: this repository resolves the corpus title first and falls back to the
 * catalog display name (id as the last resort), so a screen never has to
 * block on title loading or render a blank book name.
 *
 * [editionId] selects the language of the name: the Konkani edition resolves
 * the corpus title, an English edition uses the catalog's English name.
 */
interface BookNameRepository {

    /**
     * Corpus title first, catalog display name fallback, id as last resort.
     * Never throws for a missing/corrupt book.
     */
    suspend fun displayName(bookId: String): String

    /**
     * Edition-aware name. The default keeps the existing (Konkani) behaviour
     * for implementations and callers that are not edition-aware yet.
     */
    suspend fun displayName(
        bookId: String,
        editionId: String = BibleEditions.KONKANI_ID
    ): String = displayName(bookId)

    /**
     * Resolves names for [bookIds]; unresolved/absent ids still get the
     * catalog/id fallback.
     */
    suspend fun displayNames(bookIds: Collection<String>): Map<String, String>

    /** Edition-aware bulk resolution; defaults to the Konkani behaviour. */
    suspend fun displayNames(
        bookIds: Collection<String>,
        editionId: String = BibleEditions.KONKANI_ID
    ): Map<String, String> = displayNames(bookIds)
}
