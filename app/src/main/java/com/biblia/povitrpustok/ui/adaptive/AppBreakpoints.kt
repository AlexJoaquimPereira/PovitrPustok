package com.biblia.povitrpustok.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one set of layout breakpoints (F2-03).
 *
 * Material 3 window size classes are the reference: compact is narrower than
 * 600 dp, medium runs to 839 dp, and expanded starts at 840 dp. The shell uses
 * the compact boundary to choose between the bottom navigation bar and the
 * navigation rail; screens keep their existing font-scale and grid thresholds
 * here so the numbers cannot drift between Home, Books and the Reader.
 *
 * This is intentionally only a classification helper. There is no two-pane
 * reader, fold-posture support, or per-screen adaptive scaffold in this pass.
 */
object AppBreakpoints {

    /** Compact window: below this width the shell shows a bottom navigation bar. */
    val CompactWidth = 600.dp

    /** Expanded window: 840 dp and wider (kept for the shared contract). */
    val ExpandedWidth = 840.dp

    /**
     * Home's destination grid falls back to one column below this width.
     * Measured before the 24 dp page gutters, so a 393 dp phone keeps two
     * columns at 1x.
     */
    val HomeGridMinWidth = 360.dp

    /** Short window (landscape phone) where Home tightens its masthead. */
    val CompactHeight = 480.dp

    /** Font scale at which labels and grids need more room (1.5x). */
    const val LargeFontScale = 1.5f

    /** Font scale at which secondary labels are dropped (2x). */
    const val ExtraLargeFontScale = 2f

    /** True when the window is medium or expanded, so the shell uses a rail. */
    fun usesNavigationRail(windowWidth: Dp): Boolean = windowWidth >= CompactWidth
}
