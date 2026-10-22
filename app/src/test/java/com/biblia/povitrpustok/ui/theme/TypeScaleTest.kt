package com.biblia.povitrpustok.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-07: the type scale is fully defined — one serif voice for display,
 * titles and Scripture, one sans voice for UI chrome and metadata. This
 * pins the sizes so a style cannot silently fall back to Material defaults.
 */
class TypeScaleTest {

    @Test
    fun every_used_style_is_defined_with_its_size() {
        val expected = mapOf(
            "headlineMedium" to (28.sp to 36.sp),
            "titleLarge" to (22.sp to 30.sp),
            "titleMedium" to (17.sp to 24.sp),
            "titleSmall" to (15.sp to 22.sp),
            "bodyLarge" to (17.sp to 28.sp),
            "bodyMedium" to (15.sp to 24.sp),
            "bodySmall" to (13.sp to 20.sp),
            "labelLarge" to (14.sp to 20.sp),
            "labelMedium" to (12.sp to 16.sp),
            "labelSmall" to (11.sp to 16.sp)
        )

        val styles = mapOf(
            "headlineMedium" to Typography.headlineMedium,
            "titleLarge" to Typography.titleLarge,
            "titleMedium" to Typography.titleMedium,
            "titleSmall" to Typography.titleSmall,
            "bodyLarge" to Typography.bodyLarge,
            "bodyMedium" to Typography.bodyMedium,
            "bodySmall" to Typography.bodySmall,
            "labelLarge" to Typography.labelLarge,
            "labelMedium" to Typography.labelMedium,
            "labelSmall" to Typography.labelSmall
        )

        expected.forEach { (name, sizes) ->
            val style = requireNotNull(styles[name]) { "$name must be defined" }
            assertEquals("$name font size", sizes.first, style.fontSize)
            assertEquals("$name line height", sizes.second, style.lineHeight)
        }
    }

    @Test
    fun scripture_uses_a_serif_voice_and_ui_chrome_uses_sans() {
        assertEquals(FontFamily.Serif, Typography.titleMedium.fontFamily)
        assertEquals(FontFamily.Serif, Typography.bodyLarge.fontFamily)
        assertEquals(FontFamily.Default, Typography.bodyMedium.fontFamily)
        assertEquals(FontFamily.Default, Typography.labelMedium.fontFamily)
    }

    @Test
    fun scripture_styles_keep_reading_friendly_leading() {
        assertEquals(19.sp, ScriptureTextStyle.fontSize)
        assertEquals(32.sp, ScriptureTextStyle.lineHeight)
        assertEquals(31.sp, ScripturePoetryStyle.lineHeight)
        assertEquals(FontFamily.Serif, ScriptureTextStyle.fontFamily)
        assertEquals(12.sp, VerseNumberStyle.fontSize)
        assertEquals(FontWeight.Medium, VerseNumberStyle.fontWeight)
    }

    @Test
    fun reading_metrics_pin_the_measure_and_gutter() {
        assertEquals(620, ReadingColumnMaxWidth.value.toInt())
        assertEquals(480, PoetryColumnMaxWidth.value.toInt())
        assertEquals(30, VerseGutterWidth.value.toInt())
    }
}
