package com.biblia.povitrpustok.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.ReadingPreferences

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = LightScrim
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim
)

/**
 * Application theme: Material 3, light and dark variants, reading-focused
 * palette and typography (plan §44).
 *
 * F-02: the matching [ScripturePalette] is provided alongside the Material
 * colour scheme so Scripture text and reader state surfaces follow the same
 * light/dark choice.
 *
 * F-15: [preferences] carries the persisted reading settings. Its [AppTheme]
 * overrides the system light/dark choice, its red-letter toggle reaches the
 * provided [ScripturePalette], and the full value is exposed through
 * [LocalReadingPreferences] so Scripture text styles can apply size/leading.
 * The defaults keep previews and tests working with no arguments.
 */
@Composable
fun BibleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preferences: ReadingPreferences = ReadingPreferences(),
    content: @Composable () -> Unit
) {
    val resolvedDarkTheme = when (preferences.theme) {
        AppTheme.SYSTEM -> darkTheme
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val palette = if (resolvedDarkTheme) ScripturePalette.Dark else ScripturePalette.Light
    CompositionLocalProvider(
        LocalScripturePalette provides palette.copy(redLetterEnabled = preferences.redLetter),
        LocalReadingPreferences provides preferences
    ) {
        MaterialTheme(
            colorScheme = if (resolvedDarkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
