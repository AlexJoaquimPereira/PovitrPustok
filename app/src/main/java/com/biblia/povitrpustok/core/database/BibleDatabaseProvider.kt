package com.biblia.povitrpustok.core.database

/**
 * Provides read-only access to the imported per-book Bible databases
 * (implementation plan §11).
 *
 * Responsibilities: resolve a book to its database asset, lazily make the
 * database available (copying the asset to internal storage only when a
 * book is first opened), reuse prepared handles, and treat the Bible
 * databases as immutable content. Never preloads the corpus.
 */
interface BibleDatabaseProvider {

    /**
     * Opens (preparing on first use) the database for [bookId] and returns
     * a read-only handle. Handles are cached; repeated calls for the same
     * book return the same handle.
     *
     * @throws com.biblia.povitrpustok.core.result.BibleBookNotFoundException
     * @throws com.biblia.povitrpustok.core.result.BibleDatabaseNotFoundException
     * @throws com.biblia.povitrpustok.core.result.CorruptBibleDatabaseException
     */
    suspend fun openBook(bookId: String): BibleDatabase

    /**
     * Closes all cached handles and permanently closes this provider
     * (PROVIDER-01): repeated calls are no-ops, and any later [openBook]
     * throws [IllegalStateException]. Handles returned before close remain
     * references but fail fast if used (closed SQLite connection).
     * Production keeps the provider for the process lifetime; this exists
     * for tests/lifecycle teardown.
     */
    fun close()
}
