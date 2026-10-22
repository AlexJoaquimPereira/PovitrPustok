package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads the lightweight chapter list for one book
 * (implementation plan §8). Only `getChapters(bookId)` is queried — never
 * verses, introductions, or other books.
 */
@HiltViewModel
class BookChaptersViewModel @Inject constructor(
    private val repository: BibleRepository,
    private val bookNames: BookNameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookChaptersUiState())
    val uiState: StateFlow<BookChaptersUiState> = _uiState.asStateFlow()

    fun load(bookId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bookId = bookId,
                    // F-12: never blank while the corpus title is loading.
                    displayName = BibleCatalog.byId(bookId)?.displayName.orEmpty(),
                    isLoading = true,
                    errorMessage = null
                )
            }
            try {
                val book = repository.getBook(bookId)
                val chapters = repository.getChapters(bookId)
                _uiState.value = BookChaptersUiState(
                    book = book,
                    bookId = bookId,
                    displayName = bookNames.displayName(bookId),
                    chapters = chapters,
                    isLoading = false
                )
            } catch (e: BibleDataException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load chapters.")
                }
            }
        }
    }
}
