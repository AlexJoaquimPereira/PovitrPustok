package com.biblia.povitrpustok.core.html

/**
 * Bounded, content-level cache for parsed HTML documents (F1-21).
 *
 * The reader parses verse and heading HTML from the database. Compose's
 * `remember(html)` avoids reparsing within one composition, but a row that
 * scrolls out of the lazy list is disposed and re-parsed when it returns.
 * This cache is keyed by the exact source HTML and is shared by all reader
 * compositions, so the same verse is parsed once per process until it is
 * evicted.
 *
 * The LRU bound keeps memory predictable for a long reading session: at most
 * [maxEntries] documents, with the least recently used entry evicted first.
 * Access is synchronized because the cache is process-wide and tests/tools
 * may call it outside the main thread.
 */
class BibleHtmlDocumentCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val parse: (String) -> BibleHtmlDocument = BibleHtmlParser()::parse
) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val entries = object : LinkedHashMap<String, BibleHtmlDocument>(
        maxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, BibleHtmlDocument>?
        ): Boolean = size > maxEntries
    }

    /** Returns the cached document for [html], parsing it on the first miss. */
    fun get(html: String): BibleHtmlDocument = synchronized(entries) {
        entries.getOrPut(html) { parse(html) }
    }

    /** Visible for tests: the number of retained documents. */
    internal fun size(): Int = synchronized(entries) { entries.size }

    companion object {
        /** Roughly two long chapters of verses plus headings. */
        const val DEFAULT_MAX_ENTRIES = 128

        /**
         * Process-wide cache shared by the reader and book-introduction
         * rendering. Both screens display corpus HTML and can revisit the
         * same strings; one bounded cache keeps the memory ceiling simple.
         */
        val shared: BibleHtmlDocumentCache by lazy { BibleHtmlDocumentCache() }
    }
}
