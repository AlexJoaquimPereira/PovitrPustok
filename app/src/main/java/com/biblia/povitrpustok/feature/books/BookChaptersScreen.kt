package com.biblia.povitrpustok.feature.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.feature.common.chapterDisplayLabel
import com.biblia.povitrpustok.ui.components.BookName
import com.biblia.povitrpustok.ui.components.EmptyState
import com.biblia.povitrpustok.ui.components.ErrorState
import com.biblia.povitrpustok.ui.components.LoadingState

/**
 * Chapter-selection screen for one book (implementation plan §8, §9, §10).
 *
 * The grid is keyed by the stable `chapter.id` and each cell selects by
 * `chapter.position` for the existing Reader route. Duplicate source
 * chapter numbers (Job) remain separate cells with a presentation-only
 * disambiguator derived from source order — the underlying Chapter model
 * and the database are never altered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChaptersScreen(
    bookId: String,
    onChapterSelected: (chapterPosition: Int) -> Unit,
    onIntroductionSelected: () -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: BookChaptersViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BookName(
                        localName = state.displayName.ifBlank {
                            state.book?.displayName.orEmpty()
                        },
                        englishName = state.book?.englishName
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.home_search)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> LoadingState(testTag = "chapters.loading")
                state.errorMessage != null -> ErrorState(
                    message = state.errorMessage.orEmpty(),
                    onRetry = { viewModel.load(bookId) },
                    testTag = "chapters.error"
                )
                state.chapters.isEmpty() -> EmptyState(
                    title = stringResource(R.string.chapters_empty),
                    testTag = "chapters.empty"
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(
                        onClick = onIntroductionSelected,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(text = stringResource(R.string.book_introduction))
                    }
                    val columns = chapterGridColumns(LocalDensity.current.fontScale)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.chapters, key = { it.id }) { chapter ->
                            ChapterCell(
                                label = chapterDisplayLabel(chapter, state.chapters),
                                onClick = { onChapterSelected(chapter.position) },
                                modifier = Modifier.testTag("chapters.cell.${chapter.position}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterCell(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = ChapterCellMinHeight),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Presentation-only chapter label (moved to `feature.common` so the Reader
 * selector can share it — READER-02).
 */
