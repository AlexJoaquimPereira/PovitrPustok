package com.biblia.povitrpustok.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.biblia.povitrpustok.core.html.mapScriptureColors
import com.biblia.povitrpustok.core.html.resolveScriptureColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-02 palette contract: Scripture colours are theme-aware, meet WCAG AA on
 * the real theme backgrounds, and every unknown corpus colour degrades to
 * body text instead of becoming unreadable.
 */
class ScripturePaletteTest {

    /** The actual background tokens used by the Material schemes. */
    private val lightBackground = Color(0xFFFAF9FF)
    private val darkBackground = Color(0xFF000000)

    private val palettes = listOf(
        Triple("light", ScripturePalette.Light, lightBackground),
        Triple("dark", ScripturePalette.Dark, darkBackground)
    )

    private fun textRoles(palette: ScripturePalette): List<Pair<String, Color>> = listOf(
        "red" to palette.red,
        "green" to palette.green,
        "blue" to palette.blue,
        "navy" to palette.navy,
        "midBlue" to palette.midBlue,
        "onBackground" to palette.onBackground
    )

    @Test
    fun palettes_use_the_theme_background_tokens() {
        assertEquals(lightBackground, ScripturePalette.Light.background)
        assertEquals(darkBackground, ScripturePalette.Dark.background)
        assertEquals(LightOnBackground, ScripturePalette.Light.onBackground)
        assertEquals(DarkOnBackground, ScripturePalette.Dark.onBackground)
    }

    @Test
    fun mapped_text_roles_meet_wcag_aa_on_theme_backgrounds() {
        palettes.forEach { (themeName, palette, background) ->
            textRoles(palette).forEach { (role, color) ->
                val ratio = contrastRatio(color, background)
                assertTrue(
                    "$themeName/$role contrast $ratio is below ${MIN_TEXT_CONTRAST_RATIO}:1",
                    ratio >= MIN_TEXT_CONTRAST_RATIO
                )
            }
        }
    }

    @Test
    fun exact_corpus_colors_map_to_semantic_roles() {
        palettes.forEach { (themeName, palette, _) ->
            assertEquals(
                "$themeName red letters",
                palette.red,
                resolveScriptureColor(Color(0xFFFF0000), palette)
            )
            assertEquals(
                "$themeName green",
                palette.green,
                resolveScriptureColor(Color(0xFF00B050), palette)
            )
            assertEquals(
                "$themeName blue",
                palette.blue,
                resolveScriptureColor(Color(0xFF0000FF), palette)
            )
            assertEquals(
                "$themeName navy",
                palette.navy,
                resolveScriptureColor(Color(0xFF021A56), palette)
            )
            assertEquals(
                "$themeName mid blue",
                palette.midBlue,
                resolveScriptureColor(Color(0xFF0070C0), palette)
            )
            assertEquals(
                "$themeName literal black becomes body text",
                palette.onBackground,
                resolveScriptureColor(Color(0xFF000000), palette)
            )
        }
    }

    @Test
    fun red_letter_disabled_maps_red_to_body_text() {
        palettes.forEach { (themeName, palette, _) ->
            val quiet = palette.copy(redLetterEnabled = false)
            assertEquals(
                "$themeName red letters off",
                palette.onBackground,
                resolveScriptureColor(Color(0xFFFF0000), quiet)
            )
            // Other semantic colours are not affected by the preference.
            assertEquals(palette.blue, resolveScriptureColor(Color(0xFF0000FF), quiet))
        }
    }

    @Test
    fun mapped_text_roles_stay_readable_on_reader_state_surfaces() {
        palettes.forEach { (themeName, palette, _) ->
            listOf(
                "selection" to palette.selection,
                "target" to palette.target,
                "bookmarked" to palette.bookmarked
            ).forEach { (surfaceName, surface) ->
                textRoles(palette).forEach { (role, color) ->
                    val ratio = contrastRatio(color, surface)
                    assertTrue(
                        "$themeName/$role on $surfaceName is $ratio, below ${MIN_TEXT_CONTRAST_RATIO}:1",
                        ratio >= MIN_TEXT_CONTRAST_RATIO
                    )
                }
            }
        }
    }

    @Test
    fun unknown_low_contrast_color_falls_back_to_body_text() {
        val weakGray = Color(0xFF9AA0A6)
        assertTrue(
            "fixture must actually be low contrast on light paper",
            contrastRatio(weakGray, lightBackground) < MIN_TEXT_CONTRAST_RATIO
        )
        assertEquals(
            ScripturePalette.Light.onBackground,
            resolveScriptureColor(weakGray, ScripturePalette.Light)
        )
        // The same colour is readable on true black, so it is preserved.
        assertEquals(weakGray, resolveScriptureColor(weakGray, ScripturePalette.Dark))
    }

    @Test
    fun mapping_preserves_text_ranges_and_formatting() {
        val source = buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("A ") }
            withStyle(SpanStyle(color = Color(0xFFFF0000))) { append("love") }
        }

        val mapped = mapScriptureColors(source, ScripturePalette.Dark)

        assertEquals("A love", mapped.text)
        assertEquals(source.spanStyles.size, mapped.spanStyles.size)
        val italic = mapped.spanStyles.first { it.item.fontStyle == FontStyle.Italic }
        assertEquals(0, italic.start)
        assertEquals(2, italic.end)
        val colored = mapped.spanStyles.first { it.item.color != Color.Unspecified }
        assertEquals(ScripturePalette.Dark.red, colored.item.color)
        assertEquals(2, colored.start)
        assertEquals(6, colored.end)
    }

    @Test
    fun mapping_preserves_paragraph_alignment() {
        val builder = AnnotatedString.Builder("poem\nline")
        builder.addStyle(ParagraphStyle(textAlign = TextAlign.Center), 0, 9)

        val mapped = mapScriptureColors(builder.toAnnotatedString(), ScripturePalette.Light)

        assertEquals("poem\nline", mapped.text)
        assertEquals(1, mapped.paragraphStyles.size)
        assertEquals(TextAlign.Center, mapped.paragraphStyles.first().item.textAlign)
    }
}
