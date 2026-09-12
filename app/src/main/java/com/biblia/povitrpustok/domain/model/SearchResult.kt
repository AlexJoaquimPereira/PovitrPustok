package com.biblia.povitrpustok.domain.model

/**
 * A full-text search result (implementation plan §15).
 *
 * Carries stable internal identifiers so the reader can open the exact
 * chapter: [bookId] + [chapterPosition] navigate, [chapterId] is the
 * internal chapter identity, [verseId]/[verseStart]/[verseEnd] identify
 * the verse. Results are never identified only by
 * book + chapterNumber + verseNumber because chapter numbers are not
 * unique in the corpus (Job is the mandatory regression case).
 *
 * [snippet] is a plain-text window from the search representation
 * (`verses.text_plain`) — never HTML.
 */
data class SearchResult(
    val bookId: String,
    val chapterId: Long,
    val chapterNumber: Int,
    val chapterPosition: Int,
    val verseId: Long,
    val verseStart: Int,
    val verseEnd: Int,
    val snippet: String
)
