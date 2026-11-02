package com.biblia.povitrpustok.feature.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.core.locale.AppLocale
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.feature.about.AboutEntryRow
import com.biblia.povitrpustok.feature.about.AboutRowGroup
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints
import com.biblia.povitrpustok.ui.rememberAppVersionName
import com.biblia.povitrpustok.ui.theme.LocalReadingPreferences
import com.biblia.povitrpustok.ui.theme.LocalScripturePalette
import com.biblia.povitrpustok.ui.theme.OverlineTextStyle
import com.biblia.povitrpustok.ui.theme.ReadingColumnMaxWidth
import com.biblia.povitrpustok.ui.theme.scriptureTextStyle

/**
 * Settings (F-15): the reading controls, the app language, plus the single
 * entry point to the About pages.
 *
 * The screen is one scrolling editorial column, capped to
 * [ReadingColumnMaxWidth] so it keeps the same measure as Home and the Reader
 * on tablets and in landscape. Four quiet overline sections carry it:
 *
 *  - **READING** — Scripture size (S/M/L/XL), the optional Atkinson Hyperlegible
 *    typeface, a live preview rendered with [scriptureTextStyle], line spacing,
 *    the red-letter switch, and the theme. Every control writes through to the
 *    repository on tap, so the change applies to this screen (and the next one)
 *    without a Save button.
 *  - **BIBLE TRANSLATION** — which edition the reader opens: the shipped
 *    Konkani (Romi) text, or one of the two bundled English public-domain
 *    editions. It is a reading preference, so it sits with the reading controls
 *    and not with the app language below; the source and licence of each
 *    edition live on their About pages, never here.
 *  - **APP LANGUAGE** — English or Konkani (Romi), applied immediately through
 *    the AppCompat delegate (F2-14). This is the only control whose value does
 *    not live in the reading preferences: the delegate owns it, and the
 *    manifest's locale holder persists it.
 *  - **ABOUT** — one row that opens the About landing page, where the source
 *    edition, the Daily Reading data source, the software licences and the
 *    credits each have their own page. The disclosures deliberately no longer
 *    live inline here: Settings stays a settings screen.
 *
 * Accessibility notes:
 *  - each segmented row is a [selectableGroup] of `Role.RadioButton` segments,
 *    so TalkBack announces the position in the group and the selected state;
 *  - the size segments show the abbreviation only (S/M/L/XL) and set a spoken
 *    [contentDescription] with the full label, so TalkBack says "Small" rather
 *    than a bare letter;
 *  - the language segments keep short visible labels and speak "Konkani (Romi)",
 *    so the script is never ambiguous;
 *  - the red-letter row is one [toggleable] switch target of at least 48 dp,
 *    with the switch itself marked as decoration;
 *  - the typeface row is the same shape, so its caption is part of the tap area
 *    and its spoken label carries the low-vision purpose and the "not a
 *    dyslexia treatment" caveat;
 *  - every control clears 48 dp and the column scrolls, so nothing is clipped
 *    at 2x font scale or in landscape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val appLocale by viewModel.appLocale.collectAsStateWithLifecycle()
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
                    onThemeChange = { viewModel.setTheme(it) },
                    onScriptureFontChange = { viewModel.setScriptureFont(it) }
                )

                Spacer(modifier = Modifier.height(SectionGap))

                BibleTranslationSection(
                    selectedEditionId = preferences.readingEditionId,
                    onSelect = { viewModel.setReadingEdition(it) }
                )

                Spacer(modifier = Modifier.height(SectionGap))

                LanguageSection(
                    selectedTag = appLocale,
                    onSelect = { viewModel.setAppLocale(it) }
                )

                Spacer(modifier = Modifier.height(SectionGap))

                AboutSection(
                    versionName = versionName,
                    onOpenAbout = onOpenAbout
                )
            }
        }
    }
}

// Vertical rhythm: one gap size per relationship, so the column never relies
// on a per-block magic number.
private val SectionGap = 40.dp
private val ControlGap = 28.dp

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
    val titleStyle = if (fontScale >= AppBreakpoints.LargeFontScale) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleLarge
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = titleStyle,
                    // House rule for every bar: one line, ellipsized, never
                    // wrapping inside the fixed-height app bar.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    onScriptureFontChange: (ScriptureFont) -> Unit,
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

        ScriptureTypefaceControl(
            atkinson = preferences.scriptureFont == ScriptureFont.ATKINSON_HYPERLEGIBLE,
            onAtkinsonChange = { enabled ->
                onScriptureFontChange(
                    if (enabled) {
                        ScriptureFont.ATKINSON_HYPERLEGIBLE
                    } else {
                        ScriptureFont.SYSTEM
                    }
                )
            }
        )

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
 * with the real [scriptureTextStyle], so size, leading and the optional
 * typeface change as the user taps.
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
 * One switch row for the optional Scripture typeface (F2-09).
 *
 * A two-segment control was rejected: "Atkinson Hyperlegible" is 20 characters
 * and cannot hold one line inside a 136 dp segment at 320 dp / 200% font scale,
 * so it would wrap into a very tall segment or clip. The switch row matches
 * [RedLetterControl], keeps the caption inside the tap area, and lets the name
 * wrap naturally in the weighted column.
 *
 * The row is the whole target (48 dp minimum); the switch itself is decoration,
 * and because the row merges its descendants, TalkBack reads the typeface name,
 * the low-vision purpose, the "not a dyslexia treatment" caveat and the state
 * as one node.
 */
@Composable
private fun ScriptureTypefaceControl(
    atkinson: Boolean,
    onAtkinsonChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = atkinson,
                role = Role.Switch,
                onValueChange = onAtkinsonChange
            )
            .testTag("settings.scripture_font")
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_scripture_font),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_scripture_font_caption),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        // The row owns the interaction; the switch is the visual state only.
        Switch(checked = atkinson, onCheckedChange = null)
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
// Bible translation
// ---------------------------------------------------------------------------

/**
 * Which Bible text the reader opens.
 *
 * A reading preference, not an app-language control: the choice changes the
 * Scripture the reader shows, while the chrome stays in the language picked
 * below. That is why it sits in the reading hierarchy and why its own overline
 * names the translation rather than the language.
 *
 * The control is a vertical radio list rather than a segmented row. The three
 * editions are named, not enumerated, and "Douay-Rheims 1899" cannot hold one
 * legible line inside a ~90 dp segment at 320 dp / 200% font scale.
 * The list gives every edition its full name at any font scale, keeps the whole
 * row as the 48 dp target, and puts the mark on the right like the switch rows
 * above it. TalkBack announces the full name, the role and "selected", so the
 * abbreviation a reader might not know is never the only label.
 *
 * The selection is read from the persisted `readingEditionId` on every
 * composition; the control never memorises a local choice, so the filled row is
 * always the edition the reader will open.
 *
 * Public because the large-font UI test lays this section out on its own at
 * 320 dp / 200%, where a single-row control would have clipped.
 */
@Composable
fun BibleTranslationSection(
    selectedEditionId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.settings_section_edition))
        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .clip(shape)
                .background(colorScheme.surfaceContainerLow)
                .border(1.dp, colorScheme.outlineVariant, shape)
        ) {
            editionOptions(selectedEditionId).forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = colorScheme.outlineVariant
                    )
                }
                EditionRow(option = option, onSelect = onSelect)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_edition_caption),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}

private class EditionOption(
    val id: String,
    val label: String,
    val tag: String,
    val selected: Boolean
)

/**
 * The three editions in reading order: the shipped Konkani text first, then the
 * two English translations.
 */
@Composable
private fun editionOptions(selectedEditionId: String): List<EditionOption> {
    // Tolerate a stale stored id the way the repository does: the Konkani
    // edition is what the reader would actually get.
    val selectedId = BibleEditions.byId(selectedEditionId)?.id ?: BibleEditions.KONKANI_ID

    return listOf(
        EditionOption(
            id = BibleEditions.KONKANI_ID,
            label = stringResource(R.string.settings_edition_konkani),
            tag = "settings.edition.KONKANI",
            selected = selectedId == BibleEditions.KONKANI_ID
        ),
        EditionOption(
            id = BibleEditions.WEBC_ID,
            label = stringResource(R.string.settings_edition_webc),
            tag = "settings.edition.WEBC",
            selected = selectedId == BibleEditions.WEBC_ID
        ),
        EditionOption(
            id = BibleEditions.DRA_ID,
            label = stringResource(R.string.settings_edition_dra),
            tag = "settings.edition.DRA",
            selected = selectedId == BibleEditions.DRA_ID
        )
    )
}

/** One edition: the whole row selects it, and the row is one TalkBack node. */
@Composable
private fun EditionRow(
    option: EditionOption,
    onSelect: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(option.tag)
            .selectable(
                selected = option.selected,
                role = Role.RadioButton,
                onClick = { onSelect(option.id) }
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            // The accent ink, not a tinted background: the selected edition is
            // one solid signal, and the row still reads on AMOLED.
            color = if (option.selected) colorScheme.primary else colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        EditionMark(selected = option.selected)
    }
}

/** Filled ring for the chosen edition, hollow ring for the others. */
@Composable
private fun EditionMark(selected: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val ring = if (selected) colorScheme.primary else colorScheme.outline

    Box(
        modifier = Modifier
            .size(20.dp)
            // The row's selectable semantics already carry the state.
            .clearAndSetSemantics {}
            .clip(CircleShape)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Language
// ---------------------------------------------------------------------------

/**
 * F2-14: the app's own language choice, applied without leaving Settings.
 *
 * One segmented control under a single overline, with the scope note below it:
 * the two visible labels are short enough to hold one line inside a ~136 dp
 * segment at 320 dp / 200% font scale, and the Konkani segment speaks
 * "Konkani (Romi)" so TalkBack never has to guess which script is meant.
 *
 * The control is the same [selectableGroup] of `Role.RadioButton` segments the
 * reading controls use, so position, role and selected state are announced and
 * each option stays a 48 dp target.
 *
 * The caption sits below the control rather than above it because there is no
 * block title to introduce: the section label already names the setting, and
 * the note explains what the choice does not translate yet.
 */
@Composable
private fun LanguageSection(
    selectedTag: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.settings_section_language))
        Spacer(modifier = Modifier.height(20.dp))
        SegmentedControl(options = languageSegments(selectedTag, onSelect))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_language_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun languageSegments(
    selectedTag: String,
    onSelect: (String) -> Unit
): List<SegmentOption> = listOf(
    SegmentOption(
        label = stringResource(R.string.settings_language_english),
        tag = "settings.language.ENGLISH",
        selected = selectedTag == AppLocale.ENGLISH,
        onClick = { onSelect(AppLocale.ENGLISH) }
    ),
    SegmentOption(
        label = stringResource(R.string.settings_language_konkani),
        tag = "settings.language.KONKANI",
        selected = selectedTag == AppLocale.ROMI_KONKANI,
        onClick = { onSelect(AppLocale.ROMI_KONKANI) },
        spokenLabel = stringResource(R.string.settings_language_konkani_spoken)
    )
)

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

@Composable
private fun AboutSection(
    versionName: String?,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.settings_section_about))
        Spacer(modifier = Modifier.height(14.dp))

        // One row, not a colophon: the disclosures moved to their own pages,
        // so Settings states only that they exist and where to find them.
        AboutRowGroup {
            AboutEntryRow(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                onClick = onOpenAbout,
                tag = "settings.about",
                trailing = {
                    Text(
                        text = versionName
                            ?: stringResource(R.string.about_version_unknown),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Its own semantics boundary, so the version keeps a
                        // queryable node inside the row's merged click target.
                        modifier = Modifier
                            .testTag("settings.version")
                            .semantics(mergeDescendants = true) {}
                    )
                }
            )
        }
    }
}

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
        modifier = modifier.semantics { heading() }
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

/**
 * One selectable segment: what is drawn, what is spoken, and its state.
 *
 * [shortLabel] is a specimen drawn in place of the label (the Scripture sizes
 * show themselves in the serif they set). [spokenLabel] is the full name when
 * the visible label is shortened to fit a narrow segment; either one replaces
 * the default spoken label.
 */
private class SegmentOption(
    val label: String,
    val tag: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val shortLabel: String? = null,
    val spokenLabel: String? = null
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
    val spokenLabel = option.spokenLabel ?: option.label

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
                if (option.shortLabel != null || option.spokenLabel != null) {
                    contentDescription = spokenLabel
                }
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
