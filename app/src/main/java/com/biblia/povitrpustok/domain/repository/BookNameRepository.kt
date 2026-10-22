package com.biblia.povitrpustok.domain.repository

/**
 * Single source of human-readable book display names (F-12).
 *
 * Screens must not each combine corpus titles and catalog names on their
 * own: this repository resolves the corpus title first and falls back to the
 * catalog display name (id as the last resort), so a screen never has to
 * block on title loading or render a blank book name.
 */
interface BookNameRepository {

    /**
     * Corpus title first, catalog display name fallback, id as last resort.
     * Never throws for a missing/corrupt book.
     */
    suspend fun displayName(bookId: String): String

    /**
     * Resolves names for [bookIds]; unresolved/absent ids still get the
     * catalog/id fallback.
     */
    suspend fun displayNames(bookIds: Collection<String>): Map<String, String>
}
