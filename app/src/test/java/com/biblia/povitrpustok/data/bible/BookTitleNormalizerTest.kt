package com.biblia.povitrpustok.data.bible

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Book title normalization (corrective phase Issue 2): collapses only
 * non-name whitespace noise; punctuation and diacritics survive verbatim.
 */
class BookTitleNormalizerTest {

    @Test
    fun double_spaces_are_collapsed_and_edges_trimmed() {
        assertEquals("JOSHUACHEM PUSTOK", BookTitleNormalizer.normalizeTitleText("JOSHUACHEM  PUSTOK"))
        assertEquals("DUSRI SOMURT", BookTitleNormalizer.normalizeTitleText("  DUSRI  SOMURT  "))
        assertEquals("STOTRANCHEM PUSTOK", BookTitleNormalizer.normalizeTitleText("STOTRANCHEM  PUSTOK"))
    }

    @Test
    fun punctuation_is_preserved() {
        assertEquals("LEVI – XASTR", BookTitleNormalizer.normalizeTitleText("LEVI – XASTR"))
        assertEquals("JUANVACHEM POILEM POTR", BookTitleNormalizer.normalizeTitleText("JUANVACHEM  POILEM  POTR"))
    }

    @Test
    fun diacritics_are_preserved() {
        assertEquals("STOTRANCHO NIRÔP", BookTitleNormalizer.normalizeTitleText("STOTRANCHO  NIRÔP"))
    }

    @Test
    fun single_spaces_are_unchanged() {
        assertEquals("JAKOBACHEM POTR", BookTitleNormalizer.normalizeTitleText("JAKOBACHEM POTR"))
        assertEquals("UTPOTI", BookTitleNormalizer.normalizeTitleText("UTPOTI"))
    }

    @Test
    fun newlines_and_tabs_are_treated_as_whitespace() {
        assertEquals("AMOS", BookTitleNormalizer.normalizeTitleText("\nAMOS\n"))
    }
}
