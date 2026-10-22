package com.biblia.povitrpustok.feature.reader

/**
 * One-shot reader events consumed by the screen (never stored in
 * [ReaderUiState], so recomposition cannot replay them).
 */
sealed interface ReaderEvent {

    /**
     * A long-press bookmark toggle completed. [added] says whether the verse
     * is now bookmarked; the screen confirms it with a snackbar whose action
     * toggles the same verse back (F-09).
     */
    data class BookmarkChanged(
        val verseId: Long,
        val added: Boolean,
        val chapterNumber: Int,
        val verseStart: Int
    ) : ReaderEvent
}
