package com.biblia.povitrpustok.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 14: the Home destination grid collapses to one column when the font
 * scale or the window width would crowd two cells. Pinning the predicate
 * keeps the responsive switch deterministic and independent of the device.
 */
class HomeResponsiveTest {

    @Test
    fun a_phone_at_the_default_font_scale_keeps_two_columns() {
        assertFalse(homeStacksDestinations(maxWidth = 393.dp, fontScale = 1f))
        assertFalse(homeStacksDestinations(maxWidth = 393.dp, fontScale = 1.3f))
    }

    @Test
    fun a_large_font_scale_stacks_the_destinations() {
        assertTrue(homeStacksDestinations(maxWidth = 393.dp, fontScale = 1.5f))
        assertTrue(homeStacksDestinations(maxWidth = 393.dp, fontScale = 2f))
    }

    @Test
    fun a_narrow_window_stacks_the_destinations() {
        assertTrue(homeStacksDestinations(maxWidth = 320.dp, fontScale = 1f))
        assertTrue(homeStacksDestinations(maxWidth = 359.dp, fontScale = 1f))
        assertFalse(homeStacksDestinations(maxWidth = 360.dp, fontScale = 1f))
    }
}
