package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule
import java.text.Normalizer
import java.util.Locale
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
 * The comparison is exact. The only tolerated differences are listed in
 * [ACCEPTED_CORPUS_VARIANTS] (formal Gospel headings and "POTR" suffixes
 * that are deliberately not part of the short catalog name) and
 * [BOOKS_WITHOUT_CORPUS_TITLE] (the corpus has no title block, so the
 * catalog name is the documented fallback). An allowlist entry that is no
 * longer needed fails the test, so the list cannot rot.
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
        val staleAllowlist = mutableListOf<String>()

        BibleCatalog.books.forEach { book ->
            val corpusTitle = titles.getDisplayName(book.id)
            if (corpusTitle == null) {
                if (book.id !in BOOKS_WITHOUT_CORPUS_TITLE) {
                    mismatches += "${book.id}: catalog \"${book.displayName}\" has no corpus title"
                }
                return@forEach
            }

            val corpus = fold(corpusTitle)
            val catalog = fold(book.displayName)
            val accepted = ACCEPTED_CORPUS_VARIANTS[book.id]
            when {
                corpus == catalog -> if (accepted != null) staleAllowlist += book.id
                accepted == corpus -> Unit
                else -> mismatches +=
                    "${book.id}: corpus \"$corpusTitle\" vs catalog \"${book.displayName}\""
            }
        }

        assertTrue(
            "catalog and corpus book names drifted:\n" + mismatches.joinToString("\n"),
            mismatches.isEmpty()
        )
        assertTrue(
            "obsolete allowlist entries (corpus now equals the catalog):\n" +
                staleAllowlist.joinToString("\n"),
            staleAllowlist.isEmpty()
        )
    }

    /** Case-, diacritic-, and punctuation-insensitive form for comparison only. */
    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    private companion object {
        /**
         * Books whose corpus heading legitimately carries more than the short
         * catalog name (formal Gospel title, "POTR"/"POTR"-style suffix, or a
         * documented corpus spelling). Values are the folded corpus title.
         */
        val ACCEPTED_CORPUS_VARIANTS: Map<String, String> = mapOf(
            "Fuddareanchem_Pustok" to "fuddareanchempustok",
            "Itihasachem_Poilem_Pustok" to "itihasachempoilempustokam",
            "Mhonnneo" to "mhonnnneo",
            "Razanchem_Poilem_Pustok" to "razanchempoilempustokam",
            "efezkarank" to "efezkarankpauluchempotr",
            "filemonak" to "filemonakpauluchempotr",
            "filipkarank" to "filipkarankpauluchempotr",
            "hebrevank" to "hebrevankpotr",
            "jakob" to "jakobachempotr",
            "juanv" to "juanvapormannemjezukristachemxubhvortoman",
            "juanv1" to "juanvachempoilempotr",
            "juanv2" to "juanvachemdusrempotr",
            "juanv3" to "juanvachemtisrempotr",
            "juanvak_prokasovnni" to "juanvakprokasovnni",
            "judas" to "judasachempotr",
            "koloskarank" to "koloskarankpauluchempotr",
            "lukas" to "lukapormannemjezukristachemxubhvortoman",
            "mark" to "markupormannemjezukristachemxubhvortoman",
            "matev" to "matevapormannemjezukristachemxubhvortoman",
            "pedru1" to "pedruchempoilempotr",
            "pedru2" to "pedruchemdusrempotr",
            "tilak" to "titakpauluchempotr"
        )

        /** Books with no `title` introduction block (catalog fallback is the name). */
        val BOOKS_WITHOUT_CORPUS_TITLE = setOf("Razanchem_Dusrem_Pustok")
    }
}
