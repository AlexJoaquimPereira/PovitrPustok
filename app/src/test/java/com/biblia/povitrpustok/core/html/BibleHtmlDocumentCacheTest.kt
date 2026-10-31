package com.biblia.povitrpustok.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** F1-21: parsed HTML is reused across compositions, with a bounded LRU. */
class BibleHtmlDocumentCacheTest {

    private val parser = BibleHtmlParser()

    @Test
    fun repeated_html_is_parsed_once_and_reused() {
        var parses = 0
        val cache = BibleHtmlDocumentCache(maxEntries = 8) { html ->
            parses++
            parser.parse(html)
        }

        val first = cache.get("<p>one</p>")
        val second = cache.get("<p>one</p>")

        assertSame("the cached document must be reused", first, second)
        assertEquals("the same HTML must be parsed once", 1, parses)
        assertEquals(1, cache.size())
    }

    @Test
    fun distinct_html_is_parsed_separately() {
        var parses = 0
        val cache = BibleHtmlDocumentCache(maxEntries = 8) { html ->
            parses++
            parser.parse(html)
        }

        cache.get("<p>one</p>")
        cache.get("<p>two</p>")

        assertEquals(2, parses)
        assertEquals(2, cache.size())
    }

    @Test
    fun lru_evicts_the_least_recently_used_entry() {
        var parses = 0
        val cache = BibleHtmlDocumentCache(maxEntries = 2) { html ->
            parses++
            parser.parse(html)
        }

        cache.get("<p>a</p>")
        cache.get("<p>b</p>")
        cache.get("<p>a</p>") // a is now most recently used
        assertEquals(2, parses)

        cache.get("<p>c</p>") // evicts b
        assertEquals(3, parses)
        assertEquals(2, cache.size())

        cache.get("<p>a</p>") // still cached
        assertEquals(3, parses)

        cache.get("<p>b</p>") // was evicted, parse again
        assertEquals(4, parses)
    }

    @Test
    fun the_default_bound_holds_the_longest_chapter() {
        // Psalm 119 is the longest chapter (176 verses). The default must hold
        // it plus headings, or a full scroll-back thrashes.
        assertTrue(BibleHtmlDocumentCache.DEFAULT_MAX_ENTRIES >= 176)
    }

    @Test
    fun the_longest_chapter_is_reused_on_a_second_pass() {
        var parses = 0
        val cache = BibleHtmlDocumentCache(
            maxEntries = BibleHtmlDocumentCache.DEFAULT_MAX_ENTRIES
        ) { html ->
            parses++
            parser.parse(html)
        }
        val chapter = (1..176).map { "<p>verse $it</p>" }

        chapter.forEach { cache.get(it) }
        assertEquals("the first pass parses every verse", 176, parses)

        chapter.forEach { cache.get(it) }
        assertEquals("the whole chapter must still be cached", 176, parses)
    }

    @Test
    fun capacity_must_be_positive() {
        try {
            BibleHtmlDocumentCache(maxEntries = 0)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
