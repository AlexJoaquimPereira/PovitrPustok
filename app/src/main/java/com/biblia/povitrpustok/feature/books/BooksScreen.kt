package com.biblia.povitrpustok.feature.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Testament

/**
 * Books screen (implementation plan §7, §10, §20; corrective phase
 * Issues 1 & 2; F-03).
 *
 * Ordering/grouping come from the explicit canonical 73-book order; the
 * displayed name is the book's own database title (loaded once and cached
 * via [BooksViewModel]). No database is opened per recomposition and no
 * second book list is maintained.
 *
 * The catalog is always renderable, so this screen never shows a
 * full-screen spinner: title loading is a thin linear indicator above the
 * list, and a failed scan degrades to an inline banner with Retry while the
 * catalog names stay visible underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onBookSelected: (bookId: String) -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: BooksViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.books_title)) },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag("books.loading")
                )
            }
            if (state.titlesFailed) {
                TitlesUnavailableBanner(onRetry = viewModel::retry)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                BooksGrouping.groupByTestament(BibleCatalog.books).forEach { (testament, books) ->
                    item(key = "section-${testament.name}") {
                        TestamentHeader(testament)
                    }
                    items(books, key = { it.id }) { book ->
                        BookListItem(
                            localName = BookDisplayName.capitalizeDisplayName(
                                state.displayNames[book.id] ?: book.displayName
                            ),
                            englishName = book.englishName,
                            onClick = { onBookSelected(book.id) },
                            modifier = Modifier.testTag("books.row.${book.id}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline, non-blocking title-load failure notice (F-03). Offers Retry and
 * never replaces the book list, which keeps showing catalog names.
 */
@Composable
private fun TitlesUnavailableBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.books_titles_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("books.retry")
            ) {
                Text(text = stringResource(R.string.books_retry))
            }
        }
    }
}

@Composable
private fun TestamentHeader(testament: Testament, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            if (testament == Testament.OLD_TESTAMENT) R.string.old_testament else R.string.new_testament
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

/**
 * One book row (corrective phase Issues 4 & 11): the primary visible name
 * is the database-sourced local-language title (Issue 1), Capitalize-Cased
 * for display (Issue 3), with the canonical English name as a secondary,
 * right-aligned parenthetical (Issues 4/11). Long names ellipsize instead
 * of overlapping; navigation identity is unchanged.
 */
@Composable
fun BookListItem(
    localName: String,
    englishName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "($englishName)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
