package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.Chapter

/**
 * Immutable state for the book-chapters screen (implementation plan §8, §9).
 *
 * [chapters] preserves each record's id, number, and position separately;
 * duplicate source chapter numbers (Job) are never collapsed.
 */
data class BookChaptersUiState(
    val book: BibleBook? = null,
    val bookId: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
