package com.biblia.povitrpustok.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The app's own language choice (F2-14).
 *
 * English is the complete resource set and the fallback; Romi Konkani is
 * `kok-Latn` and maps to `res/values-b+kok+Latn` (F2-13). The choice is applied
 * through the AppCompat delegate rather than the framework API so the same
 * control works on every supported release, and it is persisted by the
 * `autoStoreLocales` holder declared in the manifest — the app never writes a
 * locale preference of its own.
 *
 * The two tags in [offered] are the only ones this app presents. Anything else
 * (including no app-specific locale at all) is reported as [ENGLISH], so the
 * Settings control always shows a real option as selected.
 */
object AppLocale {

    /** The complete resource set; also the fallback for any missing key. */
    const val ENGLISH = "en"

    /** Romi Konkani: script-qualified, never bare `kok` (which is Devanagari). */
    const val ROMI_KONKANI = "kok-Latn"

    /** The tags offered in Settings, in display order. */
    val offered: List<String> = listOf(ENGLISH, ROMI_KONKANI)

    /**
     * The app-specific locale, normalised to one of [offered].
     *
     * Read from the delegate on every call rather than cached: the system
     * per-app language screen can change it while the app is alive. When no
     * app-specific locale is set, the system locale decides, so the control
     * matches the language the app is actually rendering in.
     */
    fun currentTag(): String {
        val app = AppCompatDelegate.getApplicationLocales()
        if (!app.isEmpty) return offeredTagOrEnglish(app.toLanguageTags())
        return offeredTagOrEnglish(LocaleListCompat.getDefault().toLanguageTags())
    }

    /** Applies one of [offered]; an unsupported tag falls back to English. */
    fun setTag(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(offeredTagOrEnglish(tag))
        )
    }

    /**
     * Normalises a stored or requested locale list to the first offered tag.
     *
     * `toLanguageTags()` joins a multi-entry list with commas, and the system
     * list carries regional tags such as `en-US`. Matching the whole string or
     * an exact tag would therefore miss the app's real resolution; this walks
     * the list and matches by language, requiring the Latn script for Kokani so
     * bare `kok` (Devanagari) never selects Romi. Anything else is English.
     */
    fun offeredTagOrEnglish(tag: String?): String {
        val tags = tag.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (candidate in tags) {
            val locale = Locale.forLanguageTag(candidate)
            if (locale.language == "en") return ENGLISH
            if (locale.language == "kok" && locale.script == "Latn") return ROMI_KONKANI
        }
        return ENGLISH
    }
}
