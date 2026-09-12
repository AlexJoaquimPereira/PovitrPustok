package com.biblia.povitrpustok.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bookmarks screen state holder (Phase 9, plan §17, §11).
 *
 * Observes the domain bookmark list and deletes through the repository;
 * the UI updates via the observable state, never by manual screen
 * reconstruction.
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkRepository.observeBookmarks().collect { bookmarks ->
                _uiState.value = BookmarksUiState(bookmarks = bookmarks, isLoading = false)
            }
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmarkById(id)
        }
    }
}
