package com.biblia.povitrpustok.feature.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Issues 1 & 2).
 *
 * Ordering/grouping come from the explicit canonical 73-book order; the
 * displayed name is the book's own database title (loaded once and cached
 * via [BooksViewModel]). No database is opened per recomposition and no
 * second book list is maintained.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onBookSelected: (bookId: String) -> Unit,
    viewModel: BooksViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.books_title)) }) }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                            onClick = { onBookSelected(book.id) }
                        )
                    }
                }
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
