package com.biblia.povitrpustok.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    onBack: () -> Unit,
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
                onBack = onBack,
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
                        onRetry = viewModel::retry
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
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleBookmark: () -> Unit
) {
    TopAppBar(
        modifier = Modifier.testTag("reader.topbar"),
        title = {
            Column {
                Text(
                    text = state.book?.displayName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val chapter = state.chapter
                val selectedVerse = state.selectedVerseId?.let { id ->
                    state.verses.firstOrNull { it.id == id }
                }
                if (chapter != null) {
                    Text(
                        text = if (selectedVerse != null) {
                            // The top-bar bookmark applies to this verse (Issue 6).
                            stringResource(
                                R.string.reader_selected_verse,
                                chapter.number,
                                selectedVerse.verseStart
                            )
                        } else {
                            stringResource(
                                R.string.reader_chapter_number,
                                chapter.number
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("reader.back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
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
            // Day 1 interim: chapter stepping stays in the actions row until
            // it moves to the reader's own navigation.
            IconButton(
                onClick = onPrevious,
                enabled = state.canGoPrevious
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.reader_previous_chapter)
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
