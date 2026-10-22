package com.biblia.povitrpustok.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _readingState = MutableStateFlow<ReadingState?>(null)
    val readingState: StateFlow<ReadingState?> = _readingState.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkRepository.observeReadingState()
                .catch { emit(null) }
                .collect { state ->
                if (state != null && BibleCatalog.byId(state.bookId) == null) {
                    bookmarkRepository.clearReadingState()
                    _readingState.value = null
                } else {
                    _readingState.value = state
                }
            }
        }
    }
}
