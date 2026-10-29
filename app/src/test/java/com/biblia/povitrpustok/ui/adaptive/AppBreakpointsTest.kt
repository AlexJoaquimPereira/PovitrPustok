package com.biblia.povitrpustok.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2-03: the shared breakpoints are the only place the shell and the screens
 * read their width/font thresholds from. The boundaries are deliberate:
 * 600 dp is Material's compact/medium line, and Home's existing 360 dp/1.5x
 * behaviour must not move.
 */
class AppBreakpointsTest {

    @Test
    fun compact_widths_keep_the_bottom_navigation_bar() {
        assertFalse(AppBreakpoints.usesNavigationRail(0.dp))
        assertFalse(AppBreakpoints.usesNavigationRail(320.dp))
        assertFalse(AppBreakpoints.usesNavigationRail(599.dp))
    }

    @Test
    fun medium_and_expanded_widths_use_the_navigation_rail() {
        assertTrue(AppBreakpoints.usesNavigationRail(600.dp))
        assertTrue(AppBreakpoints.usesNavigationRail(839.dp))
        assertTrue(AppBreakpoints.usesNavigationRail(840.dp))
        assertTrue(AppBreakpoints.usesNavigationRail(1200.dp))
    }

    @Test
    fun screen_thresholds_stay_at_their_existing_values() {
        assertEquals(360.dp, AppBreakpoints.HomeGridMinWidth)
        assertEquals(480.dp, AppBreakpoints.CompactHeight)
        assertEquals(1.5f, AppBreakpoints.LargeFontScale)
        assertEquals(2f, AppBreakpoints.ExtraLargeFontScale)
        assertEquals(600.dp, AppBreakpoints.CompactWidth)
        assertEquals(840.dp, AppBreakpoints.ExpandedWidth)
    }
}
