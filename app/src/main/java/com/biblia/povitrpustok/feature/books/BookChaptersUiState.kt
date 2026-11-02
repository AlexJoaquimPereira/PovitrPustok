package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.Chapter

/**
 * Immutable state for the book-chapters screen (implementation plan §8, §9).
 *
 * [chapters] preserves each record's id, number, and position separately;
 * duplicate source chapter numbers (Job) are never collapsed.
 *
 * [displayName] is the resolved edition-aware name (F-12), so the screen
 * never renders a blank title.
 *
 * [editionId] identifies the edition the chapters were loaded from, so the
 * screen can hide the Konkani-only introduction entry point.
 *
 * [error] is a reason, not English (F-24): the screen owns the localized copy.
 */
data class BookChaptersUiState(
    val book: BibleBook? = null,
    val bookId: String? = null,
    val displayName: String = "",
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = false,
    val error: ChaptersError? = null,
    val editionId: String = BibleEditions.KONKANI_ID
)
