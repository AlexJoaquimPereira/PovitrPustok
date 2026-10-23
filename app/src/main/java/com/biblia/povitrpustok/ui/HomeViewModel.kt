package com.biblia.povitrpustok.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What Home needs to offer "Continue reading" (F-11): the stored location
 * plus the display-ready book name resolved by the single name source
 * (F-12), so the card can say where it will go instead of just "Continue".
 *
 * No chapter number is derived from [ReadingState.chapterPosition]: source
 * numbers repeat (Job), so a "Chapter N" built from the position would be
 * wrong; the card shows the book and when it was last opened instead.
 */
data class ContinueReading(
    val reading: ReadingState,
    val bookName: String
)

/** Home state (F-11): an absent [continueReading] hides the card entirely. */
data class HomeUiState(
    val continueReading: ContinueReading? = null
)

/**
 * Home state holder (Phase 9, plan §14): exposes the last reading location
 * so Home can offer "Continue reading".
 *
 * A stored book that no longer exists in the catalog is treated as stale:
 * it is cleared (gracefully) and "Continue reading" is hidden. Chapter
 * invalidity is handled by the Reader's existing error state — no crash.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val bookNameRepository: BookNameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkRepository.observeReadingState()
                // A corrupt reading-state row must not take Home down.
                .catch { emit(null) }
                .collectLatest { state -> update(state) }
        }
    }

    private suspend fun update(state: ReadingState?) {
        if (state == null) {
            _uiState.value = HomeUiState()
            return
        }
        if (BibleCatalog.byId(state.bookId) == null) {
            bookmarkRepository.clearReadingState()
            _uiState.value = HomeUiState()
            return
        }
        _uiState.value = HomeUiState(
            continueReading = ContinueReading(
                reading = state,
                bookName = bookNameRepository.displayName(state.bookId)
            )
        )
    }
}
