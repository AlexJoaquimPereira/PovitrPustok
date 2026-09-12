package com.biblia.povitrpustok.feature.dailyreading

import com.biblia.povitrpustok.domain.model.LiturgicalDay

/**
 * Daily Reading screen state (Phase 10, plan §12).
 */
sealed interface DailyReadingUiState {
    data object Loading : DailyReadingUiState
    data class Success(val day: LiturgicalDay) : DailyReadingUiState
    data class Error(val message: String) : DailyReadingUiState
}
