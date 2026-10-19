package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** REF-04: choosing a duplicate chapter must keep the requested verse target. */
class ScriptureResolutionDestinationTest {

    @Test
    fun ambiguous_chapter_destination_preserves_requested_verse() {
        val candidates = listOf(
            Chapter(id = 1L, bookId = "Jobachem_Pustok", number = 26, position = 26),
            Chapter(id = 2L, bookId = "Jobachem_Pustok", number = 26, position = 27)
        )
        val resolution = ScriptureResolution.Ambiguous(
            bookId = "Jobachem_Pustok",
            chapterNumber = 26,
            candidates = candidates,
            verseStart = 5,
            verseEnd = 8
        )

        val destination = resolution.toReaderDestination(candidates[1])

        assertEquals("Jobachem_Pustok", destination.bookId)
        assertEquals("the chosen candidate's position is the navigation identity", 27, destination.chapterPosition)
        assertEquals(5, destination.verseStart)
        assertEquals(8, destination.verseEnd)
        assertNull("no verse id is known before the chapter is loaded", destination.verseId)
    }
}
