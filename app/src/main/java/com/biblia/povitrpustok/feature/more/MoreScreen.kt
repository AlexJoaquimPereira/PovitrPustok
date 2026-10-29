package com.biblia.povitrpustok.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.feature.about.AboutEntryRow
import com.biblia.povitrpustok.feature.about.AboutRowDivider
import com.biblia.povitrpustok.feature.about.AboutRowGroup
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth

/**
 * More hub (F2-02): the quiet home for destinations that are not the Bible
 * itself.
 *
 * This is deliberately a short contents page, not a second Home: a serif
 * title, the house 44 dp rule, and one hairline-framed group of five rows.
 * Each row opens an existing route; nothing new is introduced here. The
 * adaptive navigation suite already covers Home, Bible and More, so this
 * screen only carries the secondary entries:
 *
 *  - Search
 *  - Bookmarks
 *  - Daily Reading
 *  - Go to reference
 *  - Settings & about
 *
 * The screen is edge-to-edge safe and scrolls, so every row remains reachable
 * at 200% font scale or in landscape. Rows reuse [AboutEntryRow], which is a
 * single merged 64 dp click target with a chevron.
 */
@Composable
fun MoreScreen(
    onOpenSearch: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDailyReading: () -> Unit,
    onOpenReference: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("more.screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = ReadingColumnMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp, bottom = 40.dp)
            ) {
                Text(
                    text = stringResource(R.string.more_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .testTag("more.title")
                        .semantics { heading() }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.height(24.dp))

                AboutRowGroup {
                    AboutEntryRow(
                        title = stringResource(R.string.home_search),
                        subtitle = stringResource(R.string.home_subtitle_search),
                        onClick = onOpenSearch,
                        tag = "more.search"
                    )
                    AboutRowDivider()
                    AboutEntryRow(
                        title = stringResource(R.string.home_bookmarks),
                        subtitle = stringResource(R.string.home_subtitle_bookmarks),
                        onClick = onOpenBookmarks,
                        tag = "more.bookmarks"
                    )
                    AboutRowDivider()
                    AboutEntryRow(
                        title = stringResource(R.string.home_daily_reading),
                        subtitle = stringResource(R.string.home_subtitle_daily),
                        onClick = onOpenDailyReading,
                        tag = "more.daily"
                    )
                    AboutRowDivider()
                    AboutEntryRow(
                        title = stringResource(R.string.home_reference),
                        subtitle = stringResource(R.string.home_subtitle_reference),
                        onClick = onOpenReference,
                        tag = "more.reference"
                    )
                    AboutRowDivider()
                    AboutEntryRow(
                        title = stringResource(R.string.home_about_and_settings),
                        subtitle = stringResource(R.string.more_subtitle_settings),
                        onClick = onOpenSettings,
                        tag = "more.settings"
                    )
                }
            }
        }
    }
}
