package com.biblia.povitrpustok.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.SearchResult
import com.biblia.povitrpustok.ui.components.EmptyState
import com.biblia.povitrpustok.ui.components.ErrorState

/**
 * Minimal search screen (implementation plan §25–§26).
 *
 * The UI never executes SQL, never touches SQLite, and never parses HTML:
 * it drives [SearchViewModel] and renders plain-text snippets. Result taps
 * navigate to the reader with stable identifiers (bookId + chapterPosition).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenResult: (SearchResult) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.search_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                // F-17: hairline separation between the bar and the search field.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // F2-07: the scaffold's bottom padding already contains the
                // navigation-bar inset, and the IME inset is measured from the
                // bottom of the screen (so it contains that same inset). Consume
                // what was just padded, otherwise the keyboard leaves an extra
                // nav-bar-high strip of dead space above it.
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search.field"),
                label = { Text(stringResource(R.string.search_field_label)) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    } else {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    }
                }
            )

            BookFilterRow(
                selectedBookId = state.selectedBookId,
                bookNames = state.bookNames,
                onSelect = viewModel::onBookFilterChange,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (state.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag("search.progress")
                )
            }
            (state.issue as? SearchIssue.Partial)?.let { partial ->
                Text(
                    text = pluralStringResource(
                        R.plurals.search_partial_results,
                        partial.failedBooks,
                        partial.failedBooks
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (!state.isSearching && state.issue !is SearchIssue.Failed &&
                state.query.isNotBlank() && state.results.isNotEmpty()
            ) {
                Text(
                    text = if (state.results.size >= SEARCH_LIMIT) {
                        stringResource(R.string.search_result_count_capped, state.results.size)
                    } else {
                        pluralStringResource(
                            R.plurals.search_result_count,
                            state.results.size,
                            state.results.size
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("search.count")
                )
            }
            // P2-4: a failed run keeps the last good rows (SearchViewModel);
            // surface the failure inline instead of replacing them.
            if (state.issue is SearchIssue.Failed && state.results.isNotEmpty()) {
                SearchErrorBanner(onRetry = viewModel::retry)
            }

            when {
                state.issue is SearchIssue.Failed && state.results.isEmpty() -> ErrorState(
                    message = stringResource(R.string.search_error_failed),
                    onRetry = viewModel::retry,
                    testTag = "search.error"
                )

                state.query.isBlank() -> EmptyState(
                    title = stringResource(R.string.search_empty),
                    modifier = Modifier.weight(1f),
                    testTag = "search.empty"
                )

                state.results.isEmpty() && !state.isSearching -> EmptyState(
                    title = stringResource(R.string.search_no_results),
                    modifier = Modifier.weight(1f),
                    testTag = "search.no_results"
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("search.results"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = state.results,
                        key = { _, result -> "${result.bookId}-${result.chapterId}-${result.verseId}" }
                    ) { index, result ->
                        // F-28: the hairline sits between rows only, outside the
                        // clickable row, so it is never part of the merged node.
                        Column {
                            SearchResultItem(
                                result = result,
                                query = state.query,
                                onClick = { onOpenResult(result) },
                                bookName = state.bookNames[result.bookId]
                            )
                            if (index < state.results.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SEARCH_LIMIT = 50

/**
 * P2-4: inline failure banner shown above the retained results of the last
 * successful run, so a failed refresh never hides readable rows.
 */
@Composable
private fun SearchErrorBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("search.error"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.search_error_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag("search.error.retry")
        ) {
            Text(stringResource(R.string.reader_retry))
        }
    }
}

/** Compact book filter: "All Bible" plus every catalog book (by id). */
@Composable
private fun BookFilterRow(
    selectedBookId: String?,
    bookNames: Map<String, String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.search_in_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                val bookName = selectedBookId?.let { id ->
                    bookNames[id] ?: BibleCatalog.byId(id)?.displayName
                }
                Text(
                    text = bookName ?: stringResource(R.string.search_all_bible),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_all_bible)) },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
                BibleCatalog.books.forEach { book ->
                    DropdownMenuItem(
                        text = { Text(bookNames[book.id] ?: book.displayName) },
                        onClick = {
                            expanded = false
                            onSelect(book.id)
                        }
                    )
                }
            }
        }
    }
}
