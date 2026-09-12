package com.biblia.povitrpustok.feature.bookmarks

import com.biblia.povitrpustok.domain.model.Bookmark

/**
 * Immutable state for the bookmarks screen (Phase 9).
 */
data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = true
)
