package com.biblia.povitrpustok.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth

/**
 * Home launcher, editorial pass (F-11, implementation plan §10.7, §13, §21).
 *
 * The first screen reads as a masthead rather than a debug menu:
 *
 *  - overline "KONKANI CATHOLIC BIBLE", a serif hero, a short primary rule;
 *  - "Continue reading" (only when a reading-state record exists), naming the
 *    book and how long ago it was opened — never a chapter number, because
 *    `chapterPosition` is not a display number (Job repeats source numbers);
 *  - destinations 01–05: Bible as the single primary block, then a compact
 *    two-column grid whose fourth cell is the accent-coloured Daily Reading;
 *  - the Settings link as the quiet footer; the corpus copyright now lives on
 *    the About > Source edition page instead of being repeated here.
 *
 * Day 14 adds the responsive/accessibility pass on top of that composition:
 *
 *  - [homeStacksDestinations] collapses the grid into one column once the
 *    system font scale reaches 1.5x or the window is narrower than 360 dp, so
 *    a cell never trades a 48 dp touch target for a clipped label;
 *  - landscape (short) windows tighten the masthead rhythm instead of pinning
 *    anything to a fixed height, so every destination stays one scroll away;
 *  - the footer is a merged, clickable "Settings & about" entry point (the
 *    route is wired by the caller).
 *
 * Test-safety (HomeNavigationUiTest): each cell is one merged clickable
 * surface, so "Bible" / "Search" / "Bookmarks" resolve to exactly one node
 * each and clicking it still navigates; subtitles are deliberately different
 * strings so they cannot produce a second match. No Bible content is loaded
 * here.
 *
 * The screen is edge-to-edge safe: [WindowInsets.safeDrawing] keeps content
 * clear of the status/navigation bars (including 3-button navigation), and
 * the whole column scrolls so every destination and the notice are visible
 * on first launch at 1x and reachable at larger font scales or in landscape.
 */
@Composable
fun HomeScreen(
    onOpenBooks: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenReference: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onContinueReading: (ReadingState) -> Unit = {},
    onOpenDailyReading: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continueReading = uiState.continueReading

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Window width before the 24 dp gutters, so the threshold describes
            // the device rather than the content column.
            val stacks = homeStacksDestinations(
                maxWidth = maxWidth,
                fontScale = LocalDensity.current.fontScale
            )
            // A landscape phone is ~370 dp tall once the bars are subtracted;
            // tightening the masthead there buys back a destination or two
            // before the first scroll without dropping the identity lines.
            val compact = maxHeight < AppBreakpoints.CompactHeight

            Column(
                modifier = Modifier
                    // Landscape and tablets keep a composed editorial column
                    // instead of stretching the tiles to the full window.
                    .align(Alignment.TopCenter)
                    .widthIn(max = ReadingColumnMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (compact) 16.dp else 32.dp))

                Text(
                    text = stringResource(R.string.home_overline),
                    style = OverlineTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home.overline")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("home.hero")
                )
                Spacer(modifier = Modifier.height(if (compact) 12.dp else 16.dp))

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.height(if (compact) 20.dp else 28.dp))

                if (continueReading != null) {
                    ContinueReadingCard(
                        bookName = continueReading.bookName,
                        updatedAt = continueReading.reading.updatedAt,
                        onClick = { onContinueReading(continueReading.reading) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home.grid")
                ) {
                    PrimaryDestination(
                        marker = "01",
                        label = stringResource(R.string.home_bible),
                        subtitle = stringResource(
                            R.string.home_subtitle_bible,
                            BibleCatalog.books.size
                        ),
                        onClick = onOpenBooks,
                        tag = "home.bible"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    DestinationGrid(
                        destinations = listOf(
                            HomeDestination(
                                marker = "02",
                                label = stringResource(R.string.home_search),
                                subtitle = stringResource(R.string.home_subtitle_search),
                                tag = "home.search",
                                onClick = onOpenSearch
                            ),
                            HomeDestination(
                                marker = "03",
                                label = stringResource(R.string.home_bookmarks),
                                subtitle = stringResource(R.string.home_subtitle_bookmarks),
                                tag = "home.bookmarks",
                                onClick = onOpenBookmarks
                            ),
                            HomeDestination(
                                marker = "04",
                                label = stringResource(R.string.home_daily_reading),
                                subtitle = stringResource(R.string.home_subtitle_daily),
                                tag = "home.daily",
                                onClick = onOpenDailyReading,
                                accent = true
                            ),
                            HomeDestination(
                                marker = "05",
                                label = stringResource(R.string.home_reference),
                                subtitle = stringResource(R.string.home_subtitle_reference),
                                tag = "home.reference",
                                onClick = onOpenReference
                            )
                        ),
                        stacked = stacks,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 20.dp else 32.dp))

                AboutFooter(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(if (compact) 16.dp else 28.dp))
            }
        }
    }
}

/**
 * Responsive switch for the destination layout (F-11, Day 14).
 *
 * Two columns stop working when a cell can no longer hold a 48 dp touch target
 * *and* a readable label: at a 1.5x system font scale a 13-character label
 * needs more than half of a phone's width, and below 360 dp the cells are
 * narrower than the label itself. Both cases fall back to one column, keeping
 * the same 01–05 order.
 *
 * [maxWidth] is the window width *before* the 24 dp gutters, so a 393 dp phone
 * keeps the two-column grid at 1x.
 */
internal fun homeStacksDestinations(maxWidth: Dp, fontScale: Float): Boolean =
    fontScale >= AppBreakpoints.LargeFontScale || maxWidth < AppBreakpoints.HomeGridMinWidth

/** One secondary destination, in the order it is numbered on screen. */
private class HomeDestination(
    val marker: String,
    val label: String,
    val subtitle: String,
    val tag: String,
    val onClick: () -> Unit,
    val accent: Boolean = false
)

/**
 * Two columns when there is room, one column when there is not.
 *
 * The stacked form draws the same tiles as full-width rows: the marker moves
 * beside the label and a chevron appears, so the numbered cells read as a
 * contents list rather than as a grid that fell over.
 */
@Composable
private fun DestinationGrid(
    destinations: List<HomeDestination>,
    stacked: Boolean,
    modifier: Modifier = Modifier
) {
    if (stacked) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            destinations.forEach { destination ->
                HomeDestinationCell(
                    marker = destination.marker,
                    label = destination.label,
                    subtitle = destination.subtitle,
                    onClick = destination.onClick,
                    tag = destination.tag,
                    accent = destination.accent,
                    horizontal = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            destinations.chunked(2).forEachIndexed { index, rowDestinations ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowDestinations.forEach { destination ->
                        HomeDestinationCell(
                            marker = destination.marker,
                            label = destination.label,
                            subtitle = destination.subtitle,
                            onClick = destination.onClick,
                            tag = destination.tag,
                            accent = destination.accent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quiet footer (F-11, F-15): the Settings entry point.
 *
 * The whole block is one merged clickable surface — no separate button, no
 * second label competing with the link — so the tap target clears 48 dp. The
 * footer no longer repeats the corpus copyright: that statement now lives on
 * its own Source edition page, where it has room to be read properly.
 */
@Composable
private fun AboutFooter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val supportingColor = colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("home.notice")
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = colorScheme.background,
        contentColor = supportingColor
    ) {
        Column(
            // No horizontal padding: the 24 dp page gutters are the inset.
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_about_and_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * "Continue reading" (F-11): the returning reader's shortest path back.
 *
 * Shows the resolved book name (F-12) plus a localised relative time from
 * [DateUtils]; a chapter number is intentionally absent — `chapterPosition`
 * is a navigation key, not a display number (Job repeats source numbers).
 * Stays full width in every layout, including the stacked large-font one.
 */
@Composable
internal fun ContinueReadingCard(
    bookName: String,
    updatedAt: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Recomputed on every composition (rather than remembered) so the card
    // never shows a frozen span after the screen has been idle or resumed.
    val now = System.currentTimeMillis()
    val relativeTime = if (now - updatedAt < DateUtils.MINUTE_IN_MILLIS) {
        // DateUtils would render a fresh chapter as "0 minutes ago".
        stringResource(R.string.home_read_just_now)
    } else {
        // A clock change can leave updatedAt in the future; never claim the
        // chapter will be read "in N minutes".
        DateUtils.getRelativeTimeSpanString(
            minOf(updatedAt, now),
            now,
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .testTag("home.continue")
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_continue_reading).uppercase(),
                    style = OverlineTextStyle,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookName,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    // At 2x the card grows instead of truncating the book
                    // name; the longest Konkani titles need three lines on a
                    // phone at 200%.
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
    }
}

/**
 * The primary action (F-11): Bible is the front door, so it owns a full-width
 * block in the accent colour instead of competing with the grid.
 */
@Composable
private fun PrimaryDestination(
    marker: String,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onPrimary

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker,
                    style = OverlineTextStyle,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
    }
}

/**
 * One destination (F-11).
 *
 * Secondary cells are hairline tiles on the page background — quiet on
 * AMOLED black and on paper-white light theme alike — except [accent], which
 * fills with `primaryContainer` because today's Mass is time-sensitive.
 *
 * [horizontal] is the large-font/landscape form: a full-width row with the
 * marker leading the label. In that form the label has no line cap, so it can
 * wrap as far as it needs instead of being truncated; only the supporting
 * subtitle is allowed to ellipsise.
 *
 * Semantics merge into a single clickable node so a text match still lands on
 * the whole 48 dp+ touch target.
 */
@Composable
private fun HomeDestinationCell(
    marker: String,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    horizontal: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (accent) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val supportingColor =
        if (accent) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = if (horizontal) 72.dp else 96.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = if (accent) colorScheme.primaryContainer else colorScheme.background,
        contentColor = contentColor,
        border = if (accent) null else BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = marker,
                    style = OverlineTextStyle,
                    color = supportingColor,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = supportingColor,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = marker,
                    style = OverlineTextStyle,
                    color = supportingColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
