package com.biblia.povitrpustok.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R

/**
 * Shared empty / error / loading surfaces (F-16).
 *
 * Every screen uses the same composition, typography, and recovery language:
 *  - [LoadingState]   — centred progress indicator;
 *  - [EmptyState]     — title, optional body, optional action;
 *  - [ErrorState]     — message plus a Retry action.
 *
 * State classes and navigation contracts are unchanged; this is presentation
 * composition only.
 */

private fun Modifier.optionalTestTag(tag: String?): Modifier =
    if (tag == null) this else testTag(tag)

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .optionalTestTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Empty state: never a dead end when an action is supplied.
 *
 * [title] is the existing test-visible string where one exists (e.g.
 * "No bookmarks yet.") so current tests keep passing.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    testTag: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .optionalTestTag(testTag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .optionalTestTag(testTag?.let { "$it.action" })
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * Error state with a mandatory Retry action: no failure is a dead end.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = stringResource(R.string.reader_retry),
    testTag: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .optionalTestTag(testTag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 12.dp)
                .optionalTestTag(testTag?.let { "$it.retry" })
        ) {
            Text(text = retryLabel)
        }
    }
}
