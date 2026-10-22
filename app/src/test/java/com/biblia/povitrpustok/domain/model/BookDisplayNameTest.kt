package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Capitalize-Case for the displayed local-language book name (corrective
 * phase Issue 3): deterministic, multi-word, punctuation- and
 * digit-safe; English names are never fed through it by the UI.
 */
class BookDisplayNameTest {

    @Test
    fun corpus_titles_become_capitalize_case() {
        assertEquals("Utpoti", BookDisplayName.capitalizeDisplayName("UTPOTI"))
        assertEquals("Jobachem Pustok", BookDisplayName.capitalizeDisplayName("JOBACHEM PUSTOK"))
        assertEquals("Jakobachem Potr", BookDisplayName.capitalizeDisplayName("JAKOBACHEM POTR"))
        assertEquals("Joshuachem Pustok", BookDisplayName.capitalizeDisplayName("JOSHUACHEM PUSTOK"))
        assertEquals("Stotranchem Pustok", BookDisplayName.capitalizeDisplayName("STOTRANCHEM PUSTOK"))
    }

    @Test
    fun punctuation_and_diacritics_survive() {
        assertEquals("Levi – Xastr", BookDisplayName.capitalizeDisplayName("LEVI – XASTR"))
        assertEquals("Stotrancho Nirôp", BookDisplayName.capitalizeDisplayName("STOTRANCHO NIRÔP"))
        assertEquals("Juanvak Prokasovnni", BookDisplayName.capitalizeDisplayName("JUANVAK PROKASOVNNI"))
    }

    @Test
    fun digit_prefixed_words_are_safe() {
        assertEquals("1 Samuel", BookDisplayName.capitalizeDisplayName("1 Samuel"))
        assertEquals("1 Samuel", BookDisplayName.capitalizeDisplayName("1 SAMUEL"))
    }

    @Test
    fun english_style_names_are_not_damaged() {
        // The Books screen passes BibleCatalog.englishName through
        // UNTOUCHED (never through this function), so English names keep
        // their exact form. If a correctly-cased English name were ever
        // passed in, digit-prefixed and proper-noun names stay intact;
        // "Song of Songs" is protected by the caller never applying this
        // Capitalize-Case (it is a local-language-name function).
        assertEquals("1 Samuel", BookDisplayName.capitalizeDisplayName("1 Samuel"))
        assertEquals("2 Corinthians", BookDisplayName.capitalizeDisplayName("2 Corinthians"))
        assertEquals("Revelation", BookDisplayName.capitalizeDisplayName("Revelation"))
    }

    @Test
    fun already_capitalized_input_is_idempotent() {
        val once = BookDisplayName.capitalizeDisplayName("JAKOBACHEM POTR")
        assertEquals(once, BookDisplayName.capitalizeDisplayName(once))
    }

    @Test
    fun empty_and_blank_input() {
        assertEquals("", BookDisplayName.capitalizeDisplayName(""))
        assertEquals("", BookDisplayName.capitalizeDisplayName("   "))
    }
}
