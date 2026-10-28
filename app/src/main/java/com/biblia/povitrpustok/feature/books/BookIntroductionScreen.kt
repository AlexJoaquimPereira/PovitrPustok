package com.biblia.povitrpustok.feature.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlDocumentCache
import com.biblia.povitrpustok.core.html.BibleHtmlRenderer
import com.biblia.povitrpustok.core.html.BibleHtmlText
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.ui.components.EmptyState
import com.biblia.povitrpustok.ui.components.ErrorState
import com.biblia.povitrpustok.ui.components.LoadingState
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth

/**
 * Book-introduction screen (implementation plan §11, §12; Day 17 restyle
 * F-21).
 *
 * Renders each IntroductionBlock in source order through the existing
 * BibleHtmlParser/BibleHtmlText pipeline. Multiple blocks are kept
 * separate; a book without an introduction shows an explicit empty state.
 * The Reader itself is not modified for introductions.
 *
 * The restyle gives the screen its missing context and hierarchy:
 *  - the bar names the book ("Utpoti · Introduction"), so the reader always
 *    knows which introduction they are in;
 *  - the measure is capped at [ReadingColumnMaxWidth] with 20 dp gutters, so
 *    long introductions keep a comfortable line length in landscape;
 *  - `title` blocks render as serif headings (centred); a title block that
 *    merely repeats the book's own name is dropped because the bar already
 *    says it;
 *  - the end of the introduction offers one quiet full-width way forward,
 *    "Read the chapters", wired by the caller through [onOpenChapters].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookIntroductionScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit = {},
    viewModel: BookIntroductionViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The bar may only name *this* book: while a new load is in flight the
    // ViewModel can still hold the previous book's resolved name.
    val bookDisplayName = state.bookDisplayName.takeIf { state.bookId == bookId }
    val barTitle = bookDisplayName
        ?.let { stringResource(R.string.book_introduction_with_name, it) }
        ?: stringResource(R.string.book_introduction)

    // A title block that only repeats the book name adds nothing under a bar
    // that already names the book; real headings survive.
    val visibleBlocks = remember(state.blocks, bookDisplayName) {
        val withoutDuplicateTitle = state.blocks.filterNot { block ->
            block.type == TITLE_BLOCK_TYPE && block.repeatsName(bookDisplayName)
        }
        // Two books ship only their own title block; dropping it would leave
        // a page with nothing but the chapters link, so keep it as a heading.
        withoutDuplicateTitle.ifEmpty { state.blocks }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = barTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                // F-17: hairline separation between the bar and the content.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // F-24: the ViewModel carries a reason, not English.
            val error = state.error
            when {
                state.isLoading -> LoadingState(testTag = "introduction.loading")
                error != null -> ErrorState(
                    message = stringResource(
                        when (error) {
                            IntroductionError.BOOK_NOT_FOUND -> R.string.introduction_error_book_missing
                            IntroductionError.UNKNOWN -> R.string.introduction_error_load
                        }
                    ),
                    onRetry = viewModel::retry,
                    testTag = "introduction.error"
                )
                state.blocks.isEmpty() -> EmptyState(
                    title = stringResource(R.string.no_introduction),
                    testTag = "introduction.empty"
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("introduction.list"),
                    // Same measure convention as the Reader: 20 dp page
                    // gutters, then a 620 dp cap on the text column.
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(visibleBlocks, key = { it.id }) { block ->
                        IntroductionBlockView(
                            block = block,
                            modifier = Modifier.readingColumn()
                        )
                    }
                    item(key = "introduction.chapters") {
                        ReadChaptersLink(
                            onClick = onOpenChapters,
                            modifier = Modifier.readingColumn()
                        )
                    }
                }
            }
        }
    }
}

/** Corpus type of the block that carries the book's own title. */
private const val TITLE_BLOCK_TYPE = "title"

/** The reading column: capped and centred; page gutters live in the list. */
private fun Modifier.readingColumn(): Modifier =
    widthIn(max = ReadingColumnMaxWidth).fillMaxWidth()

/**
 * True when a title block's text is just the book's own name. Case and
 * whitespace are ignored; the rendered text (not the raw HTML) is compared,
 * so markup around the name does not defeat the match.
 */
private fun IntroductionBlock.repeatsName(bookDisplayName: String?): Boolean {
    if (bookDisplayName.isNullOrBlank()) return false
    val blockText = BibleHtmlRenderer.render(BibleHtmlDocumentCache.shared.get(html)).text
    return blockText.normalizedForComparison() == bookDisplayName.normalizedForComparison()
}

private val WHITESPACE_RUN = Regex("\\s+")

private fun String.normalizedForComparison(): String =
    trim().replace(WHITESPACE_RUN, " ").lowercase()

@Composable
private fun IntroductionBlockView(block: IntroductionBlock, modifier: Modifier = Modifier) {
    val document: BibleHtmlDocument = remember(block.html) {
        BibleHtmlDocumentCache.shared.get(block.html)
    }
    val isHeading = block.type == TITLE_BLOCK_TYPE
    BibleHtmlText(
        document = document,
        modifier = if (isHeading) modifier.semantics { heading() } else modifier,
        style = if (isHeading) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.bodyLarge
        },
        textAlign = if (isHeading) TextAlign.Center else null
    )
}

/**
 * End-of-introduction action (F-21): one quiet, full-width way forward to the
 * chapters. Hidden by the caller when there is nothing to introduce.
 */
@Composable
private fun ReadChaptersLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("introduction.chapters")
        ) {
            Text(
                text = stringResource(R.string.book_introduction_read_chapters),
                style = MaterialTheme.typography.labelLarge
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(18.dp)
            )
        }
    }
}
