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
 * F2-13: selecting Romi Konkani must never blank the UI or throw while the
 * `values-b+kok+Latn` resource set is still empty — every string resolves via
 * the English default until F2-14 fills the translation in.
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
}
