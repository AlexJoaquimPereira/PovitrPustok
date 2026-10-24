package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.IntroductionBlock

/**
 * Immutable state for the book-introduction screen
 * (implementation plan §11, §12). Multiple IntroductionBlock records are
 * preserved individually and never flattened.
 *
 * [error] is a reason, not English (F-24): the screen owns the localized copy.
 */
data class BookIntroductionUiState(
    val bookId: String? = null,
    /** Display-ready book name (F-21): the bar names the book, not just "Introduction". */
    val bookDisplayName: String? = null,
    val blocks: List<IntroductionBlock> = emptyList(),
    val isLoading: Boolean = false,
    val error: IntroductionError? = null
)
