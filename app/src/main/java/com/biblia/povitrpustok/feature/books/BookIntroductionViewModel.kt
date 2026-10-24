package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleDataException
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
 * Loads a book's introduction blocks (implementation plan §11, §12).
 * Books differ in introduction structure; every IntroductionBlock is
 * preserved independently in source order.
 */
@HiltViewModel
class BookIntroductionViewModel @Inject constructor(
    private val repository: BibleRepository,
    private val bookNameRepository: BookNameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookIntroductionUiState())
    val uiState: StateFlow<BookIntroductionUiState> = _uiState.asStateFlow()

    private var lastBookId: String? = null

    fun load(bookId: String) {
        lastBookId = bookId
        viewModelScope.launch {
            _uiState.update {
                // A new book must not inherit the previous book's name or
                // blocks: a failed load would otherwise title B's error with
                // A's name (F-21).
                it.copy(
                    bookId = bookId,
                    bookDisplayName = null,
                    blocks = emptyList(),
                    isLoading = true,
                    error = null
                )
            }
            try {
                // F-21: the title must identify the book. The name source
                // falls back to the catalog, so this never blocks the load.
                val displayName = bookNameRepository.displayName(bookId)
                val blocks = repository.getIntroduction(bookId)
                _uiState.value = BookIntroductionUiState(
                    bookId = bookId,
                    bookDisplayName = displayName,
                    blocks = blocks,
                    isLoading = false
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BibleBookNotFoundException) {
                setError(IntroductionError.BOOK_NOT_FOUND)
            } catch (e: BibleDataException) {
                setError(IntroductionError.UNKNOWN)
            } catch (e: Exception) {
                setError(IntroductionError.UNKNOWN)
            }
        }
    }

    /** F-24: the state carries a reason; the screen owns the localized copy. */
    private fun setError(error: IntroductionError) {
        _uiState.update { it.copy(isLoading = false, error = error) }
    }

    /** Repeats the last load after a failure (F-16: no dead ends). */
    fun retry() {
        lastBookId?.let(::load)
    }
}
