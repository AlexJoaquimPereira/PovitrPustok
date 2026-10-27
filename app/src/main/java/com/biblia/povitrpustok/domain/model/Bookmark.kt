package com.biblia.povitrpustok.domain.model

/**
 * A bookmark identifying an exact Bible location (implementation plan §3–§5).
 *
 * [id] is a UUID owned by the app database. The stable content identity is
 * [anchor]: book id + chapter position + source verse range. Corpus row ids
 * are deliberately absent — they change when the assets are regenerated
 * (BOOKMARK-01), so they can never be user-data identity.
 *
 * [chapterNumber] is a display copy only. [deletedAt] is a tombstone reserved
 * for the future export/merge format.
 */
data class Bookmark(
    val id: String,
    val anchor: VerseAnchor,
    val chapterNumber: Int,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
) {
    val bookId: String get() = anchor.bookId
    val chapterPosition: Int get() = anchor.chapterPosition
    val verseStart: Int get() = anchor.verseStart
    val verseEnd: Int get() = anchor.verseEnd

    /**
     * Reader navigation destination (F-05): open the full chapter at the
     * bookmarked verse instead of an isolated passage.
     */
    fun toReaderDestination(): ReaderDestination =
        ReaderDestination(
            bookId = anchor.bookId,
            chapterPosition = anchor.chapterPosition,
            targetVerseNumber = anchor.verseStart
        )
}
