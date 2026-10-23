package com.biblia.povitrpustok.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import com.biblia.povitrpustok.domain.model.ReadingPreferences

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

@Composable
private fun scaledScriptureTextStyle(base: TextStyle): TextStyle {
    val preferences = LocalReadingPreferences.current
    return remember(preferences, base) {
        base.copy(
            fontSize = base.fontSize * preferences.scriptureSize.scale,
            lineHeight = base.lineHeight *
                preferences.scriptureSize.scale *
                preferences.lineSpacing.scale
        )
    }
}
