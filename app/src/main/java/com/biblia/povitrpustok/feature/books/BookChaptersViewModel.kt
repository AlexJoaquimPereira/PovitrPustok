package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads the lightweight chapter list for one book
 * (implementation plan §8). Only `getChapters(bookId)` is queried — never
 * verses, introductions, or other books.
 *
 * Edition seam: chapters and the display name come from the selected
 * reading edition. A change of edition (Settings, then Back) reloads the
 * open book instead of leaving the previous edition's chapter list behind.
 */
@HiltViewModel
class BookChaptersViewModel @Inject constructor(
    private val repository: BibleRepository,
    private val bookNames: BookNameRepository,
    private val readingPreferences: ReadingPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookChaptersUiState())
    val uiState: StateFlow<BookChaptersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            readingPreferences.preferences
                .map { editionOf(it.readingEditionId) }
                .distinctUntilChanged()
                .collect { editionId ->
                    val state = _uiState.value
                    val bookId = state.bookId
                    if (bookId != null && state.editionId != editionId) {
                        load(bookId)
                    }
                }
        }
    }

    fun load(bookId: String) {
        val editionId = editionOf(readingPreferences.preferences.value.readingEditionId)
        viewModelScope.launch {
            _uiState.update { state ->
                // A load for another book must not keep the previous book's
                // chapters or English name behind the new title (F-12).
                val sameBook = state.bookId == bookId
                state.copy(
                    book = if (sameBook) state.book else null,
                    chapters = if (sameBook) state.chapters else emptyList(),
                    bookId = bookId,
                    // F-12: never blank while the corpus title is loading.
                    displayName = BibleCatalog.displayNameFor(bookId, editionId),
                    editionId = editionId,
                    isLoading = true,
                    error = null
                )
            }
            try {
                val book = repository.getBook(bookId, editionId)
                val chapters = repository.getChapters(bookId, editionId)
                _uiState.value = BookChaptersUiState(
                    book = book,
                    bookId = bookId,
                    displayName = bookNames.displayName(bookId, editionId),
                    chapters = chapters,
                    isLoading = false,
                    editionId = editionId
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BibleBookNotFoundException) {
                setError(ChaptersError.BOOK_NOT_FOUND)
            } catch (e: BibleDataException) {
                setError(ChaptersError.UNKNOWN)
            } catch (e: Exception) {
                setError(ChaptersError.UNKNOWN)
            }
        }
    }

    /** F-24: the state carries a reason; the screen owns the localized copy. */
    private fun setError(error: ChaptersError) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = error,
                book = null,
                chapters = emptyList()
            )
        }
    }

    private fun editionOf(editionId: String): String =
        BibleEditions.byId(editionId)?.id ?: BibleEditions.KONKANI_ID
}
