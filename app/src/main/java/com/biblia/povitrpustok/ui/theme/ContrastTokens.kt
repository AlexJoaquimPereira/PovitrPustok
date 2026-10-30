package com.biblia.povitrpustok.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One ink paired with the surface it is actually drawn on (F2-06).
 *
 * A bare colour cannot be verified: the same red is readable on white paper
 * and unreadable on true black. Carrying the surface in the token means the
 * contrast ratio is a property of the token itself, so the reader, the
 * settings preview and the contrast fixture all reason about the same pair
 * instead of re-deciding what "the background" is.
 */
data class ContrastPair(
    val text: Color,
    val surface: Color
) {
    /** WCAG 2.1 contrast ratio of [text] on [surface], from 1.0 to 21.0. */
    val ratio: Double
        get() = contrastRatio(text, surface)

    /** True when the pair clears [MIN_TEXT_CONTRAST_RATIO] (WCAG AA, normal text). */
    val meetsAaNormalText: Boolean
        get() = ratio >= MIN_TEXT_CONTRAST_RATIO
}

/**
 * The non-Material reading inks for one theme (F2-06).
 *
 * [redLetter] is the pair the corpus' literal red resolves to; [verseNumber]
 * and [metaText] are the quiet inks the reader draws verse numbers, chapter
 * overlines and passage ranges in. Body text stays on the Material
 * `onBackground`/`background` pair, which [ScripturePalette] already mirrors.
 *
 * These tokens are never the only signal: the red-letter preference falls back
 * to body ink, and verse states keep their spoken `stateDescription` labels
 * and gutter markers, so nothing here is announced by colour alone.
 */
data class ThemeContrastTokens(
    val redLetter: ContrastPair,
    val verseNumber: ContrastPair,
    val metaText: ContrastPair
) {
    companion object {
        /** Light theme: near-white paper, deep saturated ink. */
        val Light = ThemeContrastTokens(
            redLetter = ContrastPair(text = LightRedLetterInk, surface = LightBackground),
            verseNumber = ContrastPair(text = LightVerseNumberInk, surface = LightBackground),
            metaText = ContrastPair(text = LightMetaInk, surface = LightBackground)
        )

        /** Dark theme: true AMOLED black, lifted desaturated ink. */
        val Dark = ThemeContrastTokens(
            redLetter = ContrastPair(text = DarkRedLetterInk, surface = DarkBackground),
            verseNumber = ContrastPair(text = DarkVerseNumberInk, surface = DarkBackground),
            metaText = ContrastPair(text = DarkMetaInk, surface = DarkBackground)
        )

        /** The tokens matching a resolved light/dark choice. */
        fun of(darkTheme: Boolean): ThemeContrastTokens = if (darkTheme) Dark else Light
    }
}
