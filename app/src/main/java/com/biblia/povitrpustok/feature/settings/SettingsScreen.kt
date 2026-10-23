package com.biblia.povitrpustok.feature.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.ui.theme.LocalReadingPreferences
import com.biblia.povitrpustok.ui.theme.LocalScripturePalette
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth
import com.biblia.povitrpustok.ui.theme.scriptureTextStyle

/**
 * Settings & About (F-15): the single destination for the reading controls and
 * everything the app has to disclose about the text it shows.
 *
 * The screen is one scrolling editorial column, capped to
 * [ReadingColumnMaxWidth] so it keeps the same measure as Home and the Reader
 * on tablets and in landscape. Two quiet overline sections carry it:
 *
 *  - **READING** — Scripture size (S/M/L/XL), a live preview rendered with
 *    [scriptureTextStyle], line spacing, the red-letter switch, and the theme.
 *    Every control writes through to the repository on tap, so the change
 *    applies to this screen (and the next one) without a Save button.
 *  - **ABOUT** — the corpus attribution and permission note, the runtime
 *    version, the Daily Reading data source with its privacy statement, and a
 *    static open-source licence list. No WebView and no new dependency.
 *
 * Accessibility notes:
 *  - each segmented row is a [selectableGroup] of `Role.RadioButton` segments,
 *    so TalkBack announces the position in the group and the selected state;
 *  - the size segments show the abbreviation only (S/M/L/XL) and set a spoken
 *    [contentDescription] with the full label, so TalkBack says "Small" rather
 *    than a bare letter;
 *  - the red-letter row is one [toggleable] switch target of at least 48 dp,
 *    with the switch itself marked as decoration;
 *  - every control clears 48 dp and the column scrolls, so nothing is clipped
 *    at 2x font scale or in landscape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val versionName = rememberAppVersionName()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsTopBar(onBack = onBack) }
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
                    .padding(top = 20.dp, bottom = 40.dp)
            ) {
                ReadingSection(
                    preferences = preferences,
                    onScriptureSizeChange = { viewModel.setScriptureSize(it) },
                    onLineSpacingChange = { viewModel.setLineSpacing(it) },
                    onRedLetterChange = { viewModel.setRedLetter(it) },
                    onThemeChange = { viewModel.setTheme(it) }
                )

                Spacer(modifier = Modifier.height(SectionGap))

                AboutSection(versionName = versionName)
            }
        }
    }
}

// Vertical rhythm: one gap size per relationship, so the column never relies
// on a per-block magic number.
private val SectionGap = 40.dp
private val ControlGap = 28.dp
private val BlockGap = 24.dp

/**
 * Top bar in house style: back arrow, serif title, and the F-17 hairline that
 * separates the bar from content on the AMOLED surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    // "Settings & About" is the longest bar title in the app. Above 1.5x the
    // title steps down one size so it stays on one line instead of wrapping
    // into a fixed-height bar, which would clip the words.
    val titleStyle = if (fontScale >= 1.5f) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleLarge
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = titleStyle
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings.back")
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

// ---------------------------------------------------------------------------
// Reading
// ---------------------------------------------------------------------------

@Composable
private fun ReadingSection(
    preferences: ReadingPreferences,
    onScriptureSizeChange: (ScriptureSize) -> Unit,
    onLineSpacingChange: (LineSpacing) -> Unit,
    onRedLetterChange: (Boolean) -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.settings_section_reading))
        Spacer(modifier = Modifier.height(20.dp))

        SettingBlock(
            title = stringResource(R.string.settings_scripture_size),
            caption = stringResource(R.string.settings_scripture_size_caption)
        ) {
            SegmentedControl(
                options = scriptureSizeSegments(
                    selected = preferences.scriptureSize,
                    onSelect = onScriptureSizeChange
                )
            )
        }

        Spacer(modifier = Modifier.height(ControlGap))

        ScripturePreview(preferences = preferences)

        Spacer(modifier = Modifier.height(ControlGap))

        SettingBlock(title = stringResource(R.string.settings_line_spacing)) {
            SegmentedControl(
                options = lineSpacingSegments(
                    selected = preferences.lineSpacing,
                    onSelect = onLineSpacingChange
                )
            )
        }

        Spacer(modifier = Modifier.height(ControlGap))

        RedLetterControl(
            enabled = preferences.redLetter,
            onEnabledChange = onRedLetterChange
        )

        Spacer(modifier = Modifier.height(ControlGap))

        SettingBlock(
            title = stringResource(R.string.settings_theme),
            caption = stringResource(R.string.settings_theme_caption)
        ) {
            SegmentedControl(
                options = themeSegments(
                    selected = preferences.theme,
                    onSelect = onThemeChange
                )
            )
        }
    }
}

/**
 * The preview answers every reading control in the same breath: it is rendered
 * with the real [scriptureTextStyle], so size and leading change as the user
 * taps.
 *
 * [LocalReadingPreferences] is provided from the collected state rather than
 * read from the theme, so the preview always matches the controls even when
 * the screen is composed outside the app theme (tests, previews).
 */
@Composable
private fun ScripturePreview(
    preferences: ReadingPreferences,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalScripturePalette.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings.preview"),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = stringResource(R.string.settings_preview),
                style = OverlineTextStyle,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            CompositionLocalProvider(LocalReadingPreferences provides preferences) {
                Text(
                    text = stringResource(R.string.settings_preview_body),
                    style = scriptureTextStyle(),
                    color = colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The sample verse carries no `font color`, so a red-letter line
            // cannot be shown from the corpus text. The legend does the next
            // best thing: the same glyph the reader uses, in the palette's
            // red-letter ink, dimmed when the preference is off.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_preview_red_letter_sample),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (preferences.redLetter) palette.red else colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_preview_red_letter),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One switch row: the whole row is the toggle target (48 dp), so the caption
 * is part of the tap area and TalkBack reads label, caption and state as one
 * node.
 */
@Composable
private fun RedLetterControl(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange
            )
            .testTag("settings.red_letter")
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_red_letter),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_red_letter_caption),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        // The row owns the interaction; the switch is the visual state only.
        Switch(checked = enabled, onCheckedChange = null)
    }
}

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

@Composable
private fun AboutSection(
    versionName: String?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.settings_section_about))
        Spacer(modifier = Modifier.height(14.dp))

        // The masthead rule from Home, reused once: what follows is the
        // colophon, not another control.
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(2.dp)
                .background(colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // The colophon: the one statement in the serif reading voice.
        Text(
            text = stringResource(R.string.corpus_copyright_notice),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.testTag("settings.about.copyright")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_about_permission),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(BlockGap))

        VersionRow(versionName = versionName)

        Spacer(modifier = Modifier.height(BlockGap))

        BlockTitle(text = stringResource(R.string.settings_about_daily_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_about_daily_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_daily_url),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(BlockGap))

        BlockTitle(text = stringResource(R.string.settings_about_licenses_title))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_about_licenses_components),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_licenses_license),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Label/value fact row. The version comes from `PackageManager` because
 * `buildConfig` is not enabled for this module.
 */
@Composable
private fun VersionRow(
    versionName: String?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_about_version_label),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = versionName ?: stringResource(R.string.settings_about_version_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("settings.version")
        )
    }
}

@Composable
private fun rememberAppVersionName(): String? {
    val context = LocalContext.current
    return remember(context) { context.appVersionName() }
}

@Suppress("DEPRECATION")
private fun Context.appVersionName(): String? = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull()

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = OverlineTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun BlockTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/** Label, optional one-line explanation, then the control. */
@Composable
private fun SettingBlock(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface
        )
        if (caption != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

/** One selectable segment: what is drawn, what is spoken, and its state. */
private class SegmentOption(
    val label: String,
    val tag: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val shortLabel: String? = null
)

/**
 * Hairline segmented control: one 48 dp row, the current value filled with the
 * solid accent surface (never an alpha tint, F-06), everything else on the
 * lifted dark surface so it reads on AMOLED.
 */
@Composable
private fun SegmentedControl(
    options: List<SegmentOption>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup()
            .clip(shape)
            .background(colorScheme.surfaceContainerLow)
            .border(1.dp, colorScheme.outlineVariant, shape)
    ) {
        options.forEachIndexed { index, option ->
            val previous = options.getOrNull(index - 1)
            // A separator only where two unfilled segments meet; beside the
            // filled one it would read as a stray line.
            if (previous != null && !previous.selected && !option.selected) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colorScheme.outlineVariant)
                )
            }
            Segment(option = option, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Segment(
    option: SegmentOption,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val foreground = if (option.selected) colorScheme.onPrimary else colorScheme.onSurface
    val spokenLabel = option.label

    Box(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .testTag(option.tag)
            .background(if (option.selected) colorScheme.primary else Color.Transparent)
            .selectable(
                selected = option.selected,
                role = Role.RadioButton,
                onClick = option.onClick
            )
            .semantics {
                // Abbreviated segments still announce their full name.
                if (option.shortLabel != null) contentDescription = spokenLabel
            }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (option.shortLabel != null) {
            // A type specimen: the sizes are shown in the serif face they set.
            Text(
                text = option.shortLabel,
                style = MaterialTheme.typography.titleMedium,
                color = foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics {}
            )
        } else {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Option lists
// ---------------------------------------------------------------------------

@Composable
private fun scriptureSizeSegments(
    selected: ScriptureSize,
    onSelect: (ScriptureSize) -> Unit
): List<SegmentOption> = ScriptureSize.entries.map { size ->
    SegmentOption(
        label = stringResource(scriptureSizeLabelRes(size)),
        shortLabel = stringResource(scriptureSizeShortLabelRes(size)),
        tag = "settings.scripture_size.${size.name}",
        selected = selected == size,
        onClick = { onSelect(size) }
    )
}

@StringRes
private fun scriptureSizeLabelRes(size: ScriptureSize): Int = when (size) {
    ScriptureSize.SMALL -> R.string.settings_scripture_size_small
    ScriptureSize.MEDIUM -> R.string.settings_scripture_size_medium
    ScriptureSize.LARGE -> R.string.settings_scripture_size_large
    ScriptureSize.EXTRA_LARGE -> R.string.settings_scripture_size_extra_large
}

@StringRes
private fun scriptureSizeShortLabelRes(size: ScriptureSize): Int = when (size) {
    ScriptureSize.SMALL -> R.string.settings_scripture_size_short_small
    ScriptureSize.MEDIUM -> R.string.settings_scripture_size_short_medium
    ScriptureSize.LARGE -> R.string.settings_scripture_size_short_large
    ScriptureSize.EXTRA_LARGE -> R.string.settings_scripture_size_short_extra_large
}

@Composable
private fun lineSpacingSegments(
    selected: LineSpacing,
    onSelect: (LineSpacing) -> Unit
): List<SegmentOption> = LineSpacing.entries.map { spacing ->
    SegmentOption(
        label = stringResource(lineSpacingLabelRes(spacing)),
        tag = "settings.line_spacing.${spacing.name}",
        selected = selected == spacing,
        onClick = { onSelect(spacing) }
    )
}

@StringRes
private fun lineSpacingLabelRes(spacing: LineSpacing): Int = when (spacing) {
    LineSpacing.COMPACT -> R.string.settings_line_spacing_compact
    LineSpacing.NORMAL -> R.string.settings_line_spacing_normal
    LineSpacing.RELAXED -> R.string.settings_line_spacing_relaxed
}

@Composable
private fun themeSegments(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit
): List<SegmentOption> = AppTheme.entries.map { theme ->
    SegmentOption(
        label = stringResource(themeLabelRes(theme)),
        tag = "settings.theme.${theme.name}",
        selected = selected == theme,
        onClick = { onSelect(theme) }
    )
}

@StringRes
private fun themeLabelRes(theme: AppTheme): Int = when (theme) {
    AppTheme.SYSTEM -> R.string.settings_theme_system
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
}
