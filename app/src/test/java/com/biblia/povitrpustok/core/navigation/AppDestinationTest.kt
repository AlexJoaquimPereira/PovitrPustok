package com.biblia.povitrpustok.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route contract tests (Phase 7 §14, §21): stable ids, compact arguments,
 * no collisions.
 */
class AppDestinationTest {

    @Test
    fun book_chapters_route_uses_book_id() {
        assertEquals("books/jakob", AppDestination.bookChapters("jakob"))
        assertEquals("books/Jobachem_Pustok", AppDestination.bookChapters("Jobachem_Pustok"))
        assertEquals(AppDestination.BOOK_CHAPTERS, "books/{bookId}")
    }

    @Test
    fun book_introduction_route_uses_book_id() {
        assertEquals("books/jakob/introduction", AppDestination.bookIntroduction("jakob"))
        assertEquals(AppDestination.BOOK_INTRODUCTION, "books/{bookId}/introduction")
    }

    @Test
    fun reader_route_uses_book_id_and_chapter_position() {
        assertEquals("reader/jakob/3", AppDestination.reader("jakob", 3))
        assertEquals("reader/jakob/3?verseId=42", AppDestination.reader("jakob", 3, 42L))
        assertEquals(
            AppDestination.READER,
            "reader/{bookId}/{chapterPosition}?verseId={verseId}&verseStart={verseStart}&verseEnd={verseEnd}&passages={passages}&targetVerse={targetVerse}"
        )
    }

    @Test
    fun reader_target_verse_number_is_optional() {
        assertEquals(
            "reader/jakob/3?targetVerse=16",
            AppDestination.reader("jakob", 3, targetVerseNumber = 16)
        )
    }

    @Test
    fun reader_verse_target_is_optional_and_backwards_compatible() {
        // existing callers omit verseId and still produce the plain route
        assertEquals("reader/jakob/1", AppDestination.reader("jakob", 1))
        assertEquals("reader/jakob/1?verseId=7", AppDestination.reader("jakob", 1, 7L))
        assertEquals(null, AppDestination.reader("jakob", 1).takeIf { it.contains("verseId") })
    }

    @Test
    fun reader_passage_route_carries_the_requested_range() {
        assertEquals("reader/jakob/1?verseStart=5&verseEnd=8", AppDestination.reader("jakob", 1, null, 5, 8))
        assertEquals(
            "reader/jakob/1?verseId=7&verseStart=5&verseEnd=8",
            AppDestination.reader("jakob", 1, 7L, 5, 8)
        )
        assertEquals("reader/jakob/1", AppDestination.reader("jakob", 1, null, null, null))
    }

    @Test
    fun chapter_navigation_preserves_position_not_number() {
        // navigation identity is the position; both Job duplicate-26 records
        // resolve to distinct routes
        assertNotEquals(
            AppDestination.reader("Jobachem_Pustok", 26),
            AppDestination.reader("Jobachem_Pustok", 27)
        )
    }

    @Test
    fun route_constants_are_distinct() {
        val routes = setOf(
            AppDestination.HOME,
            AppDestination.DAILY_READING,
            AppDestination.BOOKMARKS,
            AppDestination.BOOKS,
            AppDestination.BOOK_CHAPTERS,
            AppDestination.BOOK_INTRODUCTION,
            AppDestination.READER,
            AppDestination.SEARCH,
            AppDestination.REFERENCE_ENTRY,
            AppDestination.ABOUT
        )
        assertEquals(10, routes.size)
        assertEquals("about", AppDestination.ABOUT)
    }

    @Test
    fun routes_carry_no_content() {
        assertTrue(!AppDestination.BOOK_CHAPTERS.contains("html"))
        assertTrue(!AppDestination.READER.contains("chapterNumber"))
        assertTrue(!AppDestination.READER.contains("SearchResult"))
        assertTrue(!AppDestination.READER.contains("text_plain"))
    }
}
