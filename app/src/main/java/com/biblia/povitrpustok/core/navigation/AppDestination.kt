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

    /** Bible tab: the existing Books catalogue. */
    const val BOOKS = "books"

    /** More hub: Search, Bookmarks, Daily Reading, Reference and Settings. */
    const val MORE = "more"

    /**
     * Destinations that carry the adaptive navigation suite. Detail screens
     * keep their own top bar and are deliberately absent.
     */
    val TOP_LEVEL: Set<String> = setOf(HOME, BOOKS, MORE)

    /** books/{bookId} — chapter selection for one book. */
    const val BOOK_CHAPTERS = "books/{bookId}"

    /** books/{bookId}/introduction — the book's introduction blocks. */
    const val BOOK_INTRODUCTION = "books/{bookId}/introduction"

    /**
     * reader/{bookId}/{chapterPosition}?verseId={verseId}&verseStart={verseStart}&verseEnd={verseEnd}
     * &passages={passages}&targetVerse={targetVerse}
     *
     * [verseId] is an optional verse target; [verseStart]/[verseEnd] form an
     * optional requested passage (targeted Reader mode); [targetVerse] is a
     * stable verse-number target used by bookmarks (row ids may change when
     * the corpus is regenerated). All are optional
     * and kept separate from the chapter navigation identity
     * (chapterPosition). Existing callers that omit them still match.
     */
    const val READER = "reader/{bookId}/{chapterPosition}?verseId={verseId}&verseStart={verseStart}&verseEnd={verseEnd}&passages={passages}&targetVerse={targetVerse}&editionId={editionId}"

    const val SEARCH = "search?editionId={editionId}"

    const val REFERENCE_ENTRY = "reference"

    const val DAILY_READING = "daily-reading"

    const val BOOKMARKS = "bookmarks"

    /** Settings: reading controls, entered from Home and the Reader. */
    const val SETTINGS = "settings"

    /** About landing page; every disclosure below it has its own route. */
    const val ABOUT = "about"

    /** Source edition: who owns the Konkani Bible text. */
    const val ABOUT_SOURCE_EDITION = "about/source-edition"

    /** API & data: the Daily Reading source, privacy and reference. */
    const val ABOUT_API = "about/api"

    /** Software licences: runtime, test and build-time components. */
    const val ABOUT_LICENSES = "about/licenses"

    /** App licence: the complete Apache-2.0 text and the NOTICE file. */
    const val ABOUT_APP_LICENSE = "about/licenses/app"

    /** Credits: the people and projects behind the app. */
    const val ABOUT_CREDITS = "about/credits"

    fun search(editionId: String? = null): String =
        if (editionId.isNullOrBlank()) "search" else "search?editionId=$editionId"

    fun bookChapters(bookId: String): String = "books/$bookId"

    fun bookIntroduction(bookId: String): String = "books/$bookId/introduction"

    fun reader(
        bookId: String,
        chapterPosition: Int,
        verseId: Long? = null,
        verseStart: Int? = null,
        verseEnd: Int? = null,
        passages: List<com.biblia.povitrpustok.domain.model.ReaderPassageSpec> = emptyList(),
        targetVerseNumber: Int? = null,
        editionId: String? = null
    ): String {
        val base = "reader/$bookId/$chapterPosition"
        val params = buildList {
            verseId?.let { add("verseId=$it") }
            verseStart?.let { add("verseStart=$it") }
            verseEnd?.let { add("verseEnd=$it") }
            if (passages.isNotEmpty()) {
                add("passages=" + com.biblia.povitrpustok.domain.model.ReaderPassageCodec.encode(passages))
            }
            targetVerseNumber?.let { add("targetVerse=$it") }
            editionId?.let { add("editionId=$it") }
        }
        return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
    }
}
