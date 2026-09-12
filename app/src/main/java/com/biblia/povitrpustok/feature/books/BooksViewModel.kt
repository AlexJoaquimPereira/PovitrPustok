package com.biblia.povitrpustok.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads database-sourced book display names once per process
 * (corrective phase Issue 1). The repository caches titles, so the Books
 * screen never reopens databases on recomposition.
 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val titleRepository: BookTitleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val displayNames = titleRepository.loadAllDisplayNames()
            _uiState.value = BooksUiState(displayNames = displayNames, isLoading = false)
        }
    }
}
