package com.biblia.povitrpustok.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [BibleSearchQueryParser] (implementation plan §24).
 */
class BibleSearchQueryParserTest {

    private val parser = BibleSearchQueryParser()

    @Test
    fun blank_and_whitespace_queries_yield_no_search() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
        assertNull(parser.parse("\t\n  "))
        assertNull(parser.parse("   \"\"   "))
    }

    @Test
    fun surrounding_whitespace_is_trimmed() {
        assertEquals("\"dev\"", parser.parse("  dev  "))
    }

    @Test
    fun repeated_whitespace_collapses_to_single_spaces() {
        assertEquals("\"dev\" \"sorvespor\"", parser.parse("dev \t  sorvespor"))
    }

    @Test
    fun multi_term_queries_become_and_phrases() {
        assertEquals("\"tujea\" \"peleacho\"", parser.parse("tujea peleacho"))
    }

    @Test
    fun ordinary_word_stays_an_ordinary_word() {
        assertEquals("\"subhagi\"", parser.parse("subhagi"))
    }

    @Test
    fun apostrophe_terms_are_kept_inside_phrase_quotes() {
        // verified against the corpus: MATCH '"dil''lo"' matches dil'lo
        assertEquals("\"dil'lo\"", parser.parse("dil'lo"))
    }

    @Test
    fun fts_special_characters_lose_their_meaning_and_cannot_crash() {
        // stray quotes are removed; the rest is wrapped in phrase quotes
        assertEquals("\"hello\"", parser.parse("hel\"lo"))
        assertEquals("\"hello\"", parser.parse("\"hello"))
        assertEquals("\"hello\" \"world\"", parser.parse("\"hello world\""))
        // * : ( ) ^ - lose FTS meaning inside phrase quotes
        assertEquals("\"hello*\"", parser.parse("hello*"))
        assertEquals("\"James\" \"1:5\"", parser.parse("James 1:5"))
        assertEquals("\"(test)\"", parser.parse("(test)"))
        assertEquals("\"-x\"", parser.parse("-x"))
        assertEquals("\"a^b\"", parser.parse("a^b"))
    }

    @Test
    fun reference_like_input_stays_an_ordinary_query() {
        val expression = parser.parse("James 1:5")
        assertFalse("no reference parsing in this phase", expression!!.startsWith("("))
        assertTrue(expression.contains("\"james\"", ignoreCase = true) || expression.contains("\"James\""))
    }
}
