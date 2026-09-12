package com.biblia.povitrpustok.feature.reference

import com.biblia.povitrpustok.domain.model.ScriptureResolution

/**
 * Immutable state for the reference-entry screen (Phase 8, plan §9–§11).
 *
 * The screen only pipes input to the domain resolver and presents its
 * result; it never parses integers, matches books, or queries the
 * repository itself. Reader navigation is a separate consumable
 * pending-navigation event (see [ReferenceEntryViewModel.pendingNavigation]),
 * never derived from the persisted resolution — this prevents the
 * navigate-on-return loop.
 */
data class ReferenceEntryUiState(
    val query: String = "",
    val isResolving: Boolean = false,
    val resolution: ScriptureResolution? = null
)
