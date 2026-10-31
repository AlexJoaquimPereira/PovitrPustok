package com.biblia.povitrpustok.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont

/**
 * The persisted reading preferences, available to any composable under
 * [BibleTheme] (F-15).
 *
 * The default keeps every preview/test that does not provide a value on the
 * pre-Settings behaviour.
 */
val LocalReadingPreferences = staticCompositionLocalOf { ReadingPreferences() }

/**
 * Scripture body text with the user's size and leading multipliers applied.
 *
 * `sp` already carries the system font scale, so this is a bounded in-app
 * multiplier on top of it — never a second, independent scale.
 */
@Composable
fun scriptureTextStyle(): TextStyle = scaledScriptureTextStyle(ScriptureTextStyle)

/** Poetic Scripture with the same preference multipliers. */
@Composable
fun scripturePoetryTextStyle(): TextStyle = scaledScriptureTextStyle(ScripturePoetryStyle)

/**
 * Applies the three reading preferences to one Scripture style.
 *
 * F2-09: the optional typeface is swapped here and nowhere else, so every
 * Scripture surface — reader verses, poetic verses and the Settings preview —
 * changes together and no title or chrome text can pick it up by accident.
 */
@Composable
private fun scaledScriptureTextStyle(base: TextStyle): TextStyle {
    val preferences = LocalReadingPreferences.current
    return remember(preferences, base) {
        base.copy(
            fontFamily = when (preferences.scriptureFont) {
                ScriptureFont.SYSTEM -> base.fontFamily
                ScriptureFont.ATKINSON_HYPERLEGIBLE -> AtkinsonHyperlegibleFamily
            },
            fontSize = base.fontSize * preferences.scriptureSize.scale,
            lineHeight = base.lineHeight *
                preferences.scriptureSize.scale *
                preferences.lineSpacing.scale
        )
    }
}
