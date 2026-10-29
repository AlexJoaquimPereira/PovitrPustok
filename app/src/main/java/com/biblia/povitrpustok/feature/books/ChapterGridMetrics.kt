package com.biblia.povitrpustok.feature.books

import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints

/**
 * Chapter-grid metrics (F-14) as named, testable values.
 */

/** Minimum touch-target height of a chapter cell. */
val ChapterCellMinHeight = 48.dp

/**
 * Number of chapter columns for the current font scale: four at normal
 * scales, three from 1.5x so long labels ("150 (1)") keep fitting.
 */
fun chapterGridColumns(fontScale: Float): Int =
    if (fontScale >= AppBreakpoints.LargeFontScale) 3 else 4
