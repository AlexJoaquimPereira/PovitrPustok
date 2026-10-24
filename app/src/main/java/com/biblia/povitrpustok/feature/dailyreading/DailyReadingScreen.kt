package com.biblia.povitrpustok.feature.dailyreading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalErrorKind
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.domain.model.ReadingType
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth
import java.time.LocalDate
import java.util.Locale

/*
 * Vertical rhythm. One gap per relationship, so the column never depends on a
 * per-block magic number (same convention as Settings and Home).
 */
private val BlockGap = 28.dp
private val HeadingGap = 16.dp
private val ParagraphGap = 12.dp

/**
 * Daily Reading (Phase 10; F-18 editorial pass).
 *
 * The screen answers one devotional question — "what is the Church reading
 * today?" — with an editorial hierarchy instead of a flat link list:
 *
 *  - **Date navigation** lives under the app bar as screen state: previous /
 *    next day around [DailyReadingDates]' English date label, plus a "Today"
 *    action whenever the chosen day is not today. It is deliberately *not* a
 *    route, so the back stack still means "leave Daily Reading".
 *  - **Celebration identity** reads like a liturgy page: a season/type kicker,
 *    the celebration name as the title, the short primary rule from Home and
 *    Settings, then the quote in italic serif and the description.
 *  - **The Gospel is the feature**: one filled block in `primaryContainer`
 *    with a 2 dp `primary` rule, the reference at `titleLarge`, and the single
 *    filled action of the page ("Read the Gospel"). Days without a Gospel
 *    simply have no block — nothing is invented to fill the space.
 *  - **Other readings** are compact hairline rows (type label + reference),
 *    each a single merged 56 dp tap target.
 *  - **Freshness (F-18)** closes the page with "Saved for offline · updated
 *    HH:mm" and an "Update" action that bypasses the cache; a payload that
 *    arrived from the network this session has no footer at all. Stale simply
 *    means cached — there is no invented age threshold.
 *
 * Everything scrolls inside one [LazyColumn] capped to [ReadingColumnMaxWidth],
 * so 2× font scale, landscape and tablets wrap rather than clip. Only
 * [MaterialTheme] colours and typography are used, and every row that acts as
 * a link clears 48 dp.
 *
 * Test-safety: the top bar keeps `daily_reading_title` and the `back`
 * description, `onOpenReading` remains the only navigation callback, and
 * `viewModel.openReading` remains the row handler. Stable tags:
 * `daily.date`, `daily.prev`, `daily.next`, `daily.today`, `daily.gospel`,
 * `daily.read_gospel`, `daily.reading.<type>`, `daily.freshness`,
 * `daily.refresh`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReadingScreen(
    onOpenReading: (ReaderDestination) -> Unit,
    onBack: () -> Unit,
    viewModel: DailyReadingViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.refreshIfDateChanged() }
    // LITURGY-02: revalidate "today" when the screen resumes (e.g. app left
    // open across midnight). Same-day resumes make no request.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfDateChanged()
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val notice by viewModel.navigationNotice.collectAsStateWithLifecycle()

    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { destination ->
            onOpenReading(destination)
            viewModel.consumeNavigation()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DailyReadingTopBar(
                selectedDate = selectedDate,
                // The date flow is the single source of truth for "today", so
                // the action appears and disappears without leaving the day.
                showToday = !viewModel.isToday(selectedDate),
                onBack = onBack,
                onPreviousDay = viewModel::previousDay,
                onNextDay = viewModel::nextDay,
                onToday = viewModel::goToToday
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val current = state) {
                is DailyReadingUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is DailyReadingUiState.Error -> DailyReadingError(
                    // F-24: the repository's reason is typed; the copy lives here.
                    message = stringResource(
                        when (current.kind) {
                            LiturgicalErrorKind.NETWORK -> R.string.daily_error_network
                            LiturgicalErrorKind.HTTP -> R.string.daily_error_http
                            LiturgicalErrorKind.MALFORMED_RESPONSE -> R.string.daily_error_response
                            LiturgicalErrorKind.NO_DATA -> R.string.daily_error_no_data
                        }
                    ),
                    onRetry = { viewModel.load() }
                )

                is DailyReadingUiState.Success -> DailyReadingContent(
                    state = current,
                    notice = notice,
                    onReadingSelected = viewModel::openReading,
                    onNoticeDismissed = viewModel::consumeNotice,
                    onUpdate = viewModel::refresh
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome: title bar + date navigation
// ---------------------------------------------------------------------------

/**
 * App bar and the day navigator in one pinned block, closed by the F-17
 * hairline. The navigator stays put while the readings scroll, so stepping to
 * yesterday never requires scrolling back to the top; it also stays usable
 * while the day is loading or has failed, because the arrows are what get you
 * out of a bad day.
 *
 * The date label wraps instead of ellipsising: at 2× it may need two lines, and
 * a truncated date is worse than a taller bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyReadingTopBar(
    selectedDate: LocalDate,
    showToday: Boolean,
    onBack: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val dateLabel = remember(selectedDate) { DailyReadingDates.format(selectedDate) }

    Column(modifier = modifier.fillMaxWidth().background(colorScheme.surface)) {
        TopAppBar(
            title = { Text(stringResource(R.string.daily_reading_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ReadingColumnMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DayStepButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.daily_previous_day),
                        tag = "daily.prev",
                        onClick = onPreviousDay
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("daily.date")
                            .semantics { heading() }
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                    DayStepButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.daily_next_day),
                        tag = "daily.next",
                        onClick = onNextDay
                    )
                }

                if (showToday) {
                    // A quiet link, not a second filled button: the page keeps
                    // exactly one primary action (the Gospel).
                    TextButton(
                        onClick = onToday,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .heightIn(min = 48.dp)
                            .testTag("daily.today")
                    ) {
                        Text(
                            text = stringResource(R.string.daily_today),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colorScheme.outlineVariant)
    }
}

@Composable
private fun DayStepButton(
    icon: ImageVector,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

// ---------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------

@Composable
private fun DailyReadingError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(HeadingGap))
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(text = stringResource(R.string.reader_retry))
        }
    }
}

/**
 * The reading column. One [LazyColumn] with one item per block, so the whole
 * page scrolls as a unit at any font scale or orientation and the Gospel block
 * is never cut off by a fixed pane.
 */
@Composable
private fun DailyReadingContent(
    state: DailyReadingUiState.Success,
    notice: DailyNotice?,
    onReadingSelected: (LiturgicalReading) -> Unit,
    onNoticeDismissed: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val day = state.day
    // The Gospel is lifted out of the list into its own block. Only the first
    // one is promoted; anything else stays in the compact rows.
    val gospelIndex = day.readings.indexOfFirst { it.type == ReadingType.GOSPEL }
    val gospel = day.readings.getOrNull(gospelIndex)
    val otherReadings = if (gospelIndex >= 0) {
        day.readings.filterIndexed { index, _ -> index != gospelIndex }
    } else {
        day.readings
    }

    LazyColumn(
        modifier = modifier
            .widthIn(max = ReadingColumnMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(BlockGap)
    ) {
        if (day.hasIdentity()) {
            item(key = "identity") { DayIdentity(day = day) }
        }

        if (notice != null) {
            item(key = "notice") {
                NoticeBanner(message = notice.message(), onDismiss = onNoticeDismissed)
            }
        }

        // Requirement: no Gospel, no block. The page falls back to the compact
        // rows alone rather than showing an empty feature.
        if (gospel != null) {
            item(key = "gospel") {
                GospelFeature(
                    reading = gospel,
                    onRead = { onReadingSelected(gospel) }
                )
            }
        }

        if (otherReadings.isNotEmpty()) {
            item(key = "others") {
                OtherReadings(
                    readings = otherReadings,
                    onReadingSelected = onReadingSelected
                )
            }
        }

        if (day.readings.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.daily_reading_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.cachedAt?.let { cachedAt ->
            item(key = "freshness") {
                FreshnessFooter(cachedAt = cachedAt, onUpdate = onUpdate)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Celebration identity
// ---------------------------------------------------------------------------

/**
 * The day's identity, in the reading voice: an overline kicker (season ·
 * type), the celebration title, the house rule, then the quote and description.
 *
 * Absent fields render nothing at all — no blank lines, no stray rule — so a
 * ferial weekday composes down to a single quiet kicker.
 */
@Composable
private fun DayIdentity(day: LiturgicalDay, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val kicker = day.metaLine()
    val name = day.celebrationName.trimmedOrNull()
    val quote = day.celebrationQuote.trimmedOrNull()
    val description = day.celebrationDescription.trimmedOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        if (kicker != null) {
            Text(
                text = kicker,
                style = OverlineTextStyle,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("daily.meta")
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (name != null) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground,
                modifier = Modifier
                    .testTag("daily.celebration")
                    .semantics { heading() }
            )
            Spacer(modifier = Modifier.height(HeadingGap))
            // The masthead rule from Home and Settings, reused once: what
            // follows is the day's own words, not another heading.
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(2.dp)
                    .background(colorScheme.primary)
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (quote != null) {
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("daily.quote")
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(ParagraphGap))
            }
        }

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Gospel
// ---------------------------------------------------------------------------

/**
 * The Gospel feature block: `primaryContainer` fill, a 2 dp `primary` rule on
 * the leading edge, the reference at `titleLarge`, and the page's only filled
 * action.
 *
 * The block itself is not clickable — the filled button owns the interaction —
 * so the merged tree has exactly one action here and TalkBack does not
 * announce two overlapping targets.
 */
@Composable
private fun GospelFeature(
    reading: LiturgicalReading,
    onRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = colorScheme.onPrimaryContainer
    val displayLabel = reading.displayLabel.trimmedOrNull()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily.gospel"),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.primaryContainer,
        contentColor = contentColor
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(colorScheme.primary)
            )
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.reading_gospel).uppercase(Locale.ROOT),
                    style = OverlineTextStyle,
                    color = contentColor,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = reading.reference,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor
                )
                if (displayLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onRead,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("daily.read_gospel")
                ) {
                    Text(
                        text = stringResource(R.string.daily_read_gospel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Other readings
// ---------------------------------------------------------------------------

/**
 * The rest of the day as compact rows under one quiet overline. Hairlines
 * separate the rows, so the group scans as a list of references rather than a
 * stack of cards competing with the Gospel.
 */
@Composable
private fun OtherReadings(
    readings: List<LiturgicalReading>,
    onReadingSelected: (LiturgicalReading) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.daily_more_readings),
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        readings.forEachIndexed { index, reading ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ReadingRow(
                reading = reading,
                onClick = { onReadingSelected(reading) }
            )
        }
    }
}

/**
 * One reading link: merged semantics (one node, one click), at least 56 dp
 * tall, with the type as a quiet overline and the reference in the serif
 * reading voice. The label wraps instead of ellipsising; only the chevron is
 * decoration.
 */
@Composable
private fun ReadingRow(
    reading: LiturgicalReading,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(reading.rowTag())
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = colorScheme.background,
        contentColor = colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(reading.type.labelRes()).uppercase(Locale.ROOT),
                    style = OverlineTextStyle,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reading.reference,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Notice and freshness
// ---------------------------------------------------------------------------

/** Maps a resolution notice to its user-visible string (no prose in the VM). */
@Composable
private fun DailyNotice.message(): String = when (this) {
    is DailyNotice.AmbiguousChapter ->
        stringResource(R.string.daily_notice_ambiguous_chapter, reference)
    is DailyNotice.AmbiguousVerse ->
        stringResource(R.string.daily_notice_ambiguous_verse, reference)
    is DailyNotice.NotFound ->
        stringResource(R.string.daily_notice_not_found, reference)
    is DailyNotice.Invalid ->
        stringResource(R.string.daily_notice_invalid, reference)
}

/**
 * Reference-resolution notice, inline and dismissible. It sits where the reader
 * will see it (directly above the Gospel) instead of as a transient snackbar,
 * because the tap that produced it changed nothing else on screen.
 */
@Composable
private fun NoticeBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily.notice"),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.errorContainer,
        contentColor = colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("daily.notice_dismiss")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.reader_dismiss)
                )
            }
        }
    }
}

/**
 * F-18 freshness footer. Present only when the payload came from the cache:
 * "Saved for offline · updated HH:mm" plus the "Update" action that bypasses
 * the cache.
 *
 * The timestamp is formatted by [DailyReadingDates.formatFreshness], which
 * uses the device's zone (the same zone as the date label) and adds the date
 * when the payload was saved on an earlier day.
 */
@Composable
private fun FreshnessFooter(
    cachedAt: Long,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val updatedAt = remember(cachedAt) { DailyReadingDates.formatFreshness(cachedAt) }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.daily_saved_offline, updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .testTag("daily.freshness")
            )
            TextButton(
                onClick = onUpdate,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("daily.refresh")
            ) {
                Text(
                    text = stringResource(R.string.daily_update),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

/** `FIRST_READING` → `first_reading`, matching the row tag contract. */
private fun LiturgicalReading.rowTag(): String = "daily.reading.${type.name.lowercase(Locale.ROOT)}"

private fun String?.trimmedOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

private fun LiturgicalDay.hasIdentity(): Boolean =
    listOfNotNull(season, celebrationName, celebrationType, celebrationQuote, celebrationDescription)
        .any { it.isNotBlank() }

/**
 * "Ordinary Time · OPTIONAL_MEMORIAL" → "ORDINARY TIME · OPTIONAL MEMORIAL".
 *
 * The API mixes readable season names with upper-case enum types; the overline
 * normalises both without translating them (they are proper liturgical names).
 */
private fun LiturgicalDay.metaLine(): String? =
    listOfNotNull(season.trimmedOrNull(), celebrationType.trimmedOrNull())
        .joinToString(" · ")
        .uppercase(Locale.ROOT)
        .replace('_', ' ')
        .takeIf { it.isNotEmpty() }

private fun ReadingType.labelRes(): Int = when (this) {
    ReadingType.FIRST_READING -> R.string.reading_first
    ReadingType.RESPONSORIAL_PSALM -> R.string.reading_psalm
    ReadingType.SECOND_READING -> R.string.reading_second
    ReadingType.GOSPEL -> R.string.reading_gospel
    ReadingType.OTHER -> R.string.reading_other
}
