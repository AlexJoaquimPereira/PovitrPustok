package com.biblia.povitrpustok.data.bible

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Portable snippet builder (corrective phase Issue 1): window selection,
 * case/diacritic-insensitive term location, word-boundary truncation.
 */
class SnippetBuilderTest {

    @Test
    fun short_text_is_returned_unchanged() {
        assertEquals("subhagi modem", SnippetBuilder.build("subhagi modem", "\"subhagi\""))
        assertEquals("", SnippetBuilder.build("   ", "\"x\""))
        assertEquals("", SnippetBuilder.build("", "\"x\""))
    }

    @Test
    fun terms_are_extracted_from_quoted_expression() {
        assertEquals(listOf("subhagi", "modem"), SnippetBuilder.termsFrom("\"subhagi\" \"modem\""))
        assertEquals(listOf("dil'lo"), SnippetBuilder.termsFrom("\"dil'lo\""))
        // multi-word phrases are split into words for location
        assertEquals(listOf("tujea", "peleacho"), SnippetBuilder.termsFrom("\"tujea peleacho\""))
        // no terms -> empty list
        assertTrue(SnippetBuilder.termsFrom("").isEmpty())
    }

    @Test
    fun match_is_case_insensitive() {
        val text = "Sorvesporan tujea modem ulo marlo"
        val snippet = SnippetBuilder.build(text, "\"MODEM\"")
        assertTrue("snippet must contain the match", snippet.contains("modem"))
    }

    @Test
    fun match_is_diacritic_insensitive_like_fts5() {
        // accented text, unaccented query (unicode61 remove_diacritics)
        val text = "Tanchê sõsnnikaiechi khobor tumi aikolea"
        val snippet = SnippetBuilder.build(text, "\"sosnnikaiechi\"")
        assertTrue("snippet must contain the diacritic match", snippet.contains("sõsnnikaiechi"))
        // and the reverse: accented query against unaccented fold
        val snippet2 = SnippetBuilder.build(text, "\"sõsnnikaiechi\"")
        assertTrue(snippet2.contains("sõsnnikaiechi"))
    }

    @Test
    fun index_of_fold_returns_original_text_index() {
        // 'õ' decomposes in NFD (length changes) — the index must still be
        // an index into the ORIGINAL string.
        val text = "aõb subhagi"
        val index = SnippetBuilder.indexOfFold(text, SnippetBuilder.fold("subhagi"))
        assertEquals(4, index)
        assertNull(SnippetBuilder.indexOfFold(text, "zzzz"))
    }

    @Test
    fun long_text_is_truncated_at_word_boundaries_with_ellipsis() {
        val longText = "koxttam-dogdam modem thir ravtat tim subhagi oslê porikxent " +
            "iesesvi zaun, Sorvesporan tankam vatt ditolo ani teo uncheo vorteo kortolo. " +
            "Oxem korun tankam devachea rajeacheo uncheo vorteo mellttelio."
        val snippet = SnippetBuilder.build(longText, "\"subhagi\"", maxLength = 40)
        assertTrue("snippet must contain the term", snippet.contains("subhagi"))
        assertTrue("truncated snippet must start with ellipsis", snippet.startsWith("…"))
        assertTrue("must not exceed max length + ellipsis", snippet.length <= 42)
    }

    @Test
    fun apostrophe_terms_match_verbatim() {
        val text = "Devchem utor dil'lo mhunn"
        val snippet = SnippetBuilder.build(text, "\"dil'lo\"")
        assertTrue(snippet.contains("dil'lo"))
    }

    @Test
    fun no_match_falls_back_to_abbreviated_prefix() {
        val text = "Survatim Dev sorg ani prithum rochlem"
        val snippet = SnippetBuilder.build(text, "\"zzzqqq\"", maxLength = 12)
        assertTrue(snippet.endsWith("…"))
    }
}
