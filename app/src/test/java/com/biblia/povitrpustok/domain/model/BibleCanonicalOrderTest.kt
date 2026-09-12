package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canonical 73-book ordering (corrective phase Issue 2): complete, unique,
 * consistent with the actual catalog ids, OT-first/NT-second, with
 * verified canonical boundaries.
 */
class BibleCanonicalOrderTest {

    @Test
    fun mapping_contains_all_73_books_without_duplicates() {
        assertEquals(73, BibleCanonicalOrder.CANONICAL_IDS.size)
        assertEquals(73, BibleCanonicalOrder.CANONICAL_IDS.toSet().size)
        assertEquals(46, BibleCanonicalOrder.OLD_TESTAMENT_IDS.size)
        assertEquals(27, BibleCanonicalOrder.NEW_TESTAMENT_IDS.size)
    }

    @Test
    fun every_canonical_id_exists_in_the_catalog() {
        BibleCanonicalOrder.CANONICAL_IDS.forEach { id ->
            assertNotNull("canonical id '$id' must exist in BibleCatalog", BibleCatalog.byId(id))
        }
    }

    @Test
    fun every_catalog_book_is_in_the_canonical_mapping() {
        val canonical = BibleCanonicalOrder.CANONICAL_IDS.toSet()
        BibleCatalog.books.forEach { book ->
            assertTrue("catalog book '${book.id}' must be canonical", book.id in canonical)
        }
    }

    @Test
    fun old_testament_comes_first_and_new_testament_second() {
        val firstOt = BibleCanonicalOrder.OLD_TESTAMENT_IDS.first()
        val lastOt = BibleCanonicalOrder.OLD_TESTAMENT_IDS.last()
        val firstNt = BibleCanonicalOrder.NEW_TESTAMENT_IDS.first()
        val lastNt = BibleCanonicalOrder.NEW_TESTAMENT_IDS.last()

        assertEquals("Utpoti", firstOt) // Genesis first
        assertEquals("Malaki", lastOt) // Malachi last OT
        assertEquals("matev", firstNt) // Matthew first NT
        assertEquals("juanvak_prokasovnni", lastNt) // Revelation last

        // every OT id precedes every NT id
        val positions = BibleCanonicalOrder.CANONICAL_IDS.withIndex().associate { (i, id) -> id to i }
        assertTrue(positions[lastOt]!! < positions[firstNt]!!)
    }

    @Test
    fun canonical_boundaries_and_adjacencies() {
        val ids = BibleCanonicalOrder.CANONICAL_IDS

        // OT boundaries
        assertEquals(listOf("Samuelachem_Poilem_Pustok", "Samuelachem_Dusrem_Pustok"), ids.subList(8, 10))
        assertEquals(listOf("Razanchem_Poilem_Pustok", "Razanchem_Dusrem_Pustok"), ids.subList(10, 12))
        assertEquals(listOf("Makabeusachem_Poilem_Pustok", "Makabeusachem_Dusrem_Pustok", "Jobachem_Pustok"), ids.subList(19, 22))

        // NT boundaries
        assertEquals(listOf("korintkarant1", "korintkarant2"), ids.subList(46 + 6, 46 + 8))
        assertEquals(
            listOf("tesalonikarank1", "tesalonikarank2", "timotak1", "timotak2"),
            ids.subList(46 + 12, 46 + 16)
        )
        assertEquals("jakob", ids[46 + 19]) // James
        assertEquals(listOf("juanv1", "juanv2", "juanv3", "judas", "juanvak_prokasovnni"), ids.subList(46 + 22, 73))
    }

    @Test
    fun canonical_testament_differs_from_corpus_metadata_where_required() {
        // tesalonikarank1/2 are canonically NT although the corpus marks them OT
        assertEquals(Testament.NEW_TESTAMENT, BibleCanonicalOrder.testament("tesalonikarank1"))
        assertEquals(Testament.NEW_TESTAMENT, BibleCanonicalOrder.testament("tesalonikarank2"))
        // raw corpus metadata is preserved for database consumption
        assertEquals(Testament.OLD_TESTAMENT, BibleCatalog.byId("tesalonikarank1")!!.testament)
    }

    @Test
    fun position_and_testament_lookup() {
        assertEquals(0, BibleCanonicalOrder.position("Utpoti"))
        assertEquals(72, BibleCanonicalOrder.position("juanvak_prokasovnni"))
        assertEquals(Testament.OLD_TESTAMENT, BibleCanonicalOrder.testament("Esther"))
        assertEquals(Testament.NEW_TESTAMENT, BibleCanonicalOrder.testament("jakob"))
        assertNull(BibleCanonicalOrder.position("not-a-book"))
        assertNull(BibleCanonicalOrder.testament("not-a-book"))
    }
}
