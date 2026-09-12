package com.biblia.povitrpustok.domain.repository

/**
 * Provides human-readable book display names sourced from each book's own
 * database (introduction_blocks `title` block), cached for the process
 * (corrective phase Issue 1).
 *
 * BibleCatalog remains authoritative for id, database asset, and stable
 * identity; this repository only supplies the *display* name.
 */
interface BookTitleRepository {

    /** Loads (and caches) the display name for one book, or null if absent. */
    suspend fun getDisplayName(bookId: String): String?

    /** Loads (and caches) display names for every catalog book. */
    suspend fun loadAllDisplayNames(): Map<String, String>
}
