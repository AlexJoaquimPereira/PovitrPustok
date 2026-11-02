package com.biblia.povitrpustok.feature.bookmarks

import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.Bookmark

/**
 * Immutable state for the bookmarks screen (Phase 9).
 */
data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    /** Resolved display names for bookmark book ids (F-12). */
    val bookNames: Map<String, String> = emptyMap(),
    /** The reading edition used to resolve names; best-effort across editions. */
    val editionId: String = BibleEditions.KONKANI_ID,
    val isLoading: Boolean = true
)
