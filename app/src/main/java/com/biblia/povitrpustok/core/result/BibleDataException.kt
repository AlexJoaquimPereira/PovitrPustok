package com.biblia.povitrpustok.core.result

/**
 * Base class for all Bible data-layer failures (implementation plan §16).
 *
 * UI code must never receive raw SQLite exceptions; repository/provider
 * failures surface through this sealed hierarchy so the future UI can
 * map them to meaningful application errors.
 */
sealed class BibleDataException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** The book's database asset could not be found or copied. */
class BibleDatabaseNotFoundException(
    bookId: String,
    cause: Throwable? = null
) : BibleDataException("Bible database for book '$bookId' not found", cause)

/** The book database exists but cannot be opened or is structurally invalid. */
class CorruptBibleDatabaseException(
    bookId: String,
    cause: Throwable? = null
) : BibleDataException("Bible database for book '$bookId' is corrupt or unreadable", cause)

/** The requested book id does not exist in the static catalog. */
class BibleBookNotFoundException(
    bookId: String
) : BibleDataException("No book with id '$bookId' in the Bible catalog")

/** No chapter exists at the requested source-order position. */
class BibleChapterNotFoundException(
    bookId: String,
    position: Int
) : BibleDataException("No chapter at position $position in book '$bookId'")

/** A query against an otherwise valid book database failed. */
class BibleDatabaseAccessException(
    message: String,
    cause: Throwable? = null
) : BibleDataException(message, cause)
