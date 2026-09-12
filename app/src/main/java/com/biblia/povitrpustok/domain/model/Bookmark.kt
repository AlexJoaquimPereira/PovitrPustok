package com.biblia.povitrpustok.domain.model

/**
 * A bookmark identifying an exact Bible location (implementation plan §3–§5).
 *
 * Identity is the stable internal location: [bookId] + [chapterId] +
 * [verseId]; [chapterPosition] supports Reader navigation. Display values
 * ([chapterNumber], [verseStart], [verseEnd]) are convenience copies for
 * the bookmark list and are never used for identity.
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

    /** Reader navigation destination for this bookmark. */
    fun toReaderDestination(): ReaderDestination =
        ReaderDestination(
            bookId = bookId,
            chapterPosition = chapterPosition,
            verseId = verseId,
            verseStart = verseStart,
            verseEnd = verseEnd
        )
}
