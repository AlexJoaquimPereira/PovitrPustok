package com.biblia.povitrpustok.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.ReadingState

/**
 * Home launcher (implementation plan §13, §21): Books, Search, Bookmarks,
 * Continue reading (when a reading-state record exists), and Go to
 * reference. Minimalist — no Bible content is loaded here.
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onOpenBooks, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_bible))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_search))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenBookmarks, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_bookmarks))
            }
            val current = readingState
            if (current != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onContinueReading(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.home_continue_reading))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenReference, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_reference))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenDailyReading, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_daily_reading))
            }
        }
    }
}
