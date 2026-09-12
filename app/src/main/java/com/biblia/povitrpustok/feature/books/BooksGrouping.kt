package com.biblia.povitrpustok.feature.books

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCanonicalOrder
import com.biblia.povitrpustok.domain.model.Testament

/**
 * Pure testament grouping for the Books screen (implementation plan §7;
 * corrective phase Issue 2).
 *
 * Grouping and ordering use the explicit [BibleCanonicalOrder] (the
 * 73-book Catholic canon keyed by corpus ids) — never the corpus's
 * potentially incorrect testament metadata or filename-derived positions.
 * Books not present in the canonical mapping are placed last (defensive;
 * none should exist).
 */
object BooksGrouping {

    /** Canonical Old then New Testament sections, each in canonical order. */
    fun groupByTestament(books: List<BibleBook>): List<Pair<Testament, List<BibleBook>>> =
        listOf(Testament.OLD_TESTAMENT, Testament.NEW_TESTAMENT)
            .mapNotNull { testament ->
                val section = books
                    .filter { BibleCanonicalOrder.testament(it.id) == testament }
                    .sortedBy { BibleCanonicalOrder.position(it.id) ?: Int.MAX_VALUE }
                if (section.isEmpty()) null else testament to section
            }
}
