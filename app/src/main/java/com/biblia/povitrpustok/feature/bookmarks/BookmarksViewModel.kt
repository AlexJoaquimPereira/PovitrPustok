package com.biblia.povitrpustok.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val bookmarkRepository: BookmarkRepository,
    private val bookNames: BookNameRepository,
    private val readingPreferences: ReadingPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The reading edition decides the display language; names are
            // resolved again when Settings changes it (F-12, F-13).
            readingPreferences.preferences
                .map { it.readingEditionId }
                .distinctUntilChanged()
                .collectLatest { editionId ->
                    _uiState.update { it.copy(editionId = editionId) }
                    // Emit the list immediately with catalog-fallback names,
                    // then refine with corpus names. A slow lookup never
                    // delays the list and a newer emission wins (F-12, S5).
                    bookmarkRepository.observeBookmarks().collectLatest { bookmarks ->
                        _uiState.value = BookmarksUiState(
                            bookmarks = bookmarks,
                            editionId = editionId,
                            isLoading = false
                        )
                        val names = bookNames.displayNames(
                            bookmarks.map { it.bookId }.distinct(),
                            editionId
                        )
                        _uiState.update { state ->
                            if (state.bookmarks == bookmarks) state.copy(bookNames = names) else state
                        }
                    }
                }
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmarkById(id)
        }
    }

    /**
     * F-09 Undo: re-adds a removed bookmark from the fields it already
     * carried. The DAO's unique index still prevents duplicates if the user
     * re-bookmarks the same place while the snackbar is visible.
     */
    fun restoreBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(
                anchor = bookmark.anchor,
                chapterNumber = bookmark.chapterNumber
            )
        }
    }
}
