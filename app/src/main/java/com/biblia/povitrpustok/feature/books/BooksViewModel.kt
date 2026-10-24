package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads database-sourced book display names once per process
 * (corrective phase Issue 1; F-03). The repository caches titles, so the
 * Books screen never reopens databases on recomposition.
 *
 * Failure policy (F-03): the catalog is always renderable, so a failed scan
 * must never leave a permanent spinner. The state degrades to
 * `isLoading = false, titlesFailed = true` and [retry] re-runs the scan.
 * Cancellation is never swallowed; a single in-flight job is kept so
 * repeated retries cannot duplicate a corpus scan.
 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val titleRepository: BookTitleRepository,
    bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    private var titlesJob: Job? = null

    init {
        loadTitles()
        // F-20: mark the book of the stored reading position. A stale id
        // (book no longer in the catalog) simply shows no marker.
        viewModelScope.launch {
            bookmarkRepository.observeReadingState().collect { state ->
                val bookId = state?.bookId?.takeIf { BibleCatalog.byId(it) != null }
                _uiState.update { it.copy(lastReadBookId = bookId) }
            }
        }
    }

    /** Re-runs the title scan after a failure (banner Retry button). */
    fun retry() = loadTitles()

    private fun loadTitles() {
        if (titlesJob?.isActive == true) return
        titlesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, titlesFailed = false) }
            try {
                val displayNames = titleRepository.loadAllDisplayNames()
                _uiState.update {
                    it.copy(
                        displayNames = displayNames,
                        isLoading = false,
                        titlesFailed = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No raw exception text in state: the UI shows a localized
                // banner and keeps the catalog names (F-03).
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        titlesFailed = true
                    )
                }
            }
        }
    }
}
