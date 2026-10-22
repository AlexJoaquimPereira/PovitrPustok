package com.biblia.povitrpustok.domain.model

/**
 * A bookmark identifying an exact Bible location (implementation plan §3–§5).
 *
 * Stable navigation identity: [bookId] + [chapterPosition] + [verseStart]/
 * [verseEnd]. [chapterId]/[verseId] are corpus row ids captured when the
 * bookmark was created; the prepared assets are regenerated on app update,
 * so row ids must never be used to reopen a bookmark (BOOKMARK-01).
 * [chapterNumber] is a display copy only.
 */
data class Bookmark(
    val id: Long,
    val bookId: String,
    val chapterId: Long,
    val chapterPosition: Int,
    val verseId: Long,
    val chapterNumber: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val createdAt: Long
) {

    /**
     * Reader navigation destination for this bookmark (F-05): open the full
     * chapter at the bookmarked verse instead of an isolated passage.
     *
     * Only stable location fields are used — [chapterPosition] and the verse
     * number. Corpus row ids are never used, because the prepared assets are
     * regenerated on app updates (BOOKMARK-01).
     */
    fun toReaderDestination(): ReaderDestination =
        ReaderDestination(
            bookId = bookId,
            chapterPosition = chapterPosition,
            targetVerseNumber = verseStart
        )
}
