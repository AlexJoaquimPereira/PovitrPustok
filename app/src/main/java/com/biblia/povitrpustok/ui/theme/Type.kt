package com.biblia.povitrpustok.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.biblia.povitrpustok.R

/**
 * App typography (implementation plan §44; F-07).
 *
 * One serif voice for display, titles and Scripture; one sans voice for UI
 * chrome and metadata. Every style the app uses is defined here so no screen
 * silently falls back to Material's Roboto defaults.
 */
val Typography = Typography(
    // Home masthead (F-11): one size above every screen title so the front
    // door announces the app before anything else.
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

/**
 * Quiet editorial overline (F-11): uppercase, widely tracked, one size below
 * body text. Used for the Home masthead line and card labels; callers pass
 * already-uppercase strings, or apply `uppercase()` for label text.
 */
val OverlineTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.4.sp
)

/** Canonical Scripture style: 19/32 serif, 1.68 line height. */
val ScriptureTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)

/** Poetic Scripture: slightly tighter leading, still generous for Konkani. */
val ScripturePoetryStyle = ScriptureTextStyle.copy(lineHeight = 31.sp)

/** Verse numbers remain quiet metadata, not display text. */
val VerseNumberStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp
)

/** Backwards-compatible alias used by the reader. */
val BibleTextStyle = ScriptureTextStyle

/**
 * Optional Scripture typeface (F2-09): Atkinson Hyperlegible 1.006, bundled
 * offline under the SIL Open Font License 1.1.
 *
 * The face is designed for low vision by the Braille Institute, not as a
 * treatment for dyslexia, so it is an opt-in alternative to the app's serif —
 * it never replaces the display or chrome voices. Both weights are declared
 * from the real files because the reader's HTML can carry `<b>` spans, and a
 * synthesised bold would undo the letterform clarity the face exists for.
 *
 * The family declares only the two faces the app ships, so the `<i>` spans the
 * corpus carries render as Compose's synthesised oblique. That is accepted for
 * now: the upright letterforms carry the clarity this face exists for, and the
 * upstream italic file is not bundled.
 */
val AtkinsonHyperlegibleFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold)
)
