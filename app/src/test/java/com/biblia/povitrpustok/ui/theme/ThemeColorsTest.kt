package com.biblia.povitrpustok.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F2-06 contrast fixture.
 *
 * The reader draws three inks that are not Material text roles: the
 * red-letter ink, verse numbers and meta text. Each is a [ContrastPair], so
 * the surface travels with the ink and the ratio is computed from the real
 * token values here — a retune that drops below WCAG AA fails this test
 * instead of shipping.
 *
 * The fixture also pins the pair to the surface the app actually paints on
 * (reader paper, reader state surfaces, the Settings preview card) and checks
 * that no reader state is announced by colour alone.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeColorsTest {

    private data class ThemeCase(
        val name: String,
        val tokens: ThemeContrastTokens,
        val palette: ScripturePalette,
        val paper: Color,
        val previewSurface: Color,
        val stateSurfaces: List<Pair<String, Color>>
    )

    private val themes = listOf(
        ThemeCase(
            name = "light",
            tokens = ThemeContrastTokens.Light,
            palette = ScripturePalette.Light,
            paper = LightBackground,
            previewSurface = LightSurfaceContainerLow,
            stateSurfaces = listOf(
                "selection" to ScripturePalette.Light.selection,
                "target" to ScripturePalette.Light.target,
                "bookmarked" to ScripturePalette.Light.bookmarked
            )
        ),
        ThemeCase(
            name = "dark",
            tokens = ThemeContrastTokens.Dark,
            palette = ScripturePalette.Dark,
            paper = DarkBackground,
            previewSurface = DarkSurfaceContainerLow,
            stateSurfaces = listOf(
                "selection" to ScripturePalette.Dark.selection,
                "target" to ScripturePalette.Dark.target,
                "bookmarked" to ScripturePalette.Dark.bookmarked
            )
        )
    )

    private fun assertAa(theme: String, role: String, pair: ContrastPair) {
        assertTrue(
            "$theme/$role on its own surface is ${"%.2f".format(pair.ratio)}:1, " +
                "below $MIN_TEXT_CONTRAST_RATIO:1",
            pair.meetsAaNormalText
        )
    }

    @Test
    fun red_letter_pair_meets_aa_in_both_themes() {
        themes.forEach { theme ->
            val pair = theme.tokens.redLetter
            assertAa(theme.name, "red letter", pair)
            // The pair must be self-consistent: the surface is the paper, not
            // a stale copy of the previous theme's background.
            assertEquals("${theme.name} red-letter surface", theme.paper, pair.surface)
            assertNotEquals("${theme.name} red-letter ink must not be the paper", pair.surface, pair.text)
        }
    }

    @Test
    fun red_letter_is_a_token_per_theme_not_one_shared_red() {
        assertNotEquals(
            "light and dark red-letter ink must differ: a single literal red " +
                "cannot clear AA on both near-white paper and true black",
            ThemeContrastTokens.Light.redLetter.text,
            ThemeContrastTokens.Dark.redLetter.text
        )
        assertEquals(LightRedLetterInk, ThemeContrastTokens.Light.redLetter.text)
        assertEquals(DarkRedLetterInk, ThemeContrastTokens.Dark.redLetter.text)
    }

    @Test
    fun verse_number_and_meta_pairs_meet_aa_in_both_themes() {
        themes.forEach { theme ->
            listOf(
                "verse number" to theme.tokens.verseNumber,
                "meta text" to theme.tokens.metaText
            ).forEach { (role, pair) ->
                assertAa(theme.name, role, pair)
                assertEquals("${theme.name} $role surface", theme.paper, pair.surface)
            }
        }
    }

    @Test
    fun quiet_inks_stay_readable_on_reader_state_surfaces() {
        themes.forEach { theme ->
            val inks = listOf(
                "red letter" to theme.tokens.redLetter,
                "verse number" to theme.tokens.verseNumber,
                "meta text" to theme.tokens.metaText
            )
            inks.forEach { (role, pair) ->
                theme.stateSurfaces.forEach { (surfaceName, surface) ->
                    val ratio = contrastRatio(pair.text, surface)
                    assertTrue(
                        "${theme.name}/$role on $surfaceName is ${"%.2f".format(ratio)}:1, " +
                            "below $MIN_TEXT_CONTRAST_RATIO:1",
                        ratio >= MIN_TEXT_CONTRAST_RATIO
                    )
                }
            }
        }
    }

    @Test
    fun red_letter_stays_readable_on_the_settings_preview_surface() {
        // SettingsScreen draws the red-letter sample on surfaceContainerLow,
        // not on the reader paper; verify the ink there too.
        themes.forEach { theme ->
            val ratio = contrastRatio(theme.tokens.redLetter.text, theme.previewSurface)
            assertTrue(
                "${theme.name} red letter on the Settings preview card is " +
                    "${"%.2f".format(ratio)}:1, below $MIN_TEXT_CONTRAST_RATIO:1",
                ratio >= MIN_TEXT_CONTRAST_RATIO
            )
        }
    }

    @Test
    fun tokens_are_the_ones_the_palette_and_reader_use() {
        themes.forEach { theme ->
            assertEquals(
                "${theme.name} palette red-letter pair",
                theme.tokens.redLetter,
                theme.palette.redLetter
            )
            assertEquals("${theme.name} palette red", theme.tokens.redLetter.text, theme.palette.red)
            assertEquals(
                "${theme.name} palette verse number pair",
                theme.tokens.verseNumber,
                theme.palette.verseNumber
            )
            assertEquals(
                "${theme.name} palette meta pair",
                theme.tokens.metaText,
                theme.palette.metaText
            )
            // The palette background and the token surface are the same value,
            // so the verified surface is the one the reader actually paints.
            assertEquals("${theme.name} palette paper", theme.paper, theme.palette.background)
        }
        assertEquals(ThemeContrastTokens.Light, ThemeContrastTokens.of(darkTheme = false))
        assertEquals(ThemeContrastTokens.Dark, ThemeContrastTokens.of(darkTheme = true))
    }

    @Test
    fun body_text_pair_meets_aa_in_both_themes() {
        themes.forEach { theme ->
            val ratio = contrastRatio(theme.palette.onBackground, theme.palette.background)
            assertTrue(
                "${theme.name} body text is ${"%.2f".format(ratio)}:1, " +
                    "below $MIN_TEXT_CONTRAST_RATIO:1",
                ratio >= MIN_TEXT_CONTRAST_RATIO
            )
        }
    }

    @Test
    fun verse_states_are_announced_without_relying_on_colour() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = listOf(
            R.string.reader_verse_state_target,
            R.string.reader_verse_state_selected,
            R.string.reader_verse_state_bookmarked
        ).map(context::getString)

        assertTrue("every verse state needs a spoken label", labels.none { it.isBlank() })
        assertEquals(
            "verse states must stay distinguishable as text, not only as colour",
            labels.size,
            labels.toSet().size
        )
        // The red-letter legend is a text label beside the ink sample, so the
        // convention is readable without seeing the colour.
        assertTrue(context.getString(R.string.settings_preview_red_letter).isNotBlank())
    }

    // --- Palette contract (corrective phase Issue 7) -----------------------
    // Kept from the original ThemeColorsTest: the scheme itself must stay a
    // restrained blue/near-white/true-black palette.

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
