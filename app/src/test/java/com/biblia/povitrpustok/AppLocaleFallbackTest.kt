package com.biblia.povitrpustok

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F2-13/F2-14: selecting Romi Konkani must never blank the UI or throw, and
 * the generated `values-b+kok+Latn` set must resolve translated keys to Romi
 * while every untranslated key falls back to the English default.
 */
@RunWith(RobolectricTestRunner::class)
class AppLocaleFallbackTest {

    private fun romiContext(): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val romi = Locale.forLanguageTag("kok-Latn")
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(romi)
            setLayoutDirection(romi)
        }
        return app.createConfigurationContext(configuration)
    }

    @Test
    fun romi_configuration_is_applied() {
        val romi = romiContext()
        assertEquals("kok-Latn", romi.resources.configuration.locales[0].toLanguageTag())
    }

    @Test
    fun every_string_resolves_under_romi_konkani() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val romi = romiContext()
        val ids = R.string::class.java.fields.map { it.getInt(null) }
        assertTrue("R.string must expose the app's strings", ids.isNotEmpty())
        ids.forEach { id ->
            val resolved = romi.getString(id)
            assertTrue(
                "string id $id must not resolve to empty text under kok-Latn",
                resolved.isNotEmpty()
            )
        }
    }

    @Test
    fun untranslated_strings_still_fall_back_to_english() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val romi = romiContext()
        // app_name is a brand name and is not part of the translated chrome;
        // it must resolve to the English default rather than to empty text.
        assertEquals(app.getString(R.string.app_name), romi.getString(R.string.app_name))
    }

    @Test
    fun translated_keys_resolve_to_romi() {
        val romi = romiContext()
        // F2-14: the generated locale is no longer an empty scaffold, so a
        // translated key must resolve to its Romi value, not to English.
        assertEquals("Survat", romi.getString(R.string.nav_home))
        assertEquals("Voll copy kor", romi.getString(R.string.reader_copy_verse))
        assertEquals("VACHOP", romi.getString(R.string.settings_section_reading))
        assertEquals("APP-A VIXIM", romi.getString(R.string.settings_section_about))
    }

    @Test
    fun a_keep_english_key_stays_english_under_romi() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val romi = romiContext()
        // `settings_title` is a keep-English row: the generator emits the
        // English text as an explicit value, so it must match the default.
        assertEquals(
            app.getString(R.string.settings_title),
            romi.getString(R.string.settings_title)
        )
    }
}
