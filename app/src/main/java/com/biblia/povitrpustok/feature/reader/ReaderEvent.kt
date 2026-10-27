package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.VerseAnchor

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
        val anchor: VerseAnchor,
        val chapterNumber: Int,
        /** True when the toggle added the bookmark; false when it removed it. */
        val added: Boolean,
        /** Resolved name at toggle time, so a queued confirmation cannot be relabelled by later navigation. */
        val bookName: String
    ) : ReaderEvent
}
