package com.biblia.povitrpustok.domain.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Book-name matching tests (Phase 6 §18) against the real BibleCatalog.
 */
class BookNameMatcherTest {

    @Test
    fun full_names_resolve_to_catalog_books() {
        assertEquals("jakob", BookNameMatcher.match("James")!!.id)
        assertEquals("juanv", BookNameMatcher.match("John")!!.id)
        assertEquals("Utpoti", BookNameMatcher.match("Genesis")!!.id)
        assertEquals("Esther", BookNameMatcher.match("Esther")!!.id)
        assertEquals("Jobachem_Pustok", BookNameMatcher.match("Job")!!.id)
    }

    @Test
    fun abbreviations_resolve() {
        assertEquals("jakob", BookNameMatcher.match("Jas")!!.id)
        assertEquals("jakob", BookNameMatcher.match("Jm")!!.id)
        assertEquals("juanv", BookNameMatcher.match("Jn")!!.id)
        assertEquals("Utpoti", BookNameMatcher.match("Gen")!!.id)
    }

    @Test
    fun lectionary_aliases_and_multi_word_books_resolve() {
        assertEquals("Stotranchem_Pustok", BookNameMatcher.match("Psalm")!!.id)
        assertEquals("Stotranchem_Pustok", BookNameMatcher.match("Psalms")!!.id)
        assertEquals("Stotranchem_Pustok", BookNameMatcher.match("psalm")!!.id)
        assertEquals("Razanchem_Poilem_Pustok", BookNameMatcher.match("1 Kings")!!.id)
        assertEquals("Razanchem_Dusrem_Pustok", BookNameMatcher.match("2 Kings")!!.id)
        assertEquals("juanv1", BookNameMatcher.match("1 John")!!.id)
        assertEquals("korintkarant1", BookNameMatcher.match("1 Corinthians")!!.id)
        assertEquals("korintkarant2", BookNameMatcher.match("2 Corinthians")!!.id)
        assertEquals("Itihasachem_Dusrem_Pustok", BookNameMatcher.match("2 Chronicles")!!.id)
        assertEquals("Makabeusachem_Poilem_Pustok", BookNameMatcher.match("1 Maccabees")!!.id)
        // corpus metadata lacks proper English names for Thessalonians;
        // lectionary aliases provide them (catalog untouched)
        assertEquals("tesalonikarank1", BookNameMatcher.match("1 Thessalonians")!!.id)
        assertEquals("tesalonikarank2", BookNameMatcher.match("2 Thessalonians")!!.id)
        assertEquals("tesalonikarank1", BookNameMatcher.match("1 THESSALONIANS")!!.id)
    }

    /**
     * F-12: renaming the catalog to the corpus titles must not break the
     * short Konkani names users previously saw and could type.
     */
    @Test
    fun previous_short_display_names_still_resolve() {
        assertEquals("korintkarant1", BookNameMatcher.match("Poilem")!!.id)
        assertEquals("korintkarant2", BookNameMatcher.match("Dusrem")!!.id)
        assertEquals("romkarank", BookNameMatcher.match("Romkarant")!!.id)
        assertEquals("tesalonikarank1", BookNameMatcher.match("Tesalonikarank Poilem")!!.id)
        assertEquals("tesalonikarank2", BookNameMatcher.match("Tesalonikarank Dusrem")!!.id)
        assertEquals("timotak1", BookNameMatcher.match("Timotak Poilem")!!.id)
        assertEquals("timotak2", BookNameMatcher.match("Timotak Dusrem")!!.id)

        // The new long forms match through the catalog display name.
        assertEquals(
            "korintkarant1",
            BookNameMatcher.match("Korintkarank Pauluchem Poilem Potr")!!.id
        )
        assertEquals("romkarank", BookNameMatcher.match("Romkarank Pauluchem Potr")!!.id)
    }

    @Test
    fun matching_is_case_insensitive() {
        assertEquals("jakob", BookNameMatcher.match("JAMES")!!.id)
        assertEquals("jakob", BookNameMatcher.match("james")!!.id)
        assertEquals("jakob", BookNameMatcher.match("JaS")!!.id)
        assertEquals("Utpoti", BookNameMatcher.match("gEn")!!.id)
    }

    @Test
    fun surrounding_whitespace_is_tolerated() {
        assertEquals("jakob", BookNameMatcher.match("  James  ")!!.id)
    }

    @Test
    fun display_name_also_matches() {
        // James' Konkani display name
        assertEquals("jakob", BookNameMatcher.match("Jakobachem")!!.id)
    }

    @Test
    fun unknown_names_return_null() {
        assertNull(BookNameMatcher.match("NotABook"))
        assertNull(BookNameMatcher.match("Gn")) // not a supported abbreviation
        assertNull(BookNameMatcher.match(""))
        assertNull(BookNameMatcher.match("   "))
    }

    @Test
    fun ecclesiastes_alias_resolves() {
        // REF-03: the catalog uses "Qoheleth"; lectionaries use Ecclesiastes.
        assertEquals("Upodexok", BookNameMatcher.match("Ecclesiastes")?.id)
        assertEquals("Upodexok", BookNameMatcher.match("Eccl")?.id)
    }

    @Test
    fun no_silent_abbreviation_guessing() {
        // an unknown abbreviation must not resolve to a similar book
        assertNull(BookNameMatcher.match("Jer"))
        assertNull(BookNameMatcher.match("Mat"))
    }
}
