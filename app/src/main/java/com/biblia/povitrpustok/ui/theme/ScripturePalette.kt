package com.biblia.povitrpustok.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Theme-aware semantic colours for Scripture text and reader state (F-02, F-06).
 *
 * The corpus carries its own `font color` values (red letters, quoted
 * passages, cross-references). Those literal colours are designed for white
 * paper and become unreadable on a black background, so they are never drawn
 * verbatim: [com.biblia.povitrpustok.core.html.BibleHtmlText] translates each
 * corpus colour into one of the semantic roles below.
 *
 * [background] and [onBackground] mirror the Material theme background pair
 * so the translation can reason about contrast without reaching for a
 * composable. [selection], [target] and [bookmarked] are solid reader state
 * surfaces (no alpha tints), and [gutterAccent] draws the reader's state
 * markers. [redLetterEnabled] is groundwork for a future reading preference;
 * it is always on until Settings exposes it.
 *
 * F2-06: [redLetter], [verseNumber] and [metaText] are [ContrastPair]s from
 * [ThemeContrastTokens], so each quiet ink travels with the surface it is
 * drawn on and its ratio is testable. [red] is derived from [redLetter] so
 * there is exactly one red-letter value per theme.
 */
data class ScripturePalette(
    val green: Color,
    val blue: Color,
    val navy: Color,
    val midBlue: Color,
    val background: Color,
    val onBackground: Color,
    val selection: Color,
    val target: Color,
    val bookmarked: Color,
    val gutterAccent: Color,
    val redLetter: ContrastPair,
    val verseNumber: ContrastPair,
    val metaText: ContrastPair,
    val redLetterEnabled: Boolean = true
) {
    /** Red-letter ink; the pair also carries the surface it sits on (F2-06). */
    val red: Color
        get() = redLetter.text

    companion object {
        /** Light reading surface: near-white paper, deep saturated ink. */
        val Light = ScripturePalette(
            green = Color(0xFF0E6B36),
            blue = Color(0xFF0B57D0),
            navy = Color(0xFF22315C),
            midBlue = Color(0xFF005A8C),
            background = LightBackground,
            onBackground = LightOnBackground,
            selection = Color(0xFFE7EEFB),
            target = Color(0xFFDCE9FF),
            bookmarked = Color(0xFFFFF4D6),
            gutterAccent = LightPrimary,
            redLetter = ThemeContrastTokens.Light.redLetter,
            verseNumber = ThemeContrastTokens.Light.verseNumber,
            metaText = ThemeContrastTokens.Light.metaText
        )

        /** Dark AMOLED surface: true black with lifted, desaturated ink. */
        val Dark = ScripturePalette(
            green = Color(0xFF7EDCA0),
            blue = Color(0xFFADC6FF),
            navy = Color(0xFFA9B8E8),
            midBlue = Color(0xFF9CCBF0),
            background = DarkBackground,
            onBackground = DarkOnBackground,
            selection = Color(0xFF1B2738),
            target = Color(0xFF22324C),
            bookmarked = Color(0xFF3A2F14),
            gutterAccent = DarkPrimary,
            redLetter = ThemeContrastTokens.Dark.redLetter,
            verseNumber = ThemeContrastTokens.Dark.verseNumber,
            metaText = ThemeContrastTokens.Dark.metaText
        )
    }
}

/**
 * The active Scripture palette. [BibleTheme] provides the light or dark
 * instance; readers never construct one themselves.
 */
val LocalScripturePalette = staticCompositionLocalOf { ScripturePalette.Light }

/** WCAG 2.1 AA minimum contrast for normal-size text. */
internal const val MIN_TEXT_CONTRAST_RATIO = 4.5

/**
 * Relative luminance of an sRGB colour (WCAG 2.1, §Relative luminance).
 * Alpha is ignored: palette colours are opaque.
 */
internal fun relativeLuminance(color: Color): Double {
    fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.03928) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }

    return 0.2126 * linear(color.red) +
        0.7152 * linear(color.green) +
        0.0722 * linear(color.blue)
}

/** WCAG 2.1 contrast ratio, from 1.0 (identical) to 21.0 (black on white). */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val first = relativeLuminance(foreground)
    val second = relativeLuminance(background)
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}
