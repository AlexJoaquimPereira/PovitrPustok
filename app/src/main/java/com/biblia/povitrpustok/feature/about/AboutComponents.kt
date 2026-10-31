package com.biblia.povitrpustok.feature.about

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth

/**
 * Shared furniture for the disclosure pages under `feature/about`.
 *
 * The pages keep the app's editorial voice rather than looking like a system
 * settings dump: a serif page title, one 44 dp primary rule, hairline-ruled
 * row groups that read as a contents list, and quiet sans metadata. Every
 * page uses the same measure ([ReadingColumnMaxWidth]), the same 24 dp page
 * gutters, and the same top bar as the rest of the app.
 *
 * Accessibility:
 *  - every section label is marked as a TalkBack heading, so the pages can be
 *    navigated by heading;
 *  - rows are single merged click targets at least 48 dp tall;
 *  - external links announce their label and destination, and open through
 *    [LocalUriHandler] so the system browser owns the navigation.
 */

/** Page skeleton: top bar, hairline, and one scrolling editorial column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPageScaffold(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    screenTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(screenTag),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AboutTopBar(
                title = title,
                onBack = onBack,
                backTag = backTag
            )
        }
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
                    .padding(top = 24.dp, bottom = 40.dp),
                content = content
            )
        }
    }
}

/**
 * Top bar in house style: back arrow, serif title, and the hairline that
 * separates the bar from content on the AMOLED surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTopBar(
    title: String,
    onBack: () -> Unit,
    backTag: String
) {
    val fontScale = LocalDensity.current.fontScale
    // A long title steps down one size at large font scales so it stays on one
    // line inside the fixed-height bar instead of clipping.
    val titleStyle = if (fontScale >= AppBreakpoints.LargeFontScale) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleLarge
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(backTag)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Quiet overline that also carries TalkBack's heading role. */
@Composable
fun AboutSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = OverlineTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() }
    )
}

/** The masthead rule from Home, reused as the page's one accent line. */
@Composable
fun AboutRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(44.dp)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.primary)
    )
}

/** Opening statement: the serif reading voice, used once per page. */
@Composable
fun AboutLead(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/** Explanatory prose below a lead. */
@Composable
fun AboutBody(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The smallest voice: notes, caveats and privacy statements. */
@Composable
fun AboutNote(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Full-width hairline used to separate blocks of prose. */
@Composable
fun AboutHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * One hairline frame around a run of rows, so a list of links reads as a
 * single contents block rather than four loose cards.
 */
@Composable
fun AboutRowGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

/** Hairline between two rows inside [AboutRowGroup], inset to the text edge. */
@Composable
fun AboutRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * A row that opens another page in the app.
 *
 * The whole row is one merged click target; [trailing] renders a value (for
 * example the app version) before the chevron.
 */
@Composable
fun AboutEntryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) {},
        color = Color.Transparent,
        contentColor = colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailing()
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

/**
 * A row that opens an external page.
 *
 * Shows the label, optional metadata (version, licence) and the destination
 * URL, so the reader knows where the tap will take them before tapping.
 */
@Composable
fun AboutLinkRow(
    label: String,
    url: String,
    tag: String,
    modifier: Modifier = Modifier,
    meta: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    Surface(
        onClick = { uriHandler.openSafely(url) },
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) {},
        color = Color.Transparent,
        contentColor = colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface
                )
                if (meta != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    // Two lines so a long repository URL stays readable in
                    // full instead of losing its filename to an ellipsis.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "↗",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clearAndSetSemantics {}
            )
        }
    }
}

/** Open a URI without letting a missing browser crash the page. */
private fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}

// ---------------------------------------------------------------------------
// Licence text
// ---------------------------------------------------------------------------

/**
 * Legal text: small monospace, generous leading, selectable.
 *
 * A licence is a legal document whose clause numbering and indentation carry
 * meaning, so it is set in monospace rather than the app's editorial faces and
 * never truncated. Both the app licence and the typeface licence use it.
 */
internal val LicenceTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

/** One complete licence in a hairline panel, selectable so a clause can be copied. */
@Composable
internal fun LicencePanel(
    text: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = LicenceTextStyle,
                color = colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag(tag)
            )
        }
    }
}

/** Read a packaged text asset; `null` when it is missing or unreadable. */
internal fun Context.readAssetText(path: String): String? = runCatching {
    assets.open(path).bufferedReader().use { it.readText() }
}.getOrNull()

/** Universalis Goa calendar reference, linked from the API & data page. */
internal const val UNIVERSALIS_GOA_URL = "https://universalis.com/asia.india.goa/calendar.htm"
