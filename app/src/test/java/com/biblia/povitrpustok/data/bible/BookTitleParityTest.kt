package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule
import java.text.Normalizer
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-12 regression guard: the catalog display name and the corpus title must
 * keep describing the same book.
 *
 * The corpus is authoritative for the displayed name while the catalog
 * supplies identity, ordering, and the fallback. A catalog rename that no
 * longer matches the corpus title would silently reintroduce the
 * "name changes between screens" defect, so this test compares them after
 * folding case, diacritics, and punctuation.
 *
 * The comparison is deliberately tolerant: the corpus legitimately differs
 * in ways that are not a different book — "PUSTOK" vs "Pustok",
 * "PUSTOKAM" vs "Pustok", the "PAULUCHEM POTR" suffix, and one extra
 * consonant in "MHONN’NNEÔ". It therefore accepts a prefix match in either
 * direction or an edit distance of at most 2, and treats an absent corpus
 * title as the documented catalog fallback (e.g. 2 Kings).
 */
@RunWith(RobolectricTestRunner::class)
class BookTitleParityTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context
    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var titles: BookTitleRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = AssetBibleDatabaseProvider(context)
        titles = BookTitleRepositoryImpl(provider)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun every_corpus_title_still_matches_its_catalog_display_name() = runBlocking {
        val mismatches = mutableListOf<String>()

        BibleCatalog.books.forEach { book ->
            val corpusTitle = titles.getDisplayName(book.id) ?: return@forEach
            if (!namesAgree(corpusTitle, book.displayName)) {
                mismatches += "${book.id}: corpus \"$corpusTitle\" vs catalog \"${book.displayName}\""
            }
        }

        assertTrue(
            "catalog and corpus book names drifted:\n" + mismatches.joinToString("\n"),
            mismatches.isEmpty()
        )
    }

    private fun namesAgree(corpusTitle: String, catalogName: String): Boolean {
        val corpus = fold(corpusTitle)
        val catalog = fold(catalogName)
        if (corpus.isEmpty() || catalog.isEmpty()) return false
        return corpus.startsWith(catalog) ||
            catalog.startsWith(corpus) ||
            editDistance(corpus, catalog) <= MAX_EDIT_DISTANCE
    }

    /** Case-, diacritic-, and punctuation-insensitive form for comparison only. */
    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .lowercase()
            .filter { it.isLetterOrDigit() }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(substitution, min(previous[j] + 1, current[j - 1] + 1))
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private companion object {
        const val MAX_EDIT_DISTANCE = 2
    }
}
