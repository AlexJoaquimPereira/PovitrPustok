package com.biblia.povitrpustok.domain.model

/**
 * A resolved reference: a stable internal Bible location (implementation
 * plan §16, §14).
 *
 * [chapterId] is the internal chapter identity; [chapterPosition] is the
 * source-order position used by the Reader navigation contract
 * (bookId + chapterPosition). A verse target is optional and kept separate
 * from the chapter identity — it is never substituted for
 * [chapterPosition] and never derived from chapter numbers.
 */
data class ResolvedScriptureReference(
    val bookId: String,
    val chapterId: Long,
    val chapterNumber: Int,
    val chapterPosition: Int,
    val verseId: Long? = null,
    val verseStart: Int? = null,
    val verseEnd: Int? = null
) {

    /** Chapter-navigation destination for the existing reader route. */
    fun toReaderDestination(): ReaderDestination =
        ReaderDestination(
            bookId = bookId,
            chapterPosition = chapterPosition,
            verseId = verseId,
            verseStart = verseStart,
            verseEnd = verseEnd
        )
}
