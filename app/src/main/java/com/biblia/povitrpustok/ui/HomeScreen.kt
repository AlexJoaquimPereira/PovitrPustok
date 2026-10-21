package com.biblia.povitrpustok.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.ReadingState

/**
 * Home launcher (implementation plan §13, §21): Bible, Search, Bookmarks,
 * Go to reference, Daily Reading, and Continue reading (when a reading-state
 * record exists). Minimalist — no Bible content is loaded here.
 *
 * The screen is edge-to-edge safe: [WindowInsets.safeDrawing] keeps content
 * clear of the status/navigation bars (including 3-button navigation), and
 * the whole column scrolls so the corpus notice is visible on first launch
 * at 1x and reachable at larger font scales or in landscape.
 */
@Composable
fun HomeScreen(
    onOpenBooks: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenReference: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onContinueReading: (ReadingState) -> Unit = {},
    onOpenDailyReading: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val readingState by viewModel.readingState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onOpenBooks,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .testTag("home.bible")
            ) {
                Text(
                    text = stringResource(R.string.home_bible),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeTile(
                    label = stringResource(R.string.home_search),
                    onClick = onOpenSearch,
                    tag = "home.search",
                    modifier = Modifier.weight(1f)
                )
                HomeTile(
                    label = stringResource(R.string.home_bookmarks),
                    onClick = onOpenBookmarks,
                    tag = "home.bookmarks",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeTile(
                    label = stringResource(R.string.home_reference),
                    onClick = onOpenReference,
                    tag = "home.reference",
                    modifier = Modifier.weight(1f)
                )
                HomeTile(
                    label = stringResource(R.string.home_daily_reading),
                    onClick = onOpenDailyReading,
                    tag = "home.daily",
                    modifier = Modifier.weight(1f)
                )
            }

            val current = readingState
            if (current != null) {
                Spacer(modifier = Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = { onContinueReading(current) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("home.continue"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_continue_reading),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = stringResource(R.string.corpus_copyright_notice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
                    .testTag("home.notice")
            )
        }
    }
}

/** Compact secondary home action: one cell of the 2-column grid. */
@Composable
private fun HomeTile(
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}
