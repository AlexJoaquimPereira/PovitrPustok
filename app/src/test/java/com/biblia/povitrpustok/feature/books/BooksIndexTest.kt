package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.Testament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-31: the testament jump targets must stay in step with the LazyColumn DSL
 * (one header item plus one item per book, in canonical order).
 */
class BooksIndexTest {

    private fun book(id: String, testament: Testament, position: Int) = BibleBook(
        id = id,
        displayName = id,
        englishName = id,
        testament = testament,
        position = position,
        databaseAsset = "bible/$id.sqlite"
    )

    @Test
    fun each_section_start_skips_its_own_header() {
        val sections = listOf(
            Testament.OLD_TESTAMENT to listOf(
                book("a", Testament.OLD_TESTAMENT, 1),
                book("b", Testament.OLD_TESTAMENT, 2)
            ),
            Testament.NEW_TESTAMENT to listOf(
                book("c", Testament.NEW_TESTAMENT, 3)
            )
        )

        val starts = firstRowIndexes(sections)

        assertEquals("first book sits after the Old Testament header", 1, starts[Testament.OLD_TESTAMENT])
        assertEquals(
            "the New Testament header is at 3, so its first book is at 4",
            4,
            starts[Testament.NEW_TESTAMENT]
        )
    }

    @Test
    fun a_single_section_starts_at_one() {
        val sections = listOf(
            Testament.NEW_TESTAMENT to listOf(book("c", Testament.NEW_TESTAMENT, 1))
        )

        val starts = firstRowIndexes(sections)

        assertEquals(1, starts[Testament.NEW_TESTAMENT])
        assertFalse("an absent section must not claim an index", starts.containsKey(Testament.OLD_TESTAMENT))
    }

    @Test
    fun the_real_catalog_produces_two_sections_in_order() {
        val sections = BooksGrouping.groupByTestament(
            com.biblia.povitrpustok.domain.model.BibleCatalog.books
        )

        val starts = firstRowIndexes(sections)

        assertEquals(2, starts.size)
        val old = starts.getValue(Testament.OLD_TESTAMENT)
        val new = starts.getValue(Testament.NEW_TESTAMENT)
        assertTrue("the Old Testament starts at the first row", old == 1)
        assertTrue("the New Testament starts later", new > old)
        val oldCount = sections.first { it.first == Testament.OLD_TESTAMENT }.second.size
        assertEquals("the jump lands on the row after the New Testament header", oldCount + 2, new)
    }
}
