package com.biblia.povitrpustok.domain.model

/**
 * Stable identity for a verse or source range (F1-09).
 *
 * Corpus row ids change when the assets are regenerated, so user-owned
 * records never key by [com.biblia.povitrpustok.domain.model.Verse.id].
 * The anchor uses the book id, source chapter position, and source verse
 * range; offsets are reserved for future rendered-text annotations.
 */
data class VerseAnchor(
    val bookId: String,
    val chapterPosition: Int,
    val verseStart: Int,
    val verseEnd: Int = verseStart,
    val startOffset: Int = 0,
    val endOffset: Int = 0
) {
    init {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(chapterPosition >= 1) { "chapterPosition must be positive" }
        require(verseStart >= 1) { "verseStart must be positive" }
        require(verseEnd >= verseStart) { "verseEnd must not precede verseStart" }
        require(startOffset >= 0) { "startOffset must not be negative" }
        require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    }
}
