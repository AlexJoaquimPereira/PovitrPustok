package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.repository.BibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads a book's introduction blocks (implementation plan §11, §12).
 * Books differ in introduction structure; every IntroductionBlock is
 * preserved independently in source order.
 */
@HiltViewModel
class BookIntroductionViewModel @Inject constructor(
    private val repository: BibleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookIntroductionUiState())
    val uiState: StateFlow<BookIntroductionUiState> = _uiState.asStateFlow()

    private var lastBookId: String? = null

    fun load(bookId: String) {
        lastBookId = bookId
        viewModelScope.launch {
            _uiState.update {
                it.copy(bookId = bookId, isLoading = true, errorMessage = null)
            }
            try {
                val blocks = repository.getIntroduction(bookId)
                _uiState.value = BookIntroductionUiState(
                    bookId = bookId,
                    blocks = blocks,
                    isLoading = false
                )
            } catch (e: BibleDataException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load introduction.")
                }
            }
        }
    }

    /** Repeats the last load after a failure (F-16: no dead ends). */
    fun retry() {
        lastBookId?.let(::load)
    }
}
