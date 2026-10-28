package com.biblia.povitrpustok.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun capacity_must_be_positive() {
        try {
            BibleHtmlDocumentCache(maxEntries = 0)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
