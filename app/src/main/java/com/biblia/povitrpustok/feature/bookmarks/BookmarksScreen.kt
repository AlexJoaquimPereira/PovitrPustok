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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReaderDestination

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
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
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
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.bookmarks.isEmpty() -> Text(
                    text = stringResource(R.string.bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(state.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            onClick = { onOpenBookmark(bookmark.toReaderDestination()) },
                            onDelete = { viewModel.removeBookmark(bookmark.id) }
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bookName = BibleCatalog.byId(bookmark.bookId)?.displayName ?: bookmark.bookId
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
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
