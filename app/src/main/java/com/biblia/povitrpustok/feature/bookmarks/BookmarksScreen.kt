package com.biblia.povitrpustok.feature.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.ui.components.BookName
import com.biblia.povitrpustok.ui.components.BookNameLayout
import com.biblia.povitrpustok.ui.components.EmptyState
import com.biblia.povitrpustok.ui.components.LoadingState
import kotlinx.coroutines.launch

/**
 * Bookmarks screen (Phase 9, plan §9–§11).
 *
 * Lists saved bookmarks (Book from the static catalog, Chapter:Verse from
 * stored display values). Tapping a bookmark navigates to the existing
 * Reader targeting mechanism via [ReaderDestination]; deletion flows
 * through observable repository state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenBookmark: (ReaderDestination) -> Unit,
    onBack: () -> Unit,
    onOpenBooks: () -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.bookmarks_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                // F-17: hairline separation between the bar and the list.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> LoadingState(testTag = "bookmarks.loading")

                state.bookmarks.isEmpty() -> EmptyState(
                    title = stringResource(R.string.bookmarks_empty),
                    body = stringResource(R.string.bookmarks_empty_body),
                    actionLabel = stringResource(R.string.bookmarks_browse_books),
                    onAction = onOpenBooks,
                    testTag = "bookmarks.empty"
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(state.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            localName = state.bookNames[bookmark.bookId]
                                ?: BibleCatalog.byId(bookmark.bookId)?.displayName
                                ?: bookmark.bookId,
                            englishName = BibleCatalog.byId(bookmark.bookId)?.englishName,
                            onClick = { onOpenBookmark(bookmark.toReaderDestination()) },
                            onDelete = {
                                viewModel.removeBookmark(bookmark.id)
                                // F-09: deletion is reversible from the
                                // stored fields; no schema change needed.
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.bookmarks_removed),
                                        actionLabel = context.getString(R.string.reader_bookmark_undo)
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreBookmark(bookmark)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    localName: String,
    englishName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BookName(
                localName = localName,
                englishName = englishName,
                layout = BookNameLayout.INLINE,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (bookmark.verseStart == bookmark.verseEnd) {
                    stringResource(R.string.bookmark_reference, bookmark.chapterNumber, bookmark.verseStart)
                } else {
                    stringResource(R.string.bookmark_reference_range, bookmark.chapterNumber, bookmark.verseStart, bookmark.verseEnd)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.bookmarks_remove)
            )
        }
    }
}
