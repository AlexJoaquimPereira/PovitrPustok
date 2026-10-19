package com.biblia.povitrpustok.core.database

import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Search must remain functional on SQLite builds without FTS5. Robolectric's
 * desktop SQLite is one such build, so these tests exercise the scan fallback
 * directly (the FTS5 path is covered by the instrumented suite).
 */
@RunWith(RobolectricTestRunner::class)
class BibleDatabaseSearchFallbackTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    @Test
    fun scan_fallback_matches_accented_source_text_and_honours_the_limit() = runBlocking {
        val provider = AssetBibleDatabaseProvider(ApplicationProvider.getApplicationContext())
        try {
            val database = provider.openBook("jakob")

            val hits = database.searchVerses("subhagi", limit = 10)
            assertTrue("the fallback must match the source text ignoring accents", hits.isNotEmpty())

            val capped = database.searchVerses("subhagi", limit = 1)
            assertEquals("the per-book limit must be respected", 1, capped.size)
            assertTrue(
                "edge punctuation must be ignored like the FTS tokenizer",
                database.searchVerses("subhagi,", limit = 10).isNotEmpty()
            )
            assertTrue(
                "curly and ASCII apostrophes must match the same source text",
                database.searchVerses("dil'lo", limit = 10).isNotEmpty()
            )
            assertTrue("invalid queries must not fabricate hits", database.searchVerses("zzzznotthere", 10).isEmpty())
        } finally {
            provider.close()
        }
    }
}
