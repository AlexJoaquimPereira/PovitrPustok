package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route codec for non-continuous / cross-chapter passages. The app's
 * navigation contract depends on this format surviving a process round trip
 * and on malformed parts being dropped rather than misread.
 */
class ReaderPassageCodecTest {

    @Test
    fun round_trips_closed_open_and_whole_chapter_ranges() {
        val passages = listOf(
            ReaderPassageSpec(1, 16, 20),
            ReaderPassageSpec(2, 5, null, openEnd = true),
            ReaderPassageSpec(3, null, 4),
            ReaderPassageSpec(4, null, null)
        )

        assertEquals(passages, ReaderPassageCodec.decode(ReaderPassageCodec.encode(passages)))
        assertEquals(
            "1:16-20,2:5-,3:-4,4:-",
            ReaderPassageCodec.encode(passages)
        )
    }

    @Test
    fun blank_input_decodes_to_no_passages() {
        assertTrue(ReaderPassageCodec.decode(null).isEmpty())
        assertTrue(ReaderPassageCodec.decode("").isEmpty())
        assertTrue(ReaderPassageCodec.decode("   ").isEmpty())
    }

    @Test
    fun malformed_parts_are_dropped_not_interpreted() {
        assertEquals(
            listOf(ReaderPassageSpec(2, 5, 8)),
            ReaderPassageCodec.decode("garbage,2:5-8,3,4:x-y,:1-2")
        )
    }

    @Test
    fun open_end_is_inferred_only_when_the_end_is_omitted() {
        val decoded = ReaderPassageCodec.decode("1:5-,2:-8,3:1-2")
        assertEquals(
            listOf(
                ReaderPassageSpec(1, 5, null, openEnd = true),
                ReaderPassageSpec(2, null, 8, openEnd = false),
                ReaderPassageSpec(3, 1, 2, openEnd = false)
            ),
            decoded
        )
    }
}
