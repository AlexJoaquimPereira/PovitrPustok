package com.biblia.povitrpustok.feature.books

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.core.html.BibleHtmlText
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.ui.components.EmptyState
import com.biblia.povitrpustok.ui.components.ErrorState
import com.biblia.povitrpustok.ui.components.LoadingState

/**
 * Lightweight book-introduction screen (implementation plan §11, §12).
 *
 * Renders each IntroductionBlock in source order through the existing
 * BibleHtmlParser/BibleHtmlText pipeline. Multiple blocks are kept
 * separate; a book without an introduction shows an explicit empty state.
 * The Reader itself is not modified for introductions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookIntroductionScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: BookIntroductionViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_introduction)) },
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
                state.isLoading -> LoadingState(testTag = "introduction.loading")
                state.errorMessage != null -> ErrorState(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::retry,
                    testTag = "introduction.error"
                )
                state.blocks.isEmpty() -> EmptyState(
                    title = stringResource(R.string.no_introduction),
                    testTag = "introduction.empty"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    items(state.blocks, key = { it.id }) { block ->
                        IntroductionBlockView(block)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroductionBlockView(block: IntroductionBlock, modifier: Modifier = Modifier) {
    val document: BibleHtmlDocument = remember(block.html) { BibleHtmlParser().parse(block.html) }
    BibleHtmlText(
        document = document,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
}
