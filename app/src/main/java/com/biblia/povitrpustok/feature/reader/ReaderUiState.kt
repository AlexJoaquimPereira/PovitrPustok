package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.Verse

/**
 * Immutable reader state (implementation plan §17, §48).
 *
 * Holds only what the reader needs to render: the selected book, the
 * current chapter plus its headings/verses, and the book's chapter list
 * (used by the chapter selector and previous/next boundaries). Book
 * introductions are shown by the separate book-introduction screen, not
 * the Reader (READER-03).
 *
 * Chapter identity is preserved exactly as the repository returns it:
 *  - [Chapter.id] internal identity
 *  - [Chapter.number] source/display number (not unique — e.g. Job)
 *  - [Chapter.position] source order used for navigation
 */
data class ReaderUiState(
    val book: BibleBook? = null,
    val chapter: Chapter? = null,
    val chapters: List<Chapter> = emptyList(),
    val headings: List<Heading> = emptyList(),
    val verses: List<Verse> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Transient one-shot verse target (Phase 8): the id of the verse to
     * scroll to/highlight after the chapter loads. Consumed once by
     * [ReaderViewModel.onTargetApplied] so recomposition never re-scrolls.
     */
    val targetVerseId: Long? = null,
    /**
     * Temporary highlighted verse (Phase 8): set when the target is
     * applied, cleared after a short duration or on navigation. Purely
     * presentational — Bible content is never modified.
     */
    val highlightedVerseId: Long? = null,
    /** True when a target was requested but does not exist in this chapter. */
    val targetMissing: Boolean = false,
    /**
     * Ids of verses in the current chapter that are bookmarked (Phase 9).
     * Chapter-scoped, observed from the application database; the set is
     * a Set (not per-verse queries — no N+1).
     */
    val bookmarkedVerseIds: Set<Long> = emptySet(),
    /** The verse the bookmark action operates on (Phase 9): the applied
     * navigation target. Distinct from the transient [highlightedVerseId].
     */
    val selectedVerseId: Long? = null,
    /**
     * Optional requested passage (corrective phase Issue 5): when set, the
     * Reader displays only that passage instead of the whole chapter.
     */
    val passage: ReaderPassage? = null
) {

    /** Previous is available only inside the book (position-based). */
    val canGoPrevious: Boolean
        get() = chapter != null &&
            chapters.isNotEmpty() &&
            chapter!!.position > chapters.first().position

    /** Next is available only inside the book (position-based). */
    val canGoNext: Boolean
        get() = chapter != null &&
            chapters.isNotEmpty() &&
            chapter!!.position < chapters.last().position
}
