package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BOOKMARK-01: saved bookmarks must survive corpus row-id changes. */
class BookmarkDestinationTest {

    @Test
    fun destination_uses_stable_location_not_corpus_rowids() {
        val bookmark = Bookmark(
            id = 1,
            bookId = "jakob",
            chapterId = 999_999, // stale corpus row id
            chapterPosition = 3,
            verseId = 888_888, // stale corpus row id
            chapterNumber = 1,
            verseStart = 5,
            verseEnd = 6,
            createdAt = 0
        )

        val destination = bookmark.toReaderDestination()

        assertEquals("jakob", destination.bookId)
        assertEquals("chapter position is the stable navigation identity", 3, destination.chapterPosition)
        assertEquals(5, destination.verseStart)
        assertEquals(6, destination.verseEnd)
        assertNull("corpus row ids must not be used for navigation", destination.verseId)
    }
}
