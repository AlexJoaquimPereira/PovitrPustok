package com.biblia.povitrpustok.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.core.html.BibleHtmlText
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.ui.theme.LocalScripturePalette
import com.biblia.povitrpustok.ui.theme.PoetryColumnMaxWidth
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth
import com.biblia.povitrpustok.ui.theme.VerseGutterWidth
import com.biblia.povitrpustok.ui.theme.ScripturePoetryStyle
import com.biblia.povitrpustok.ui.theme.ScriptureTextStyle
import com.biblia.povitrpustok.ui.theme.VerseNumberStyle
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

/** Chapter number as a quiet overline; the corpus heading is the title (F-25). */
@Composable
fun ChapterHeader(
    chapter: Chapter,
    modifier: Modifier = Modifier,
    verseRange: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.reader_chapter_number, chapter.number).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // F-04: passage context belongs in the scrolling header, not the bar.
        if (verseRange != null) {
            Text(
                text = verseRange,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
 * F-06: the reader states use solid, theme-aware surfaces from
 * [LocalScripturePalette] instead of alpha tints, so a state is visible on
 * both light paper and true black. [isTarget] marks the transient highlight
 * (Phase 8), [isSelected] the tapped verse (corrective phase Issue 6) and
 * [isBookmarked] a persistent bookmark (Phase 9). Target wins over selected,
 * selected over bookmarked. A 2 dp gutter accent bar marks target/selected
 * and a small tab marks a bookmark; the same states are announced to
 * accessibility services through `stateDescription`. None of the states
 * modify the canonical Verse content.
 */
@Composable
fun VerseBlock(
    verse: Verse,
    bookId: String? = null,
    isTarget: Boolean = false,
    isBookmarked: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val document = rememberDocument(verse.html)
    val palette = LocalScripturePalette.current
    val gutterWidth = with(LocalDensity.current) { (VerseGutterWidth.value * fontScale).dp }
    val background = when {
        isTarget -> palette.target
        isSelected -> palette.selection
        isBookmarked -> palette.bookmarked
        else -> Color.Transparent
    }
    val stateDescription = verseStateDescription(isTarget, isSelected, isBookmarked)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (stateDescription != null) {
                    Modifier.semantics { this.stateDescription = stateDescription }
                } else {
                    Modifier
                }
            )
            .then(
                when {
                    // F-09: long-press bookmarks the verse directly; tap
                    // still selects it for the top-bar action.
                    onLongPress != null -> Modifier.combinedClickable(
                        onClick = { onSelect?.invoke() },
                        onLongClick = onLongPress
                    )
                    onSelect != null -> Modifier.clickable(onClick = onSelect)
                    else -> Modifier
                }
            )
            .background(background, MaterialTheme.shapes.small)
            // F-06: markers live in the gutter/end padding, drawn after the
            // background so they stay visible on a state surface. Drawing
            // never changes the row's measured size or the item keys.
            .drawBehind {
                if (isTarget || isSelected) {
                    val barWidth = GUTTER_ACCENT_WIDTH.toPx()
                    drawRoundRect(
                        color = palette.gutterAccent,
                        topLeft = Offset.Zero,
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }
                if (isBookmarked) {
                    val tabWidth = BOOKMARK_TAB_WIDTH.toPx()
                    val tabInset = BOOKMARK_TAB_INSET.toPx()
                    drawRoundRect(
                        color = palette.gutterAccent,
                        topLeft = Offset(size.width - tabWidth - tabInset, tabInset),
                        size = Size(
                            tabWidth,
                            BOOKMARK_TAB_HEIGHT.toPx().coerceAtMost(size.height - tabInset * 2)
                        ),
                        cornerRadius = CornerRadius(tabWidth / 2f)
                    )
                }
            }
            .padding(start = 4.dp, top = 2.dp, end = 10.dp, bottom = 2.dp)
    ) {
        VerseNumber(
            verseStart = verse.verseStart,
            verseEnd = verse.verseEnd,
            modifier = Modifier.widthIn(min = gutterWidth).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Issue 8: Psalm verses without their own alignment render as a
        // centered poetic block (the <br> line structure is preserved by
        // the renderer; nothing is flattened).
        if (PsalmPoetry.isPoeticVerse(bookId, document)) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BibleHtmlText(
                    document = document,
                    modifier = Modifier.widthIn(max = PoetryColumnMaxWidth),
                    style = ScripturePoetryStyle,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            BibleHtmlText(
                document = document,
                modifier = Modifier.weight(1f),
                style = ScriptureTextStyle
            )
        }
    }
}

/**
 * Spoken state of one verse row, e.g. "Target verse. Selected. Bookmarked."
 * Returns null when the verse carries no state, so a plain verse keeps the
 * default accessibility description. F-06.
 */
@Composable
private fun verseStateDescription(
    isTarget: Boolean,
    isSelected: Boolean,
    isBookmarked: Boolean
): String? {
    val labels = buildList {
        if (isTarget) add(stringResource(R.string.reader_verse_state_target))
        if (isSelected) add(stringResource(R.string.reader_verse_state_selected))
        if (isBookmarked) add(stringResource(R.string.reader_verse_state_bookmarked))
    }
    if (labels.isEmpty()) return null
    // Sentence punctuation is not localized separately; keep the spoken
    // state compact and free of literal quote characters.
    return labels.joinToString(". ") + "."
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
        style = VerseNumberStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/** Scrollable chapter surface: header, interleaved headings and verses. */
@Composable
fun ReaderContent(
    state: ReaderUiState,
    onTargetApplied: () -> Unit = {},
    onHighlightCleared: () -> Unit = {},
    onVerseSelected: (Long) -> Unit = {},
    onVerseLongPress: (Long) -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chapter = state.chapter ?: return
    // Keyed on the chapter identity: a new chapter gets a fresh list state
    // (starts at the top); recomposition of the same chapter preserves the
    // scroll position (Track A §E).
    // DIST-01: survive process recreation; keyed by chapter so a new chapter
    // starts at the top while the same chapter restores its scroll offset.
    val listState = rememberSaveable(
        state.chapter?.id,
        saver = androidx.compose.foundation.lazy.LazyListState.Saver
    ) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    // One authoritative display sequence (corrective phase Issues 3 & 5;
    // READER-03): normal mode = interleaved headings/verses; targeted mode =
    // the passage-filtered sequence. Introductions live on their own screen.
    val items = remember(
        chapter.id,
        state.headings,
        state.verses,
        state.passage,
        state.passageChapters
    ) {
        if (state.passageChapters.isNotEmpty()) {
            state.passageChapters.flatMap { part ->
                ReaderContentBuilder.build(
                    chapter = part.chapter,
                    headings = part.headings,
                    verses = part.verses,
                    passage = part.passage
                )
            }
        } else {
            ReaderContentBuilder.build(
                chapter = chapter,
                headings = state.headings,
                verses = state.verses,
                passage = state.passage
            )
        }
    }

    // One-shot scroll to the target verse: the index comes from the exact
    // same item list that is rendered (no separate index logic).
    LaunchedEffect(state.targetVerseId, items) {
        val targetId = state.targetVerseId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it is ReaderContentItem.Verse && it.verse.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
            // Only consume a target that was actually rendered: a verse
            // filtered out by a passage range must not become a phantom
            // selection the bookmark action can act on.
            onTargetApplied()
        }
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(items, key = { it.key }) { item ->
            val itemWidth = Modifier.widthIn(max = ReadingColumnMaxWidth).fillMaxWidth()
            when (item) {
                is ReaderContentItem.Header -> ChapterHeader(
                    chapter = item.chapter,
                    modifier = itemWidth,
                    verseRange = passageRangeLabel(state, item.chapter)
                )
                is ReaderContentItem.Heading -> HeadingBlock(
                    item.heading,
                    itemWidth.padding(top = 16.dp, bottom = 4.dp)
                )
                is ReaderContentItem.Verse -> VerseBlock(
                    verse = item.verse,
                    bookId = state.book?.id,
                    isTarget = item.verse.id == state.highlightedVerseId,
                    isBookmarked = state.bookmarkedVerseIds.contains(item.verse.id),
                    isSelected = item.verse.id == state.selectedVerseId,
                    onSelect = { onVerseSelected(item.verse.id) },
                    onLongPress = { onVerseLongPress(item.verse.id) },
                    modifier = itemWidth.testTag("reader.verse.${item.verse.id}")
                )
            }
        }
    }
}

/**
 * F-04: "verses x-y" for the header of [chapter] when a passage is active.
 * Multi-chapter passages report each chapter's own span; a whole chapter has
 * no label (it would be noise).
 */
@Composable
private fun passageRangeLabel(state: ReaderUiState, chapter: Chapter): String? {
    val ranges = state.passageChapters
        .firstOrNull { it.chapter.id == chapter.id }
        ?.passage
        ?.ranges
        ?: state.passage?.let { listOf(it.verseStart..it.verseEnd) }
        ?: return null
    if (ranges.isEmpty()) return null
    val first = ranges.minOf { it.first }
    val last = ranges.maxOf { it.last }
    return if (first == last) {
        stringResource(R.string.reader_verse_only, first)
    } else {
        stringResource(R.string.verse_range, first, last)
    }
}

/** Horizontal distance (dp) a swipe must exceed to navigate chapters. */
private val SWIPE_THRESHOLD_DP = 96.dp

/** F-06 gutter accent bar width for target/selected verses. */
private val GUTTER_ACCENT_WIDTH = 2.dp

/** F-06 bookmark tab in the row's end padding (decorative; announced via stateDescription). */
private val BOOKMARK_TAB_WIDTH = 4.dp
private val BOOKMARK_TAB_HEIGHT = 18.dp
private val BOOKMARK_TAB_INSET = 2.dp

/** Highlight duration for a targeted verse (Phase 8, modest and local). */
private const val HIGHLIGHT_DURATION_MILLIS = 2_000L

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
