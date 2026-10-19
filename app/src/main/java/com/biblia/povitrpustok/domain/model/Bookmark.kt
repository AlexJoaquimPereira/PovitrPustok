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
     * Reader navigation destination for this bookmark, using only stable
     * location fields. The passage range identifies the verse even if the
     * corpus row ids changed since the bookmark was saved.
     */
    fun toReaderDestination(): ReaderDestination =
        ReaderDestination(
            bookId = bookId,
            chapterPosition = chapterPosition,
            verseStart = verseStart,
            verseEnd = verseEnd
        )
}
