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
    fun a_locale_list_picks_the_first_offered_tag() {
        // LocaleListCompat.toLanguageTags() joins entries with commas. The
        // first offered tag must win, matching Android's resolution order.
        assertEquals(
            AppLocale.ROMI_KONKANI,
            AppLocale.offeredTagOrEnglish("kok-Latn,en")
        )
        assertEquals(
            AppLocale.ENGLISH,
            AppLocale.offeredTagOrEnglish("en,kok-Latn")
        )
        assertEquals(
            AppLocale.ROMI_KONKANI,
            AppLocale.offeredTagOrEnglish("fr,kok-Latn")
        )
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish("fr,de"))
    }

    @Test
    fun regional_tags_match_their_language() {
        // The system list carries tags like en-US; the app offers en. It must
        // not skip en-US and fall through to a later kok-Latn entry.
        assertEquals(
            AppLocale.ENGLISH,
            AppLocale.offeredTagOrEnglish("en-US,kok-Latn")
        )
        assertEquals(
            AppLocale.ROMI_KONKANI,
            AppLocale.offeredTagOrEnglish("kok-Latn-IN,en")
        )
        assertEquals(AppLocale.ENGLISH, AppLocale.offeredTagOrEnglish("en-GB"))
        assertEquals(
            AppLocale.ROMI_KONKANI,
            AppLocale.offeredTagOrEnglish("kok-Latn-IN")
        )
    }

    @Test
    fun with_no_app_specific_locale_the_choice_is_english() {
        // Robolectric's system default is English, so the system fallback
        // resolves to English here. A system default of kok-Latn would
        // correctly return Romi instead.
        assertEquals(AppLocale.ENGLISH, AppLocale.currentTag())
    }
}
