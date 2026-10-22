package com.biblia.povitrpustok.feature.books

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-14: the chapter grid keeps 48 dp touch targets and drops to three
 * columns once the system font scale makes "150 (1)" too wide.
 */
class ChapterGridMetricsTest {

    @Test
    fun cells_meet_the_48dp_touch_target() {
        assertEquals(48, ChapterCellMinHeight.value.toInt())
    }

    @Test
    fun columns_follow_the_font_scale() {
        assertEquals(4, chapterGridColumns(1.0f))
        assertEquals(4, chapterGridColumns(1.3f))
        assertEquals(3, chapterGridColumns(1.5f))
        assertEquals(3, chapterGridColumns(2.0f))
    }
}
