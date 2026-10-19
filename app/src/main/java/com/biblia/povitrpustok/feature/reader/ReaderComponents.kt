package com.biblia.povitrpustok.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.core.html.BibleHtmlText
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.feature.common.chapterDisplayLabel
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.ui.theme.BibleTextStyle
import kotlinx.coroutines.delay

/**
 * Reusable reader components (implementation plan §42–§43).
 *
 * All Scripture and heading HTML is rendered exclusively through the
 * existing [BibleHtmlParser] + [BibleHtmlText] pipeline; components never
 * touch raw HTML strings, `text_plain`, or the database.
 */

private val htmlParser = BibleHtmlParser()

/** Parses (and caches per recomposition) heading/verse HTML. */
@Composable
private fun rememberDocument(html: String): BibleHtmlDocument =
    remember(html) { htmlParser.parse(html) }

/** Chapter number as the reading header (plan §42). */
@Composable
fun ChapterHeader(chapter: Chapter, modifier: Modifier = Modifier) {
    Text(
        text = chapter.number.toString(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

/** One heading block; multiple headings render as separate blocks. */
@Composable
fun HeadingBlock(heading: Heading, modifier: Modifier = Modifier) {
    BibleHtmlText(
        document = rememberDocument(heading.html),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium
    )
}

/**
 * One verse: number + formatted Scripture from Verse.text.
 *
 * [isTarget] applies a temporary, theme-derived background to the target
 * verse only (Phase 8); [isBookmarked] shows a persistent, subtle bookmark
 * indicator (Phase 9); [isSelected] marks a tapped verse (corrective
 * phase Issue 6) so the top-bar bookmark action is unambiguous. The three
 * states are independent and neither modifies the canonical Verse content.
 */
@Composable
fun VerseBlock(
    verse: Verse,
    bookId: String? = null,
    isTarget: Boolean = false,
    isBookmarked: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val document = rememberDocument(verse.html)
    val background = when {
        isTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
        isBookmarked -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier
            )
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        VerseNumber(
            verseStart = verse.verseStart,
            verseEnd = verse.verseEnd,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        // Issue 8: Psalm verses without their own alignment render as a
        // centered poetic block (the <br> line structure is preserved by
        // the renderer; nothing is flattened).
        BibleHtmlText(
            document = document,
            modifier = Modifier.weight(1f),
            style = BibleTextStyle,
            textAlign = if (PsalmPoetry.isPoeticVerse(bookId, document)) TextAlign.Center else null
        )
    }
}

/**
 * Verse number: `verse_start` for ordinary verses; a compact range
 * (`11–12`) when verse_start != verse_end. A range stays one Verse.
 */
@Composable
fun VerseNumber(
    verseStart: Int,
    verseEnd: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (verseStart == verseEnd) {
            verseStart.toString()
        } else {
            stringResource(R.string.verse_range, verseStart, verseEnd)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Scrollable chapter surface: header, introductions, headings, verses. */
@Composable
fun ReaderContent(
    state: ReaderUiState,
    onTargetApplied: () -> Unit = {},
    onHighlightCleared: () -> Unit = {},
    onVerseSelected: (Long) -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chapter = state.chapter ?: return
    // Keyed on the chapter identity: a new chapter gets a fresh list state
    // (starts at the top); recomposition of the same chapter preserves the
    // scroll position (Track A §E).
    val listState = remember(state.chapter?.id) { LazyListState() }

    // One authoritative display sequence (corrective phase Issues 3 & 5):
    // normal mode = header + introductions + interleaved headings/verses;
    // targeted mode = the passage-filtered sequence.
    val items = remember(chapter.id, state.introductions, state.headings, state.verses, state.passage) {
        ReaderContentBuilder.build(
            chapter = chapter,
            introductions = state.introductions,
            headings = state.headings,
            verses = state.verses,
            passage = state.passage
        )
    }

    // One-shot scroll to the target verse: the index comes from the exact
    // same item list that is rendered (no separate index logic).
    LaunchedEffect(state.targetVerseId, items) {
        val targetId = state.targetVerseId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it is ReaderContentItem.Verse && it.verse.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
        onTargetApplied()
    }

    // Temporary highlight (Phase 8): cleared after a short duration.
    LaunchedEffect(state.highlightedVerseId) {
        val highlighted = state.highlightedVerseId ?: return@LaunchedEffect
        delay(HIGHLIGHT_DURATION_MILLIS)
        onHighlightCleared()
    }

    // Horizontal swipe -> chapter navigation (corrective phase Issue 7).
    // The gesture is only *observed* (never consumed), so the LazyColumn's
    // vertical scrolling is untouched; only predominantly horizontal drags
    // past the threshold navigate, and only within chapter boundaries.
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.toPx() }
    val canGoNext = state.canGoNext
    val canGoPrevious = state.canGoPrevious

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(chapter.id, canGoNext, canGoPrevious) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        totalX += change.position.x - change.previousPosition.x
                        totalY += change.position.y - change.previousPosition.y
                        if (event.changes.none { it.pressed }) break
                    }
                    when (swipeDirection(totalX, totalY, swipeThresholdPx)) {
                        SwipeDirection.NEXT -> if (canGoNext) onNext()
                        SwipeDirection.PREVIOUS -> if (canGoPrevious) onPrevious()
                        SwipeDirection.NONE -> Unit
                    }
                }
            },
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ReaderContentItem.Header -> ChapterHeader(item.chapter)
                is ReaderContentItem.Introduction -> IntroductionBlockView(item.block)
                is ReaderContentItem.Heading -> HeadingBlock(item.heading)
                is ReaderContentItem.Verse -> VerseBlock(
                    verse = item.verse,
                    bookId = state.book?.id,
                    isTarget = item.verse.id == state.highlightedVerseId,
                    isBookmarked = state.bookmarkedVerseIds.contains(item.verse.id),
                    isSelected = item.verse.id == state.selectedVerseId,
                    onSelect = { onVerseSelected(item.verse.id) }
                )
            }
        }
    }
}

/** Horizontal distance (dp) a swipe must exceed to navigate chapters. */
private val SWIPE_THRESHOLD_DP = 96.dp

/** Highlight duration for a targeted verse (Phase 8, modest and local). */
private const val HIGHLIGHT_DURATION_MILLIS = 2_000L

/** Introduction block rendered in source order via the HTML pipeline. */
@Composable
private fun IntroductionBlockView(block: IntroductionBlock, modifier: Modifier = Modifier) {
    BibleHtmlText(
        document = rememberDocument(block.html),
        modifier = modifier.fillMaxWidth(),
        style = BibleTextStyle
    )
}

/** Centered loading indicator. */
@Composable
fun ReaderLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Error state with retry. */
@Composable
fun ReaderErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.reader_retry))
        }
    }
}

/**
 * Basic chapter selector: displays `chapter.number`, selects by
 * `chapter.position` (plan §17). The chapter list stays domain objects.
 */
@Composable
fun ChapterSelector(
    chapters: List<Chapter>,
    currentPosition: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentChapter = chapters.firstOrNull { it.position == currentPosition }
    val currentLabel = currentChapter?.let { chapterDisplayLabel(it, chapters) }
        ?: currentPosition.toString()
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(text = stringResource(R.string.chapter_selector_label, currentLabel))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            chapters.forEach { chapter ->
                DropdownMenuItem(
                    text = { Text(text = chapterDisplayLabel(chapter, chapters)) },
                    onClick = {
                        expanded = false
                        if (chapter.position != currentPosition) {
                            onSelect(chapter.position)
                        }
                    }
                )
            }
        }
    }
}
