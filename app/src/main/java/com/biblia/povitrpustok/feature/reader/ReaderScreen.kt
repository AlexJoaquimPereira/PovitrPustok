package com.biblia.povitrpustok.feature.reader

import android.content.ClipData
import android.content.Intent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ReaderPassageCodec
import com.biblia.povitrpustok.feature.common.chapterDisplayLabel
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints
import com.biblia.povitrpustok.ui.components.BookName
import com.biblia.povitrpustok.ui.components.BookNameLayout

/** F-19: chapter changes slide 160 ms; the target scroll itself stays instant. */
private const val READER_SLIDE_MS = 160
private const val READER_FADE_MS = 120
private const val READER_SLIDE_DIVISOR = 8
private const val BOOKMARK_SCALE_MS = 120

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
    editionId: String = BibleEditions.KONKANI_ID,
    onOpenAbout: () -> Unit = {},
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
        passagesSpec,
        editionId
    ) {
        viewModel.load(
            bookId = bookId,
            chapterPosition = chapterPosition,
            targetVerseId = targetVerseId,
            passageStart = passageStart,
            passageEnd = passageEnd,
            passages = passages,
            targetVerseNumber = targetVerseNumber,
            editionId = editionId
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    // F-23: long-press opens the verse actions instead of bookmarking blindly.
    var actionVerseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
                        event.anchor.verseStart
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
        topBar = {
            Column {
                ReaderTopBar(
                    state = state,
                    onBack = onBack,
                    onOpenAbout = onOpenAbout
                )
                // F-17: a hairline keeps the bar from blending into the page.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
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
            // F-24: the ViewModel carries a reason, not English.
            val errorMessage = state.loadError?.let { error ->
                stringResource(
                    when (error) {
                        ReaderLoadError.BOOK_NOT_FOUND -> R.string.reader_error_book_missing
                        ReaderLoadError.CHAPTER_NOT_FOUND -> R.string.reader_error_chapter_missing
                        ReaderLoadError.UNKNOWN -> R.string.reader_error_load_failed
                    }
                )
            }
            when {
                state.isLoading -> ReaderLoadingState()
                errorMessage != null ->
                    ReaderErrorState(
                        message = errorMessage,
                        onRetry = viewModel::retry
                    )
                state.chapter != null -> AnimatedContent(
                    targetState = state,
                    // Only chapter changes animate; selection/highlight state
                    // updates recompose in place.
                    contentKey = { it.book?.id to it.chapter?.id },
                    transitionSpec = {
                        val forward = (targetState.chapter?.position ?: 0) >=
                            (initialState.chapter?.position ?: 0)
                        (
                            fadeIn(tween(READER_FADE_MS)) +
                                slideInHorizontally(tween(READER_SLIDE_MS)) {
                                    if (forward) it / READER_SLIDE_DIVISOR else -it / READER_SLIDE_DIVISOR
                                }
                            ) togetherWith (
                            fadeOut(tween(READER_FADE_MS)) +
                                slideOutHorizontally(tween(READER_SLIDE_MS)) {
                                    if (forward) -it / READER_SLIDE_DIVISOR else it / READER_SLIDE_DIVISOR
                                }
                            )
                    },
                    label = "reader-chapter"
                ) { target ->
                    ReaderContent(
                        state = target,
                        onTargetApplied = viewModel::onTargetApplied,
                        onHighlightCleared = viewModel::clearHighlight,
                        onVerseSelected = viewModel::onVerseSelected,
                        onVerseLongPress = { verseId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            actionVerseId = verseId
                        },
                        onVerseBookmarkAction = viewModel::toggleBookmarkFor,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous
                    )
                }
                else -> ReaderLoadingState()
            }
            }
            // F2-11/F2-12: playback sits above the action bar and never
            // covers the verse; the bar is empty when nothing is happening.
            ReaderSpeechBar(
                speech = speechState,
                verseNumber = verseNumberFor(state, speechState.verseId),
                onTogglePause = viewModel::toggleSpeechPause,
                onStop = viewModel::stopSpeech,
                onDismiss = viewModel::dismissSpeechIssue
            )
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

    // F-23: one sheet for the verse a reader long-pressed. Copy/share use the
    // domain plain text; the bookmark action keeps the existing Undo flow.
    val actionVerse = actionVerseId?.let { id ->
        state.verses.firstOrNull { it.id == id }
            ?: state.passageChapters
                .asSequence()
                .flatMap { it.verses.asSequence() }
                .firstOrNull { it.id == id }
    }
    if (actionVerse != null) {
        val bookName = state.bookDisplayName.ifBlank {
            state.book?.displayName.orEmpty()
        }
        val shareText = verseClipboardText(
            bookName = bookName,
            chapterNumber = actionVerse.chapterNumber,
            verseStart = actionVerse.verseStart,
            verseEnd = actionVerse.verseEnd,
            plainText = actionVerse.plainText
        )
        VerseActionSheet(
            isBookmarked = state.bookmarkedVerseIds.contains(actionVerse.id),
            onDismiss = { actionVerseId = null },
            onRead = {
                actionVerseId = null
                viewModel.readVerse(actionVerse.id)
            },
            onCopy = {
                scope.launch {
                    // Compose 1.9's Clipboard API is suspend; copy first, then
                    // dismiss, so the sheet never closes before the text lands.
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                context.getString(R.string.reader_copy_verse),
                                shareText
                            )
                        )
                    )
                    actionVerseId = null
                    snackbarHostState.showSnackbar(context.getString(R.string.reader_verse_copied))
                }
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                actionVerseId = null
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.reader_share_chooser))
                )
            },
            onToggleBookmark = {
                actionVerseId = null
                viewModel.toggleBookmarkFor(actionVerse.id)
            }
        )
    }
}

/**
 * F-23: Copy / Share / Bookmark for one verse, opened by long-press.
 *
 * Deliberately not a text-range `SelectionContainer`: the verse row owns tap
 * (select), long-press (this sheet) and horizontal swipe (chapter), and a
 * nested selection container would fight all three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerseActionSheet(
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reader.action_sheet")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.reader_verse_actions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            // F2-11: reading is a per-verse action like copy/share, so it
            // belongs in the same sheet rather than in the crowded bar.
            SheetAction(
                label = stringResource(R.string.reader_read_verse),
                tag = "reader.action_read",
                onClick = onRead
            )
            SheetAction(
                label = stringResource(R.string.reader_copy_verse),
                tag = "reader.action_copy",
                onClick = onCopy
            )
            SheetAction(
                label = stringResource(R.string.reader_share_verse),
                tag = "reader.action_share",
                onClick = onShare
            )
            SheetAction(
                label = stringResource(
                    if (isBookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark
                ),
                tag = "reader.action_bookmark",
                onClick = onToggleBookmark
            )
        }
    }
}

@Composable
private fun SheetAction(label: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Verse number of [verseId] in the rendered chapter or passage, for the strip. */
private fun verseNumberFor(state: ReaderUiState, verseId: Long?): Int? {
    if (verseId == null) return null
    return state.verses.firstOrNull { it.id == verseId }?.verseStart
        ?: state.passageChapters
            .asSequence()
            .flatMap { it.verses.asSequence() }
            .firstOrNull { it.id == verseId }
            ?.verseStart
}

/**
 * F2-11/F2-12: the reader's playback strip, above the action bar.
 *
 * Two shapes share one slot. While a verse is being read the strip names it,
 * shows the word the engine is on, and offers pause/resume and stop. When the
 * request cannot be spoken at all — Romi Konkani with no Konkani voice,
 * missing English voice data, or another app holding the audio — the strip
 * says so instead of staying silent. It is a polite live region, so a screen
 * reader hears the change without being moved out of the verse.
 */
@Composable
private fun ReaderSpeechBar(
    speech: ReaderSpeechState,
    verseNumber: Int?,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!speech.isVisible) return
    val issue = speech.issue
    if (issue != null) {
        SpeechNotice(issue = issue, onDismiss = onDismiss)
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader.tts_bar")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = speechTitle(speech, verseNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // The word `onRangeStart` reported. Highlighting inside the
                // verse text itself needs addressable rendering (S1-02); until
                // then this is the range-level feedback, and the verse keeps
                // its target surface.
                speech.spokenSegment?.let { segment ->
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val speaking = speech.phase == ReaderSpeechPhase.Speaking
            if (speaking || speech.phase == ReaderSpeechPhase.Paused) {
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.testTag("reader.tts_pause")
                ) {
                    Icon(
                        painter = painterResource(
                            if (speaking) R.drawable.ic_pause else R.drawable.ic_play_arrow
                        ),
                        contentDescription = stringResource(
                            if (speaking) R.string.reader_tts_pause else R.string.reader_tts_resume
                        )
                    )
                }
            }
            IconButton(
                onClick = onStop,
                modifier = Modifier.testTag("reader.tts_stop")
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = stringResource(R.string.reader_tts_stop)
                )
            }
        }
    }
}

@Composable
private fun speechTitle(speech: ReaderSpeechState, verseNumber: Int?): String =
    when (speech.phase) {
        ReaderSpeechPhase.Preparing -> stringResource(R.string.reader_tts_starting)
        ReaderSpeechPhase.Speaking -> if (verseNumber != null) {
            stringResource(R.string.reader_tts_reading_verse, verseNumber)
        } else {
            stringResource(R.string.reader_tts_reading)
        }
        ReaderSpeechPhase.Paused -> if (verseNumber != null) {
            stringResource(R.string.reader_tts_paused_verse, verseNumber)
        } else {
            stringResource(R.string.reader_tts_paused)
        }
        else -> ""
    }

/** F2-12: why nothing was spoken. A limitation, never an accusation at the reader. */
@Composable
private fun SpeechNotice(issue: ReaderSpeechIssue, onDismiss: () -> Unit) {
    // At 2x the title, body and button cannot share a narrow row without
    // eating the page: stack the action under the text, like the passage
    // banner does.
    val stacked = LocalDensity.current.fontScale >= AppBreakpoints.ExtraLargeFontScale
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader.tts_notice")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SpeechNoticeText(issue)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp)
                        .testTag("reader.tts_dismiss")
                ) {
                    Text(text = stringResource(R.string.reader_dismiss))
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) { SpeechNoticeText(issue) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reader.tts_dismiss")
                ) {
                    Text(text = stringResource(R.string.reader_dismiss))
                }
            }
        }
    }
}

@Composable
private fun SpeechNoticeText(issue: ReaderSpeechIssue) {
    Text(
        text = stringResource(speechIssueTitle(issue)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer
    )
    Text(
        text = stringResource(speechIssueBody(issue)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer
    )
}

private fun speechIssueTitle(issue: ReaderSpeechIssue): Int = when (issue) {
    ReaderSpeechIssue.NoKonkaniVoice -> R.string.reader_tts_no_konkani_voice
    ReaderSpeechIssue.EnglishVoiceDataMissing -> R.string.reader_tts_voice_missing
    ReaderSpeechIssue.EnglishVoiceNotSupported -> R.string.reader_tts_voice_unsupported
    ReaderSpeechIssue.AudioBusy -> R.string.reader_tts_audio_busy
    ReaderSpeechIssue.EngineFailed -> R.string.reader_tts_failed
}

private fun speechIssueBody(issue: ReaderSpeechIssue): Int = when (issue) {
    ReaderSpeechIssue.NoKonkaniVoice -> R.string.reader_tts_no_konkani_voice_body
    ReaderSpeechIssue.EnglishVoiceDataMissing -> R.string.reader_tts_voice_missing_body
    ReaderSpeechIssue.EnglishVoiceNotSupported -> R.string.reader_tts_voice_unsupported_body
    ReaderSpeechIssue.AudioBusy -> R.string.reader_tts_audio_busy_body
    ReaderSpeechIssue.EngineFailed -> R.string.reader_tts_failed_body
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    // A long book name in a fixed-height bar loses its ending at 2x. One step
    // down keeps more of the name visible, matching Settings and About.
    val titleStyle = if (LocalDensity.current.fontScale >= AppBreakpoints.ExtraLargeFontScale) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.titleMedium
    }
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
                style = titleStyle
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
        },
        actions = {
            // F-15: Settings & About must be reachable while reading, not
            // only from Home.
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("reader.menu")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.home_about_and_settings)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_about_and_settings)) },
                        onClick = {
                            menuOpen = false
                            onOpenAbout()
                        },
                        modifier = Modifier.testTag("reader.menu.about")
                    )
                }
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
        // F-27/Day 20 matrix: at 2x the full "Chapter 3" ellipsized to
        // "Chapter…", which says nothing. Keep the numeral and describe it.
        val compactSelector = LocalDensity.current.fontScale >= AppBreakpoints.LargeFontScale
        TextButton(
            onClick = onOpenChapterPicker,
            enabled = state.chapter != null && state.chapters.isNotEmpty(),
            modifier = Modifier
                .weight(1.4f)
                .heightIn(min = 48.dp)
                .testTag("reader.chapter_picker")
        ) {
            // F-27: no chapter yet (loading/error) -> no dangling "Chapter ".
            if (currentLabel.isNotBlank()) {
                val spoken = stringResource(R.string.chapter_selector_label, currentLabel)
                Text(
                    text = if (compactSelector) currentLabel else spoken,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = spoken }
                )
            }
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
        val bookmarkScale by animateFloatAsState(
            targetValue = if (isBookmarked) 1.12f else 1f,
            animationSpec = tween(BOOKMARK_SCALE_MS),
            label = "bookmark-scale"
        )
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
                },
                modifier = Modifier.scale(bookmarkScale)
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
    val columns = if (LocalDensity.current.fontScale >= AppBreakpoints.LargeFontScale) 3 else 5
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
internal fun PassageBanner(
    state: ReaderUiState,
    onShowFullChapter: () -> Unit
) {
    // At 2x the button and the verse label cannot share a narrow row: the
    // label breaks mid-word ("Verse / s 31…"). Stack them so both stay whole.
    val stacked = LocalDensity.current.fontScale >= AppBreakpoints.ExtraLargeFontScale
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("reader.passage_banner")
    ) {
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = passageLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onShowFullChapter,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp)
                        .testTag("reader.show_full_chapter")
                ) {
                    Text(text = stringResource(R.string.reader_show_full_chapter))
                }
            }
        } else {
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader.target_missing")
            // Audit P3-2: announce the notice when it appears.
            .semantics { liveRegion = LiveRegionMode.Polite }
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
