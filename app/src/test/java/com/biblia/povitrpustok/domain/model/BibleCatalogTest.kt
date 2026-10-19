package com.biblia.povitrpustok.domain.model

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CATALOG-01: the static catalog must agree with the corpus it describes.
 *
 * Ids, English names, testaments, and asset paths are hard contracts;
 * display names are human-facing (and may deliberately normalize corpus
 * spelling), so they are asserted non-blank plus a guard for the known
 * "Dhumdutancho" typo.
 */
@RunWith(RobolectricTestRunner::class)
class BibleCatalogTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context
    private lateinit var provider: AssetBibleDatabaseProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = AssetBibleDatabaseProvider(context)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun catalog_ids_order_and_assets_are_unique() {
        assertEquals("the corpus has 73 books", 73, BibleCatalog.books.size)
        assertEquals(73, BibleCatalog.books.map { it.id }.toSet().size)
        assertEquals(73, BibleCatalog.books.map { it.databaseAsset }.toSet().size)
        assertTrue(BibleCatalog.books.all { it.displayName.isNotBlank() })
        assertEquals(
            "catalog positions must be a gapless 1..73",
            (1..73).toList(),
            BibleCatalog.books.map { it.position }.sorted()
        )
        assertEquals(
            "canonical order must cover every book",
            73,
            BibleCatalog.books.mapNotNull { BibleCanonicalOrder.position(it.id) }.distinct().size
        )
    }

    @Test
    fun catalog_metadata_matches_each_assets_books_row() = runBlocking {
        val problems = mutableListOf<String>()

        for (book in BibleCatalog.books) {
            if (book.databaseAsset != "bible/${book.id}.sqlite") {
                problems += "${book.id}: asset '${book.databaseAsset}' does not match id"
            }
            provider.openBook(book.id)
            val file = File(context.filesDir, "bible/${book.id}.sqlite")
            val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            raw.use { database ->
                database.rawQuery(
                    "SELECT id, english_name, testament FROM books",
                    null
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        problems += "${book.id}: database has no books row"
                    } else {
                        val id = cursor.getString(0)
                        val english = cursor.getString(1)
                        val testament = cursor.getString(2)
                        if (id != book.id) problems += "${book.id}: books.id is '$id'"
                        if (english != book.englishName) {
                            problems += "${book.id}: englishName '${book.englishName}' != corpus '$english'"
                        }
                        val expected = if (testament == "NT") {
                            Testament.NEW_TESTAMENT
                        } else {
                            Testament.OLD_TESTAMENT
                        }
                        if (book.testament != expected) {
                            problems += "${book.id}: testament ${book.testament} != corpus $testament"
                        }
                    }
                }
            }
        }

        // Guard the confirmed display-name typo without freezing every
        // human-facing normalization.
        val acts = BibleCatalog.byId("dhormdutancho_itihas")
        assertTrue(
            "Acts display name must use 'Dhormdutancho', got '${acts?.displayName}'",
            acts?.displayName?.startsWith("Dhormdutancho") == true
        )

        assertTrue(
            "catalog/corpus mismatches:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }
}
