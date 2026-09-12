package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Presentation-only chapter label behavior (Phase 7 §9, §10).
 */
class ChapterDisplayLabelTest {

    private fun chapter(id: Long, number: Int, position: Int) =
        Chapter(id = id, bookId = "Jobachem_Pustok", number = number, position = position)

    @Test
    fun unique_number_shows_plain_number() {
        val chapters = listOf(chapter(1, 1, 1), chapter(2, 2, 2))
        assertEquals("1", chapterDisplayLabel(chapters[0], chapters))
        assertEquals("2", chapterDisplayLabel(chapters[1], chapters))
    }

    @Test
    fun duplicate_numbers_get_source_order_suffixes() {
        val chapters = listOf(
            chapter(26L, 26, 26),
            chapter(27L, 26, 27),
            chapter(28L, 27, 28)
        )
        assertEquals("26 (1)", chapterDisplayLabel(chapters[0], chapters))
        assertEquals("26 (2)", chapterDisplayLabel(chapters[1], chapters))
        assertEquals("27", chapterDisplayLabel(chapters[2], chapters))
    }

    @Test
    fun label_never_changes_identity_fields() {
        val chapters = listOf(chapter(26L, 26, 26), chapter(27L, 26, 27))
        assertEquals("label must not touch ids", setOf(26L, 27L), chapters.map { it.id }.toSet())
        assertEquals("label must not touch positions", listOf(26, 27), chapters.map { it.position })
        assertEquals("label must not renumber", listOf(26, 26), chapters.map { it.number })
    }
}
