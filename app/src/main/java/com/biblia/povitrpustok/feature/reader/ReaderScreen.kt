package com.biblia.povitrpustok.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R

/**
 * Reader screen (implementation plan §17, §42).
 *
 * Loads only the requested chapter via [ReaderViewModel] and renders it
 * through the reader components. It never accesses SQLite, parses HTML,
 * manipulates raw HTML, performs networking, or contains repository
 * logic.
 */
@Composable
fun ReaderScreen(
    bookId: String,
    chapterPosition: Int,
    targetVerseId: Long? = null,
    passageStart: Int? = null,
    passageEnd: Int? = null,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId, chapterPosition, targetVerseId, passageStart, passageEnd) {
        viewModel.load(bookId, chapterPosition, targetVerseId, passageStart, passageEnd)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ReaderTopBar(
                state = state,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onSelectChapter = viewModel::selectChapter,
                onToggleBookmark = viewModel::toggleBookmark
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val errorMessage = state.errorMessage
            when {
                state.isLoading -> ReaderLoadingState()
                errorMessage != null ->
                    ReaderErrorState(
                        message = errorMessage,
                        onRetry = { viewModel.load(bookId, chapterPosition) }
                    )
                state.chapter != null -> ReaderContent(
                    state = state,
                    onTargetApplied = viewModel::onTargetApplied,
                    onHighlightCleared = viewModel::clearHighlight,
                    onVerseSelected = viewModel::onVerseSelected,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous
                )
                else -> ReaderLoadingState()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleBookmark: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = state.book?.displayName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium
                )
                val selectedVerse = state.selectedVerseId?.let { id ->
                    state.verses.firstOrNull { it.id == id }
                }
                Text(
                    text = if (selectedVerse != null) {
                        // The top-bar bookmark applies to this verse (Issue 6).
                        stringResource(
                            R.string.reader_selected_verse,
                            state.chapter?.number ?: 0,
                            selectedVerse.verseStart
                        )
                    } else {
                        stringResource(
                            R.string.reader_chapter_number,
                            state.chapter?.number ?: 0
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onPrevious,
                enabled = state.canGoPrevious
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.reader_previous_chapter)
                )
            }
        },
        actions = {
            // Bookmark toggle for the currently selected (targeted) verse.
            if (state.selectedVerseId != null) {
                val isBookmarked = state.bookmarkedVerseIds.contains(state.selectedVerseId)
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(
                            if (isBookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark
                        ),
                        tint = if (isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (state.chapters.isNotEmpty() && state.chapter != null) {
                ChapterSelector(
                    chapters = state.chapters,
                    currentPosition = state.chapter!!.position,
                    onSelect = onSelectChapter
                )
            }
            IconButton(
                onClick = onNext,
                enabled = state.canGoNext
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.reader_next_chapter)
                )
            }
        }
    )
}
