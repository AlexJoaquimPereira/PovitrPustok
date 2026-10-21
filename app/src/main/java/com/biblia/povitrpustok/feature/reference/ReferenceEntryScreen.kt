package com.biblia.povitrpustok.feature.reference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.model.toReaderDestination
import com.biblia.povitrpustok.feature.common.chapterDisplayLabel

/**
 * Scripture-reference entry screen (Phase 8, plan §9–§11, §16, §17).
 *
 * Input -> ScriptureReferenceResolver -> resolution -> navigate/message.
 * The four resolution states are presented distinctly; Ambiguous shows
 * every candidate chapter (source-order labels) and never picks silently;
 * selecting a candidate navigates by bookId + chapterPosition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceEntryScreen(
    onOpenResolved: (ReaderDestination) -> Unit,
    onBack: () -> Unit,
    viewModel: ReferenceEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Navigate exactly once for a successful resolution, then consume the
    // event so returning to this screen never re-navigates.
    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { destination ->
            onOpenResolved(destination)
            viewModel.consumeNavigation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reference_entry_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reference_entry_label)) },
                placeholder = { Text(stringResource(R.string.reference_entry_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (state.query.isNotBlank() && !state.isResolving) {
                            focusManager.clearFocus()
                            viewModel.resolve()
                        }
                    }
                ),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = viewModel::resolve,
                enabled = state.query.isNotBlank() && !state.isResolving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reference_entry_go))
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (val resolution = state.resolution) {
                null -> if (state.isResolving) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ScriptureResolution.Resolved -> Unit // navigation handled by LaunchedEffect

                is ScriptureResolution.Ambiguous -> AmbiguousMessage(
                    resolution = resolution,
                    onCandidateSelected = { chapter ->
                        onOpenResolved(resolution.toReaderDestination(chapter))
                    }
                )

                is ScriptureResolution.AmbiguousVerse -> AmbiguousVerseMessage(
                    resolution = resolution,
                    onCandidateSelected = { verse ->
                        onOpenResolved(
                            ReaderDestination(
                                bookId = resolution.bookId,
                                chapterPosition = resolution.chapterPosition,
                                verseId = verse.id,
                                verseStart = verse.verseStart,
                                verseEnd = verse.verseEnd
                            )
                        )
                    }
                )

                is ScriptureResolution.NotFound -> Message(
                    text = stringResource(R.string.reference_not_found, resolution.reason)
                )

                is ScriptureResolution.Invalid -> Message(
                    text = stringResource(R.string.reference_invalid, resolution.reason)
                )
            }
        }
    }
}

@Composable
private fun AmbiguousMessage(
    resolution: ScriptureResolution.Ambiguous,
    onCandidateSelected: (com.biblia.povitrpustok.domain.model.Chapter) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reference_ambiguous),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(resolution.candidates, key = { it.id }) { chapter ->
                Text(
                    text = chapterDisplayLabel(chapter, resolution.candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCandidateSelected(chapter) }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun AmbiguousVerseMessage(
    resolution: ScriptureResolution.AmbiguousVerse,
    onCandidateSelected: (com.biblia.povitrpustok.domain.model.Verse) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reference_ambiguous_verse),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(resolution.candidates, key = { it.id }) { verse ->
                Text(
                    text = verseLabel(verse, resolution.candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCandidateSelected(verse) }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 14.dp)
                )
            }
        }
    }
}

/**
 * Presentation-only label for ambiguous verse candidates: the source range
 * plus a source-order occurrence suffix when several records share it.
 */
private fun verseLabel(
    verse: com.biblia.povitrpustok.domain.model.Verse,
    candidates: List<com.biblia.povitrpustok.domain.model.Verse>
): String {
    val base = if (verse.verseStart == verse.verseEnd) {
        verse.verseStart.toString()
    } else {
        "${verse.verseStart}–${verse.verseEnd}"
    }
    if (candidates.size <= 1) return base
    val occurrence = candidates.indexOfFirst { it.id == verse.id } + 1
    return "$base ($occurrence)"
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth()
    )
}


