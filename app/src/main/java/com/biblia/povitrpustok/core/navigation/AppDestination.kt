package com.biblia.povitrpustok.core.navigation

/**
 * Navigation route contract (implementation plan §31).
 *
 * Routes identify content semantically; navigation arguments are small
 * identifiers (book id, chapter position) — never chapter HTML, verse
 * lists, or full content objects. Stable ids are the navigation contract.
 */
object AppDestination {

    const val HOME = "home"

    const val BOOKS = "books"

    /** books/{bookId} — chapter selection for one book. */
    const val BOOK_CHAPTERS = "books/{bookId}"

    /** books/{bookId}/introduction — the book's introduction blocks. */
    const val BOOK_INTRODUCTION = "books/{bookId}/introduction"

    /**
     * reader/{bookId}/{chapterPosition}?verseId={verseId}&verseStart={verseStart}&verseEnd={verseEnd}
     *
     * [verseId] is an optional verse target; [verseStart]/[verseEnd] form an
     * optional requested passage (targeted Reader mode). All are optional
     * and kept separate from the chapter navigation identity
     * (chapterPosition). Existing callers that omit them still match.
     */
    const val READER = "reader/{bookId}/{chapterPosition}?verseId={verseId}&verseStart={verseStart}&verseEnd={verseEnd}"

    const val SEARCH = "search"

    const val REFERENCE_ENTRY = "reference"

    const val DAILY_READING = "daily-reading"

    const val BOOKMARKS = "bookmarks"

    const val SETTINGS = "settings"

    fun bookChapters(bookId: String): String = "books/$bookId"

    fun bookIntroduction(bookId: String): String = "books/$bookId/introduction"

    fun reader(
        bookId: String,
        chapterPosition: Int,
        verseId: Long? = null,
        verseStart: Int? = null,
        verseEnd: Int? = null
    ): String {
        val base = "reader/$bookId/$chapterPosition"
        val params = buildList {
            verseId?.let { add("verseId=$it") }
            verseStart?.let { add("verseStart=$it") }
            verseEnd?.let { add("verseEnd=$it") }
        }
        return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
    }
}
