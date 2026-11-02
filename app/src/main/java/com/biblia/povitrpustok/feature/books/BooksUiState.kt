package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.BibleEditions

/**
 * Immutable state for the Books screen (corrective phase Issue 1; F-03):
 * database-sourced display names, loaded once and cached.
 *
 * The screen is never blocked by title loading: [isLoading] defaults to
 * `false` because the static 73-book catalog can always be rendered. A
 * failed title scan only raises [titlesFailed] (never raw exception text);
 * the UI then keeps showing catalog names and offers Retry.
 *
 * [editionId] is the reading edition the list is scoped to; [displayNames]
 * is only populated for the Konkani corpus (English editions use the
 * catalog's English names directly).
 */
data class BooksUiState(
    val displayNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val titlesFailed: Boolean = false,
    /** Book of the stored reading position (F-20); null when there is none. */
    val lastReadBookId: String? = null,
    /** Selected reading edition; the list shows this edition's canon. */
    val editionId: String = BibleEditions.KONKANI_ID
)
