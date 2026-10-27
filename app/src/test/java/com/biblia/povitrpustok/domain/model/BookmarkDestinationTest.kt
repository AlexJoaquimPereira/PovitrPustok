package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BOOKMARK-01: saved bookmarks must survive corpus row-id changes. */
class BookmarkDestinationTest {

    @Test
    fun destination_uses_stable_anchor_not_corpus_rowids() {
        val bookmark = Bookmark(
            id = "8d5e...",
            anchor = VerseAnchor(
                bookId = "jakob",
                chapterPosition = 3,
                verseStart = 5,
                verseEnd = 6
            ),
            chapterNumber = 1,
            createdAt = 0
        )

        val destination = bookmark.toReaderDestination()

        assertEquals("jakob", destination.bookId)
        assertEquals("chapter position is the stable navigation identity", 3, destination.chapterPosition)
        // F-05: bookmarks open the full chapter at the verse, not a fragment.
        assertEquals(5, destination.targetVerseNumber)
        assertNull(destination.verseStart)
        assertNull(destination.verseEnd)
        assertNull("corpus row ids must not be used for navigation", destination.verseId)
    }
}
