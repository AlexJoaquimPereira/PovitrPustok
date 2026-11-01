package com.biblia.povitrpustok.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F2-14: the Settings control must always show a real option as selected, so
 * any tag the app does not offer — including a bare `kok`, which is Devanagari
 * — normalises to English rather than leaving the control with no selection.
 */
@RunWith(RobolectricTestRunner::class)
class AppLocaleTest {

    @Test
    fun romi_konkani_is_recognised() {
        assertEquals(
            AppLocale.ROMI_KONKANI,
            AppLocale.offeredTagOrEnglish(AppLocale.ROMI_KONKANI)
        )
    }

    @Test
    fun english_and_unknown_tags_normalise_to_english() {
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish(AppLocale.ENGLISH))
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish(""))
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish(null))
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish("kok"))
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish("hi-IN"))
    }

    @Test
    fun with_no_app_specific_locale_the_choice_is_english() {
        // Nothing is stored for the app yet, so the delegate reports no
        // locales and the control must show English as the default.
        assertEquals(AppLocale.ENGLISH, AppLocale.currentTag())
    }
}
