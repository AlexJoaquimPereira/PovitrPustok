package com.biblia.povitrpustok.feature.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.BookDisplayName
import com.biblia.povitrpustok.domain.model.Testament
import com.biblia.povitrpustok.ui.components.englishBookNameVisible
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle
import kotlinx.coroutines.launch

/**
 * Books screen (implementation plan §7, §10, §20; corrective phase
 * Issues 1 & 2; F-03; Day 17 scanning aids F-20/F-31).
 *
 * Ordering/grouping come from the explicit canonical 73-book order; the
 * displayed name is the book's own database title (loaded once and cached
 * via [BooksViewModel]). No database is opened per recomposition and no
 * second book list is maintained.
 *
 * Scanning aids (F-20/F-31):
 *  - testament headers are `stickyHeader`s styled as quiet editorial
 *    overlines, so the section stays named while its rows scroll;
 *  - a compact Old/New Testament segmented control jumps to a section;
 *  - each row is a two-line bilingual index entry (Konkani name above the
 *    English name), which survives large font scales better than the old
 *    right-aligned parenthetical;
 *  - the book of the stored reading position carries a quiet accent rule and
 *    a "Last read" label.
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

    // One grouping feeds both the list and the jump control, so the indices
    // cannot drift from what is rendered. Canonical order and `book.id` keys
    // are untouched.
    val sections = remember { BooksGrouping.groupByTestament(BibleCatalog.books) }
    val sectionStarts = remember(sections) { firstRowIndexes(sections) }
    val oldTestamentStart = sectionStarts[Testament.OLD_TESTAMENT] ?: 0
    val newTestamentStart = sectionStarts[Testament.NEW_TESTAMENT]

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The selected segment follows the scroll position. The New Testament
    // header item itself already means "in the New Testament", hence -1.
    val inNewTestament by remember(listState, newTestamentStart) {
        derivedStateOf {
            val start = newTestamentStart ?: Int.MAX_VALUE
            listState.firstVisibleItemIndex >= start - 1
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
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
                // F-17: hairline separation between the bar and the list.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TestamentJump(
                inNewTestament = inNewTestament,
                onJumpToOldTestament = {
                    scope.launch { listState.scrollToItem(oldTestamentStart) }
                },
                onJumpToNewTestament = {
                    newTestamentStart?.let { start ->
                        scope.launch { listState.scrollToItem(start) }
                    }
                }
            )
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag("books.loading")
                )
            }
            if (state.titlesFailed) {
                TitlesUnavailableBanner(onRetry = viewModel::retry)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                sections.forEach { (testament, books) ->
                    stickyHeader(key = "section-${testament.name}") {
                        TestamentHeader(testament)
                    }
                    itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                        BookListItem(
                            localName = BookDisplayName.capitalizeDisplayName(
                                state.displayNames[book.id] ?: book.displayName
                            ),
                            englishName = book.englishName,
                            onClick = { onBookSelected(book.id) },
                            modifier = Modifier.testTag("books.row.${book.id}"),
                            isLastRead = book.id == state.lastReadBookId,
                            showDivider = index < books.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * LazyColumn index of the first book row of each testament (F-31).
 *
 * Walks the exact grouping that renders the list: every section contributes
 * one header item plus one item per book, so the running offset stays in
 * step with the LazyColumn DSL. A section that is absent from the catalog is
 * simply missing from the map and its segment does nothing.
 */
internal fun firstRowIndexes(
    sections: List<Pair<Testament, List<BibleBook>>>
): Map<Testament, Int> {
    var nextIndex = 0
    val starts = LinkedHashMap<Testament, Int>()
    sections.forEach { (testament, books) ->
        starts[testament] = nextIndex + 1
        nextIndex += 1 + books.size
    }
    return starts
}

/**
 * Compact Old/New Testament jump control (F-31) directly under the app bar.
 *
 * It is a selection, not a second list: the segment reflects the section
 * currently at the top of the list, and tapping one jumps the *same*
 * canonical list to that section's first row. The jump is instant rather than
 * animated — flicking through ~40 rows would be slower and more disorienting
 * than arriving. 48 dp minimum targets keep it usable at 2x font scale; the
 * labels wrap to two lines instead of clipping.
 */
@Composable
private fun TestamentJump(
    inNewTestament: Boolean,
    onJumpToOldTestament: () -> Unit,
    onJumpToNewTestament: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SingleChoiceSegmentedButtonRow(
            // Compact: full width on a phone, a toolbar-sized control on a
            // tablet or in landscape.
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
        ) {
            SegmentedButton(
                selected = !inNewTestament,
                onClick = onJumpToOldTestament,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("books.jump.old"),
                icon = {},
                label = { JumpLabel(stringResource(R.string.old_testament)) }
            )
            SegmentedButton(
                selected = inNewTestament,
                onClick = onJumpToNewTestament,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("books.jump.new"),
                icon = {},
                label = { JumpLabel(stringResource(R.string.new_testament)) }
            )
        }
    }
}

@Composable
private fun JumpLabel(text: String) {
    Text(
        text = text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
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
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("books.retry")
            ) {
                Text(text = stringResource(R.string.books_retry))
            }
        }
    }
}

/**
 * Sticky testament header (F-20): an uppercase overline and a hairline rule,
 * not a link-coloured title.
 *
 * The fill is opaque `background`, so rows cannot show through the header
 * while it is stuck to the top of the list. The displayed text is uppercased
 * here, while the string resources keep their canonical form for tests and
 * for the jump control.
 */
@Composable
private fun TestamentHeader(testament: Testament, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
    ) {
        Text(
            text = stringResource(
                if (testament == Testament.OLD_TESTAMENT) R.string.old_testament
                else R.string.new_testament
            ).uppercase(),
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * One book row (corrective phase Issues 4 & 11; F-20).
 *
 * The primary visible name is the database-sourced local-language title
 * (Issue 1), Capitalize-Cased for display (Issue 3). The canonical English
 * name is a sans subtitle underneath — no parentheses, no right-aligned
 * parenthetical — so both names keep room and the row reads like a bilingual
 * index. [englishBookNameVisible] hides the subtitle at 2x font scale rather
 * than truncating the primary name.
 *
 * The last-read book (F-20) gets a 2 dp accent rule drawn behind the row plus
 * a small "Last read" label. Both are absent for every other row, and the
 * rule is drawn, not laid out, so nothing shifts when the marker disappears.
 * The whole row is one merged clickable node; navigation identity is
 * unchanged.
 */
@Composable
fun BookListItem(
    localName: String,
    englishName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLastRead: Boolean = false,
    showDivider: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val lastReadLabel = stringResource(R.string.books_last_read)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .drawBehind {
                    if (isLastRead) {
                        drawRect(
                            color = colorScheme.primary,
                            topLeft = Offset.Zero,
                            size = Size(2.dp.toPx(), size.height)
                        )
                    }
                }
                .semantics(mergeDescendants = true) { role = Role.Button }
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (englishBookNameVisible()) {
                    Text(
                        text = englishName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isLastRead) {
                Text(
                    text = lastReadLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = colorScheme.outlineVariant
            )
        }
    }
}
