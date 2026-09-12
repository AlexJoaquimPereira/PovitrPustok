package com.biblia.povitrpustok.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme palette (implementation plan §44; corrective phase Issue 7).
 *
 * Restrained minimalist Bible-reader palette: light mode is near-white
 * with a blue primary accent and near-black text; dark mode is true AMOLED
 * black with a blue primary accent and near-white text. No yellow/gold,
 * no gradients, no decorative colors. Composables must reference the
 * MaterialTheme color scheme instead of hard-coding colors.
 */

// Light scheme.
val LightPrimary = Color(0xFF0B57D0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD8E2FF)
val LightOnPrimaryContainer = Color(0xFF001A41)
val LightSecondary = Color(0xFF5A5F72)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDEE1F9)
val LightOnSecondaryContainer = Color(0xFF171C2B)
val LightTertiary = Color(0xFF3F6550)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC0EBD0)
val LightOnTertiaryContainer = Color(0xFF00210F)
val LightBackground = Color(0xFFFAF9FF)
val LightOnBackground = Color(0xFF1A1B20)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1B20)
val LightSurfaceVariant = Color(0xFFE1E2EC)
val LightOnSurfaceVariant = Color(0xFF44464F)
val LightOutline = Color(0xFF74777F)
val LightOutlineVariant = Color(0xFFC4C6D0)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Dark (AMOLED) scheme.
val DarkPrimary = Color(0xFFADC6FF)
val DarkOnPrimary = Color(0xFF002E69)
val DarkPrimaryContainer = Color(0xFF004494)
val DarkOnPrimaryContainer = Color(0xFFD8E2FF)
val DarkSecondary = Color(0xFFC2C5DD)
val DarkOnSecondary = Color(0xFF2B2F42)
val DarkSecondaryContainer = Color(0xFF414559)
val DarkOnSecondaryContainer = Color(0xFFDEE1F9)
val DarkTertiary = Color(0xFFA5CFB4)
val DarkOnTertiary = Color(0xFF0A3721)
val DarkTertiaryContainer = Color(0xFF254E37)
val DarkOnTertiaryContainer = Color(0xFFC0EBD0)
val DarkBackground = Color(0xFF000000)
val DarkOnBackground = Color(0xFFE4E1E9)
val DarkSurface = Color(0xFF000000)
val DarkOnSurface = Color(0xFFE4E1E9)
val DarkSurfaceVariant = Color(0xFF44464F)
val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
val DarkOutline = Color(0xFF8E9099)
val DarkOutlineVariant = Color(0xFF44464F)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
