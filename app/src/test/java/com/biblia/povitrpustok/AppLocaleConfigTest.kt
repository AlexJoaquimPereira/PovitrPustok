package com.biblia.povitrpustok

import androidx.appcompat.app.AppCompatActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2-13: per-app language plumbing must list English (the fallback) and Romi
 * Konkani, wire the locale config on the application, and keep the AppCompat
 * delegate usable below API 33.
 *
 * Romi Konkani is `kok-Latn` (script-qualified); `kok` alone is Devanagari and
 * must never be used as the resource qualifier.
 */
class AppLocaleConfigTest {

    private fun file(relative: String): File {
        val file = File(relative)
        assertTrue(
            "$relative must exist (working dir ${File(".").absolutePath})",
            file.isFile
        )
        return file
    }

    @Test
    fun locale_config_lists_english_then_romi_konkani() {
        val xml = file("src/main/res/xml/locales_config.xml").readText()
        val tags = Regex("""<locale\s+android:name="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            "English must be listed first so it stays the fallback",
            listOf("en", "kok-Latn"),
            tags
        )
    }

    @Test
    fun romi_konkani_uses_the_script_qualified_qualifier() {
        assertTrue(
            "Romi Konkani resources must live in values-b+kok+Latn",
            File("src/main/res/values-b+kok+Latn").isDirectory
        )
        assertFalse(
            "values-kok is Devanagari Konkani, not Romi",
            File("src/main/res/values-kok").exists()
        )
    }

    @Test
    fun manifest_wires_the_locale_config_and_the_delegate_storage() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "the application must declare the locale config",
            manifest.contains("android:localeConfig=\"@xml/locales_config\"")
        )
        assertTrue(
            "the AppCompat locale-holder service is the documented opt-in " +
                "marker for API 30-32 locale storage",
            manifest.contains("androidx.appcompat.app.AppLocalesMetadataHolderService")
        )
        assertTrue(
            "the holder service must opt in to autoStoreLocales",
            Regex("""android:name="autoStoreLocales"\s+android:value="true"""").containsMatchIn(manifest)
        )
        assertTrue(
            "the holder service is metadata only and must stay disabled",
            Regex("""AppLocalesMetadataHolderService"\s+android:enabled="false"""").containsMatchIn(manifest)
        )
    }

    @Test
    fun main_activity_is_appcompat_so_the_delegate_applies_below_api_33() {
        assertTrue(
            "AppCompatDelegate.setApplicationLocales only reaches activities " +
                "hosted by an AppCompatActivity below API 33",
            AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java)
        )
    }

    @Test
    fun romi_resources_never_define_a_key_that_english_lacks() {
        val english = stringNames(file("src/main/res/values/strings.xml").readText())
        val romi = stringNames(file("src/main/res/values-b+kok+Latn/strings.xml").readText())
        assertTrue("English strings.xml must not be empty", english.isNotEmpty())
        assertEquals(
            "every Romi key needs an English default to fall back to",
            emptySet<String>(),
            romi - english
        )
    }

    private fun stringNames(xml: String): Set<String> =
        Regex("""<string\s+name="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toSet()
}
