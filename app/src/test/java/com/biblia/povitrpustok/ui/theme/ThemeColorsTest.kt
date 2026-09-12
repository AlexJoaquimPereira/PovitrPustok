package com.biblia.povitrpustok.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Light/dark theme color contract (corrective phase Issue 7): blue accent,
 * near-white / true-black backgrounds, near-black / near-white text.
 */
class ThemeColorsTest {

    @Test
    fun dark_background_is_true_amoled_black() {
        assertEquals(Color(0xFF000000), DarkBackground)
        assertEquals(Color(0xFF000000), DarkSurface)
    }

    @Test
    fun light_background_is_near_white() {
        assertTrue("light background must be near-white", LightBackground.luminance() > 0.95f)
        assertTrue("light surface must be white", LightSurface.luminance() > 0.95f)
    }

    @Test
    fun primary_accent_is_blue_not_yellow() {
        assertEquals(Color(0xFF0B57D0), LightPrimary)
        assertEquals(Color(0xFFADC6FF), DarkPrimary)
    }

    @Test
    fun text_contrast_is_dark_on_light_light_on_dark() {
        assertTrue("light text must be near-black", LightOnBackground.luminance() < 0.1f)
        assertTrue("dark text must be near-white", DarkOnBackground.luminance() > 0.7f)
    }

    @Test
    fun secondary_text_is_neutral_gray() {
        // neutral gray, not a warm tone
        val gray = LightOnSurfaceVariant
        assertTrue(gray.luminance() in 0.05f..0.35f)
    }
}
