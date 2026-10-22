package com.biblia.povitrpustok.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Reading-layout metrics (F-08) kept as named constants so the type scale,
 * the reading column, and the chapter grid can be asserted by unit tests
 * instead of only by inspection.
 */

/** Maximum width of the reading column: keeps a comfortable line measure. */
val ReadingColumnMaxWidth = 620.dp

/** Maximum width of a centred poetic verse (tighter than prose). */
val PoetryColumnMaxWidth = 480.dp

/** Width reserved for the verse-number gutter before the text column. */
val VerseGutterWidth = 30.dp
