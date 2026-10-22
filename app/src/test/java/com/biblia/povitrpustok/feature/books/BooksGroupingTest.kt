package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Testament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Books catalog coverage and testament grouping (Phase 7 §5, §6, §21;
 * corrective phase Issue 2 — grouping now follows the explicit canonical
 * order, not the corpus's potentially incorrect testament metadata).
 */
class BooksGroupingTest {

    @Test
    fun all_73_books_appear_in_exactly_one_section() {
        val sections = BooksGrouping.groupByTestament(BibleCatalog.books)
        assertEquals(2, sections.size)

        val all = sections.flatMap { (_, books) -> books }
        assertEquals(73, all.size)
        assertEquals(
            "no duplicates across sections",
            73,
            all.map { it.id }.toSet().size
        )
    }

    @Test
    fun sections_follow_the_canonical_testament() {
        val sections = BooksGrouping.groupByTestament(BibleCatalog.books).toMap()
        val oldTestament = sections.getValue(Testament.OLD_TESTAMENT)
        val newTestament = sections.getValue(Testament.NEW_TESTAMENT)

        oldTestament.forEach { assertEquals("book ${it.id} must be canonically OT", Testament.OLD_TESTAMENT, com.biblia.povitrpustok.domain.model.BibleCanonicalOrder.testament(it.id)) }
        newTestament.forEach { assertEquals("book ${it.id} must be canonically NT", Testament.NEW_TESTAMENT, com.biblia.povitrpustok.domain.model.BibleCanonicalOrder.testament(it.id)) }
    }

    @Test
    fun ordering_is_canonical_not_alphabetical() {
        val old = BooksGrouping.groupByTestament(BibleCatalog.books)
            .first { it.first == Testament.OLD_TESTAMENT }.second
        assertEquals("Genesis", old.first().englishName)
        assertEquals("Malachi", old.last().englishName)
        assertTrue("Genesis must precede Exodus", old.map { it.id }.indexOf("Utpoti") < old.map { it.id }.indexOf("Suttka"))
    }

    @Test
    fun corpus_metadata_anomalies_are_preserved_in_the_catalog_but_not_in_grouping() {
        val sections = BooksGrouping.groupByTestament(BibleCatalog.books).toMap()
        val newTestament = sections.getValue(Testament.NEW_TESTAMENT)

        // CATALOG-01: Thessalonians are NT in both the canonical order and
        // the (corrected) catalog metadata.
        assertTrue(newTestament.any { it.id == "tesalonikarank1" })
        assertTrue(newTestament.any { it.id == "tesalonikarank2" })
        assertEquals(Testament.NEW_TESTAMENT, BibleCatalog.byId("tesalonikarank1")!!.testament)
        // short display names preserved
        assertEquals("Korintkarank Pauluchem Poilem Potr", BibleCatalog.byId("korintkarant1")!!.displayName)
    }
}
