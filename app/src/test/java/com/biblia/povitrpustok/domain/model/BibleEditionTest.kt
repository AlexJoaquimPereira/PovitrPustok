package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1-14/F1-17: the edition seam is explicit and defaults to Konkani. */
class BibleEditionTest {

    @Test
    fun default_edition_is_konkani_and_declares_the_73_book_canon() {
        assertEquals(BibleEditions.KONKANI_ID, BibleEditions.DEFAULT.id)
        assertEquals("kok-Latn", BibleEditions.DEFAULT.languageTag)
        assertEquals(73, BibleEditions.DEFAULT.canon.size)
        assertEquals(BibleCatalog.books.map { it.id }.toSet(), BibleEditions.DEFAULT.canon)
        assertEquals(
            setOf(BibleEditions.KONKANI_ID, BibleEditions.WEBC_ID, BibleEditions.DRA_ID),
            BibleEditions.all
        )
    }

    @Test
    fun edition_asset_pattern_matches_every_shipped_catalog_asset() {
        BibleCatalog.books.forEach { book ->
            assertEquals(book.databaseAsset, BibleEditions.KONKANI.assetFor(book.id))
        }
    }

    @Test
    fun catalog_is_scoped_to_an_edition() {
        assertEquals(73, BibleCatalog.forEdition(BibleEditions.KONKANI_ID).size)
        assertTrue(BibleCatalog.forEdition("not-an-edition").isEmpty())
        assertEquals("jakob", BibleCatalog.byId("jakob", BibleEditions.KONKANI_ID)?.id)
        assertNull(BibleCatalog.byId("jakob", "not-an-edition"))
        assertEquals("jakob", BibleCatalog.byId("jakob")?.id)
    }

    @Test
    fun unknown_edition_ids_are_rejected() {
        assertNull(BibleEditions.byId("not-an-edition"))
        assertEquals(BibleEditions.KONKANI, BibleEditions.byId(null))
    }

    @Test
    fun english_editions_use_their_own_asset_subtree_and_english_names() {
        assertEquals("bible/webc/jakob.sqlite", BibleEditions.WEBC.assetFor("jakob"))
        assertEquals("bible/dra/jakob.sqlite", BibleEditions.DRA.assetFor("jakob"))
        assertEquals("en", BibleEditions.WEBC.languageTag)
        assertEquals("en", BibleEditions.DRA.languageTag)
        assertEquals("World English Bible (Catholic)", BibleEditions.WEBC.label)
        assertEquals("Douay-Rheims 1899", BibleEditions.DRA.label)
        assertEquals(73, BibleEditions.WEBC.canon.size)
        assertEquals(73, BibleEditions.DRA.canon.size)
        assertTrue(BibleEditions.isEnglish(BibleEditions.WEBC_ID))
        assertTrue(BibleEditions.isEnglish(BibleEditions.DRA_ID))
        assertFalse(BibleEditions.isEnglish(BibleEditions.KONKANI_ID))
        assertFalse(BibleEditions.isEnglish("not-an-edition"))
    }

    @Test
    fun display_names_follow_the_edition_language() {
        assertEquals("James", BibleCatalog.displayNameFor("jakob", BibleEditions.WEBC_ID))
        assertEquals("James", BibleCatalog.displayNameFor("jakob", BibleEditions.DRA_ID))
        assertEquals(
            BibleCatalog.byId("jakob")?.displayName,
            BibleCatalog.displayNameFor("jakob", BibleEditions.KONKANI_ID)
        )
        assertEquals("not-a-book", BibleCatalog.displayNameFor("not-a-book"))
    }
}
