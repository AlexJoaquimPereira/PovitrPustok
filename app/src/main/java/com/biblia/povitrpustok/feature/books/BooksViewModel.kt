package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 *
 * Edition seam: the corpus title scan belongs to the Konkani edition. For an
 * English edition the screen renders the catalog's English names, so no scan
 * runs and selecting another edition resets the cached name map.
 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val titleRepository: BookTitleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readingPreferences: ReadingPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    private var titlesJob: Job? = null

    init {
        // F-20: mark the book of the stored reading position. A stale id
        // (book no longer in the catalog) simply shows no marker.
        viewModelScope.launch {
            bookmarkRepository.observeReadingState()
                // Audit P3-7: a corrupt reading-state row must not kill the
                // marker coroutine (Home treats this the same way).
                .catch { emit(null) }
                .collect { state ->
                val bookId = state?.bookId?.takeIf { BibleCatalog.byId(it) != null }
                _uiState.update { it.copy(lastReadBookId = bookId) }
            }
        }
        viewModelScope.launch {
            readingPreferences.preferences
                .map { editionOf(it.readingEditionId) }
                .distinctUntilChanged()
                .collect { editionId -> selectEdition(editionId) }
        }
    }

    /** Re-runs the title scan after a failure (banner Retry button). */
    fun retry() = loadTitles()

    private fun selectEdition(editionId: String) {
        // A scan for the previous edition must not land in the new state.
        titlesJob?.cancel()
        val english = BibleEditions.isEnglish(editionId)
        _uiState.update {
            it.copy(
                editionId = editionId,
                displayNames = emptyMap(),
                isLoading = !english,
                titlesFailed = false
            )
        }
        if (!english) loadTitles()
    }

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

    private fun editionOf(editionId: String): String =
        BibleEditions.byId(editionId)?.id ?: BibleEditions.KONKANI_ID
}
