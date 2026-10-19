package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reader content-model tests (corrective phase Issues 3 & 5): one
 * authoritative sequence drives rendering order, heading interleaving by
 * before_verse, targeted-passage filtering, and verse-target indexing.
 */
class ReaderContentBuilderTest {

    private val chapter = Chapter(id = 1, bookId = "jakob", number = 1, position = 1)

    private fun heading(id: Long, beforeVerse: Int, position: Int) =
        Heading(id = id, chapterId = 1, position = position, beforeVerse = beforeVerse, html = "h$id")

    private fun verse(id: Long, start: Int, end: Int = start) =
        Verse(id = id, chapterId = 1, chapterNumber = 1, verseStart = start, verseEnd = end, html = "v$id", plainText = "v$id")

    private fun verseIds(items: List<ReaderContentItem>) =
        items.filterIsInstance<ReaderContentItem.Verse>().map { it.verse.id }

    private fun headingIds(items: List<ReaderContentItem>) =
        items.filterIsInstance<ReaderContentItem.Heading>().map { it.heading.id }

    @Test
    fun headings_are_interleaved_not_grouped_at_top() {
        val items = ReaderContentBuilder.build(
            chapter,
            headings = listOf(heading(1, 1, 1), heading(2, 3, 2)),
            verses = listOf(verse(100, 1), verse(101, 2), verse(102, 3))
        )

        val kinds = items.map { it.key }
        assertEquals(
            listOf("chapter-header", "heading-1", "verse-100", "verse-101", "heading-2", "verse-102"),
            kinds
        )
    }

    @Test
    fun heading_before_verse_N_appears_immediately_before_verse_N() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(7, 5, 1)),
            listOf(verse(1, 1), verse(2, 2), verse(3, 3), verse(4, 4), verse(5, 5), verse(6, 6))
        )

        val headingIndex = items.indexOfFirst { it.key == "heading-7" }
        val next = items[headingIndex + 1]
        assertTrue("heading must precede verse 5", next is ReaderContentItem.Verse && next.verse.verseStart == 5)
    }

    @Test
    fun multiple_headings_same_before_verse_preserve_source_order() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(1, 2, 1), heading(2, 2, 2)),
            listOf(verse(1, 1), verse(2, 2))
        )
        assertEquals(listOf(1L, 2L), headingIds(items))
    }

    @Test
    fun heading_beyond_last_verse_is_appended_deterministically() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(9, 99, 1)),
            listOf(verse(1, 1), verse(2, 2))
        )
        // not discarded; placed at the end
        assertEquals("verse-2", items[items.size - 2].key)
        assertEquals("heading-9", items[items.size - 1].key)
    }

    @Test
    fun target_index_uses_the_interleaved_sequence() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(1, 3, 1)),
            listOf(verse(100, 1), verse(101, 2), verse(102, 3))
        )
        // header, verse-100, verse-101, heading-1, verse-102
        val targetIndex = items.indexOfFirst { it is ReaderContentItem.Verse && it.verse.id == 102L }
        assertEquals(4, targetIndex)
        assertNull(items.indexOfFirst { it is ReaderContentItem.Verse && it.verse.id == 999L }.takeIf { it >= 0 })
    }

    @Test
    fun single_verse_passage_shows_only_that_verse() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(1, 1, 1)),
            (1..20).map { verse(it.toLong(), it) },
            passage = ReaderPassage(16, 16)
        )
        assertEquals(listOf(16L), verseIds(items))
        assertTrue(
            "only header/heading/verse items exist now that introductions have their own screen",
            items.all {
                it is ReaderContentItem.Header ||
                    it is ReaderContentItem.Heading ||
                    it is ReaderContentItem.Verse
            }
        )
    }

    @Test
    fun multi_verse_range_passage_shows_only_the_range() {
        val items = ReaderContentBuilder.build(
            chapter,
            emptyList(),
            (1..20).map { verse(it.toLong(), it) },
            passage = ReaderPassage(5, 8)
        )
        assertEquals(listOf(5L, 6L, 7L, 8L), verseIds(items))
    }

    @Test
    fun source_range_verse_keeps_corpus_semantics_in_passage() {
        // source record 11-12 with a requested range of 11..11 must still display
        val items = ReaderContentBuilder.build(
            chapter,
            emptyList(),
            listOf(verse(1, 1), verse(777, 11, 12), verse(778, 13, 13)),
            passage = ReaderPassage(11, 11)
        )
        assertEquals(listOf(777L), verseIds(items))
        val kept = items.filterIsInstance<ReaderContentItem.Verse>().single().verse
        assertEquals(11, kept.verseStart)
        assertEquals(12, kept.verseEnd)
    }

    @Test
    fun passage_keeps_the_heading_immediately_before_the_first_selected_verse() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(1, 1, 1), heading(2, 5, 2)),
            (1..10).map { verse(it.toLong(), it) },
            passage = ReaderPassage(5, 6)
        )
        // heading 2 (before_verse 5) precedes verse 5 and is kept; heading 1 is not
        assertEquals(listOf(2L), headingIds(items))
        val headingIndex = items.indexOfFirst { it.key == "heading-2" }
        val next = items[headingIndex + 1]
        assertTrue(next is ReaderContentItem.Verse && next.verse.verseStart == 5)
    }

    @Test
    fun passage_without_heading_has_none() {
        val items = ReaderContentBuilder.build(
            chapter,
            emptyList(),
            (1..10).map { verse(it.toLong(), it) },
            passage = ReaderPassage(3, 3)
        )
        assertEquals(listOf(3L), verseIds(items))
        assertTrue(items.none { it is ReaderContentItem.Heading })
    }

    @Test
    fun passage_at_chapter_start_and_end() {
        val verses = (1..10).map { verse(it.toLong(), it) }
        val atStart = ReaderContentBuilder.build(chapter, emptyList(), verses, ReaderPassage(1, 2))
        val atEnd = ReaderContentBuilder.build(chapter, emptyList(), verses, ReaderPassage(9, 10))
        assertEquals(listOf(1L, 2L), verseIds(atStart))
        assertEquals(listOf(9L, 10L), verseIds(atEnd))
    }

    @Test
    fun normal_mode_keeps_all_chapter_content() {
        val items = ReaderContentBuilder.build(
            chapter,
            listOf(heading(1, 1, 1)),
            listOf(verse(1, 1), verse(2, 2))
        )
        assertEquals(2, items.count { it is ReaderContentItem.Verse })
        assertEquals(1, items.count { it is ReaderContentItem.Heading })
        assertEquals(1, items.count { it is ReaderContentItem.Header })
        assertEquals(4, items.size)
    }
}
