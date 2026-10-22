package com.biblia.povitrpustok.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ReaderPassageCodec
import com.biblia.povitrpustok.feature.common.chapterDisplayLabel
import com.biblia.povitrpustok.ui.components.BookName
import com.biblia.povitrpustok.ui.components.BookNameLayout

/**
 * Reader screen (implementation plan §17, §42).
 *
 * Loads only the requested chapter via [ReaderViewModel] and renders it
 * through the reader components. It never accesses SQLite, parses HTML,
 * manipulates raw HTML, performs networking, or contains repository
 * logic.
 */
@Composable
fun ReaderScreen(
    bookId: String,
    chapterPosition: Int,
    onBack: () -> Unit,
    targetVerseId: Long? = null,
    passageStart: Int? = null,
    passageEnd: Int? = null,
    passagesSpec: String = "",
    targetVerseNumber: Int? = null,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val passages = remember(passagesSpec) { ReaderPassageCodec.decode(passagesSpec) }
    LaunchedEffect(
        bookId,
        chapterPosition,
        targetVerseId,
        targetVerseNumber,
        passageStart,
        passageEnd,
        passagesSpec
    ) {
        viewModel.load(
            bookId = bookId,
            chapterPosition = chapterPosition,
            targetVerseId = targetVerseId,
            passageStart = passageStart,
            passageEnd = passageEnd,
            passages = passages,
            targetVerseNumber = targetVerseNumber
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    // F-09: confirm every long-press toggle and offer an immediate Undo.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReaderEvent.BookmarkChanged -> {
                    val message = context.getString(
                        if (event.added) {
                            R.string.reader_bookmark_added
                        } else {
                            R.string.reader_bookmark_removed
                        },
                        event.bookName,
                        event.chapterNumber,
                        event.verseStart
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(R.string.reader_bookmark_undo)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoBookmarkChange(event)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ReaderTopBar(state = state, onBack = onBack) },
        bottomBar = {
            // F-04: every frequent action lives in the thumb zone.
            ReaderActionBar(
                state = state,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onToggleBookmark = viewModel::toggleBookmark,
                onOpenChapterPicker = { pickerOpen = true }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (shouldShowPassageBanner(state)) {
                PassageBanner(
                    state = state,
                    onShowFullChapter = viewModel::showFullChapter
                )
            }
            if (state.targetMissing) {
                TargetMissingNotice(onDismiss = viewModel::dismissTargetMissing)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val errorMessage = state.errorMessage
            when {
                state.isLoading -> ReaderLoadingState()
                errorMessage != null ->
                    ReaderErrorState(
                        message = errorMessage,
                        onRetry = viewModel::retry
                    )
                state.chapter != null -> ReaderContent(
                    state = state,
                    onTargetApplied = viewModel::onTargetApplied,
                    onHighlightCleared = viewModel::clearHighlight,
                    onVerseSelected = viewModel::onVerseSelected,
                    onVerseLongPress = { verseId ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleBookmarkFor(verseId)
                    },
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous
                )
                else -> ReaderLoadingState()
            }
            }
        }
    }

    if (pickerOpen) {
        ChapterPickerSheet(
            chapters = state.chapters,
            currentPosition = state.chapter?.position,
            onSelect = { position ->
                pickerOpen = false
                if (position != state.chapter?.position) {
                    viewModel.selectChapter(position)
                }
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(state: ReaderUiState, onBack: () -> Unit) {
    TopAppBar(
        modifier = Modifier.testTag("reader.topbar"),
        title = {
            // F-04: one line only; the chapter/verse context lives in the
            // scrolling content header. The English name stays secondary
            // (F-12) and yields space first.
            BookName(
                localName = state.bookDisplayName.ifBlank {
                    state.book?.displayName.orEmpty()
                },
                englishName = state.book?.englishName,
                layout = BookNameLayout.INLINE,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("reader.back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderActionBar(
    state: ReaderUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenChapterPicker: () -> Unit
) {
    BottomAppBar(modifier = Modifier.testTag("reader.actionbar")) {
        IconButton(
            onClick = onPrevious,
            enabled = state.canGoPrevious,
            modifier = Modifier.weight(1f).testTag("reader.previous")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.reader_previous_chapter)
            )
        }

        val currentLabel = state.chapter
            ?.let { chapterDisplayLabel(it, state.chapters) }
            .orEmpty()
        TextButton(
            onClick = onOpenChapterPicker,
            enabled = state.chapter != null && state.chapters.isNotEmpty(),
            modifier = Modifier
                .weight(1.4f)
                .heightIn(min = 48.dp)
                .testTag("reader.chapter_picker")
        ) {
            Text(
                text = stringResource(R.string.chapter_selector_label, currentLabel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onNext,
            enabled = state.canGoNext,
            modifier = Modifier.weight(1f).testTag("reader.next")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.reader_next_chapter)
            )
        }

        val selected = state.selectedVerseId
        val isBookmarked = selected != null && state.bookmarkedVerseIds.contains(selected)
        IconButton(
            onClick = onToggleBookmark,
            enabled = selected != null,
            modifier = Modifier.weight(1f).testTag("reader.bookmark")
        ) {
            Icon(
                painter = painterResource(
                    if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
                ),
                contentDescription = stringResource(
                    if (isBookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark
                ),
                tint = if (isBookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * F-04: the chapter picker is a grid sheet (five columns, three at 1.5x+)
 * with the current chapter marked, instead of a 150-item dropdown. Selecting
 * a chapter calls the unchanged [ReaderViewModel.selectChapter] path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterPickerSheet(
    chapters: List<Chapter>,
    currentPosition: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val columns = if (LocalDensity.current.fontScale >= 1.5f) 3 else 5
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("reader.picker")
    ) {
        Text(
            text = stringResource(R.string.chapter_picker_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chapters, key = { it.id }) { chapter ->
                val current = chapter.position == currentPosition
                val currentDescription = stringResource(R.string.chapter_picker_current)
                Surface(
                    onClick = { onSelect(chapter.position) },
                    color = if (current) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(
                            if (current) {
                                Modifier.semantics { stateDescription = currentDescription }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("reader.picker.cell.${chapter.position}")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chapterDisplayLabel(chapter, chapters),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (current) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * F-05: a banner is only meaningful when content is actually filtered. A
 * single whole-chapter part (chapter-only references use one) must not
 * claim a restriction that does not exist.
 */
private fun shouldShowPassageBanner(state: ReaderUiState): Boolean {
    val parts = state.passageChapters
    if (parts.isNotEmpty()) {
        if (parts.size > 1) return true
        val part = parts.first()
        return !coversWholeChapter(part.passage.ranges, part.verses)
    }
    val passage = state.passage ?: return false
    return !coversWholeChapter(passage.ranges, state.verses)
}

private fun coversWholeChapter(
    ranges: List<IntRange>,
    verses: List<com.biblia.povitrpustok.domain.model.Verse>
): Boolean {
    val first = verses.firstOrNull()?.verseStart ?: return false
    val last = verses.lastOrNull()?.let { it.verseEnd ?: it.verseStart } ?: return false
    return ranges.size == 1 && ranges.first().first <= first && ranges.first().last >= last
}

/**
 * F-05: passage mode is never contextless. The banner names the requested
 * verses (or the number of chapters) and offers a one-tap way back to the
 * whole chapter. Additive: filtering itself is unchanged.
 */
@Composable
private fun PassageBanner(
    state: ReaderUiState,
    onShowFullChapter: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("reader.passage_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = passageLabel(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onShowFullChapter,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("reader.show_full_chapter")
            ) {
                Text(text = stringResource(R.string.reader_show_full_chapter))
            }
        }
    }
}

@Composable
private fun passageLabel(state: ReaderUiState): String {
    val parts = state.passageChapters
    if (parts.size > 1) {
        return stringResource(R.string.reader_passage_chapters, parts.size)
    }
    val ranges = parts.firstOrNull()?.passage?.ranges
        ?: state.passage?.ranges
        ?: return stringResource(R.string.reader_passage_label)
    val labels = mutableListOf<String>()
    for (range in ranges) {
        labels += if (range.first == range.last) {
            stringResource(R.string.reader_verse_only, range.first)
        } else {
            stringResource(R.string.verse_range, range.first, range.last)
        }
    }
    return if (ranges.size == 1 && ranges.first().first == ranges.first().last) {
        labels.first()
    } else {
        stringResource(R.string.reader_passage_verses, labels.joinToString(", "))
    }
}

/** F-05: a stale target explains itself instead of silently showing the chapter. */
@Composable
private fun TargetMissingNotice(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().testTag("reader.target_missing")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.reader_target_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("reader.target_missing_dismiss")
            ) {
                Text(text = stringResource(R.string.reader_dismiss))
            }
        }
    }
}
