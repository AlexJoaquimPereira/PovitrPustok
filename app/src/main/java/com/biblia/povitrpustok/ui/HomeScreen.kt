package com.biblia.povitrpustok.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.ReadingState
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle

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
 *    two-column grid whose fifth cell is the accent-coloured Daily Reading;
 *  - the corpus notice, unchanged and still the last thing in the viewport.
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

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
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.height(28.dp))

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeDestinationCell(
                        marker = "02",
                        label = stringResource(R.string.home_search),
                        subtitle = stringResource(R.string.home_subtitle_search),
                        onClick = onOpenSearch,
                        tag = "home.search",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    HomeDestinationCell(
                        marker = "03",
                        label = stringResource(R.string.home_bookmarks),
                        subtitle = stringResource(R.string.home_subtitle_bookmarks),
                        onClick = onOpenBookmarks,
                        tag = "home.bookmarks",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeDestinationCell(
                        marker = "04",
                        label = stringResource(R.string.home_daily_reading),
                        subtitle = stringResource(R.string.home_subtitle_daily),
                        onClick = onOpenDailyReading,
                        tag = "home.daily",
                        accent = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    HomeDestinationCell(
                        marker = "05",
                        label = stringResource(R.string.home_reference),
                        subtitle = stringResource(R.string.home_subtitle_reference),
                        onClick = onOpenReference,
                        tag = "home.reference",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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

/**
 * "Continue reading" (F-11): the returning reader's shortest path back.
 *
 * Shows the resolved book name (F-12) plus a localised relative time from
 * [DateUtils]; a chapter number is intentionally absent — `chapterPosition`
 * is a navigation key, not a display number (Job repeats source numbers).
 */
@Composable
private fun ContinueReadingCard(
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
                    maxLines = 1,
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
 * One cell of the two-column destination grid (F-11).
 *
 * Secondary cells are hairline tiles on the page background — quiet on
 * AMOLED black and on paper-white light theme alike — except [accent], which
 * fills with `primaryContainer` because today's Mass is time-sensitive.
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
    accent: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (accent) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val supportingColor =
        if (accent) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 96.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = if (accent) colorScheme.primaryContainer else colorScheme.background,
        contentColor = contentColor,
        border = if (accent) null else BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
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
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
