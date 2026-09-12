package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the static book catalog (implementation plan §12).
 * The catalog must be complete, consistent, and resolvable without ever
 * opening a SQLite database.
 */
class BibleCatalogTest {

    @Test
    fun catalog_contains_one_entry_per_book() {
        assertEquals(73, BibleCatalog.books.size)
    }

    @Test
    fun ids_are_unique_and_match_asset_filenames() {
        val ids = BibleCatalog.books.map { it.id }
        assertEquals(73, ids.toSet().size)
        BibleCatalog.books.forEach { book ->
            assertEquals("bible/${book.id}.sqlite", book.databaseAsset)
            assertTrue("databaseAsset must point into assets/bible", book.databaseAsset.startsWith("bible/"))
        }
    }

    @Test
    fun positions_are_the_canonical_ordering_without_gaps() {
        assertEquals((1..73).toList(), BibleCatalog.books.map { it.position }.sorted())
    }

    @Test
    fun every_book_has_display_and_english_names() {
        BibleCatalog.books.forEach { book ->
            assertTrue(book.displayName.isNotBlank())
            assertTrue(book.englishName.isNotBlank())
        }
    }

    @Test
    fun byId_and_byPosition_resolve() {
        val james = BibleCatalog.byId("jakob")
        assertNotNull(james)
        assertEquals("James", james!!.englishName)
        assertEquals("Jakobachem", james.displayName)
        assertEquals(Testament.NEW_TESTAMENT, james.testament)
        assertEquals(53, james.position)

        val job = BibleCatalog.byId("Jobachem_Pustok")
        assertNotNull(job)
        assertEquals("Job", job!!.englishName)
        assertEquals(Testament.OLD_TESTAMENT, job.testament)

        assertNull(BibleCatalog.byId("does-not-exist"))
        assertNull(BibleCatalog.byPosition(999))
        assertNotNull(BibleCatalog.byPosition(1))
        assertEquals(1, BibleCatalog.byPosition(1)!!.position)
    }
}
