package com.biblia.povitrpustok.feature.dailyreading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.LiturgicalDay
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.domain.model.ReadingType
import java.time.format.DateTimeFormatter

/**
 * Daily Reading screen (Phase 10, plan §13, §14).
 *
 * Displays the date, celebration, season, and the day's readings
 * (reference strings only — the local Bible Reader provides the text).
 * Selecting a reading resolves it through the existing resolver and opens
 * the one Bible Reader via [onOpenReading].
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
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val notice by viewModel.navigationNotice.collectAsStateWithLifecycle()

    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { destination ->
            onOpenReading(destination)
            viewModel.consumeNavigation()
        }
    }

    Scaffold(
        topBar = {
            Column {
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
                // F-17: hairline separation between the bar and the readings.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = state) {
                is DailyReadingUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is DailyReadingUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.load() }) {
                        Text(text = stringResource(R.string.reader_retry))
                    }
                }

                is DailyReadingUiState.Success -> DailyReadingContent(
                    day = s.day,
                    notice = notice,
                    onReadingSelected = viewModel::openReading,
                    onNoticeDismissed = viewModel::consumeNotice
                )
            }
        }
    }
}

@Composable
private fun DailyReadingContent(
    day: LiturgicalDay,
    notice: String?,
    onReadingSelected: (LiturgicalReading) -> Unit,
    onNoticeDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateLabel = rememberDateFormatter().format(day.date)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "date") {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        day.celebrationName?.let { name ->
            item(key = "celebration") {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        day.season?.let { season ->
            item(key = "season") {
                Text(
                    text = season,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        day.celebrationType?.let { type ->
            item(key = "celebrationType") {
                Text(
                    text = type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (notice != null) {
            item(key = "notice") {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
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
        items(day.readings, key = { "${it.type}-${it.reference}" }) { reading ->
            ReadingRow(
                reading = reading,
                onClick = { onReadingSelected(reading) }
            )
        }
    }
}

@Composable
private fun ReadingRow(reading: LiturgicalReading, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = stringResource(reading.type.labelRes()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = reading.reference,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun ReadingType.labelRes(): Int = when (this) {
    ReadingType.FIRST_READING -> R.string.reading_first
    ReadingType.RESPONSORIAL_PSALM -> R.string.reading_psalm
    ReadingType.SECOND_READING -> R.string.reading_second
    ReadingType.GOSPEL -> R.string.reading_gospel
    ReadingType.OTHER -> R.string.reading_other
}

@Composable
private fun rememberDateFormatter(): DateTimeFormatter =
    androidx.compose.runtime.remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy") }
