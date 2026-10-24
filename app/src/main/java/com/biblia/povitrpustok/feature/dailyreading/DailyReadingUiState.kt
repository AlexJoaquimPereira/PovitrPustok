package com.biblia.povitrpustok.feature.dailyreading

import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalErrorKind

/**
 * Daily Reading screen state (Phase 10, plan §12; F-18 freshness).
 *
 * [Success.cachedAt] is the epoch millis when the payload was first fetched;
 * `null` means it came straight from the network on this load.
 *
 * [Success.refreshFailed] is true when a forced "Update" could not reach the
 * network and the saved payload was shown instead (P1-2), so the screen can
 * say the update failed rather than implying the data is current.
 */
sealed interface DailyReadingUiState {
    data object Loading : DailyReadingUiState

    data class Success(
        val day: LiturgicalDay,
        val cachedAt: Long? = null,
        val refreshFailed: Boolean = false
    ) : DailyReadingUiState

    /** F-24: a typed reason; the screen owns the localized copy. */
    data class Error(val kind: LiturgicalErrorKind) : DailyReadingUiState
}
