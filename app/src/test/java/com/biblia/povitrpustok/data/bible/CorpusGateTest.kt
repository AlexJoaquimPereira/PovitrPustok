package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.BibleCatalog
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TEST-02: corpus presence/schema gate.
 *
 * The app ships 73 per-book SQLite assets. This gate fails early with
 * actionable per-book diagnostics if any asset is missing, has a non-textual
 * `books.id`, lacks the read schema, is empty, has an inverted verse range,
 * or drifts from the shared schema. It runs as part of the normal JVM suite.
 */
@RunWith(RobolectricTestRunner::class)
class CorpusGateTest {

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
    fun every_expected_asset_opens_with_the_required_schema_and_textual_id() = runBlocking {
        val assets = context.assets.list("bible").orEmpty().toSet()
        assertEquals("the packaged bible asset directory must contain 73 files", 73, assets.size)

        val problems = mutableListOf<String>()
        val schemaFingerprints = mutableSetOf<String>()

        for (book in BibleCatalog.books) {
            val assetName = "${book.id}.sqlite"
            if (assetName !in assets) {
                problems += "${book.id}: missing packaged asset $assetName"
                continue
            }
            provider.openBook(book.id)
            val file = File(context.filesDir, "bible/$assetName")
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                val tables = tableNames(database)
                val missingTables = REQUIRED_TABLES - tables
                if (missingTables.isNotEmpty()) {
                    problems += "${book.id}: missing tables $missingTables"
                }
                if (FTS_TABLE !in tables) {
                    problems += "${book.id}: missing FTS table $FTS_TABLE"
                }

                val idType = scalarString(database, "SELECT typeof(id) FROM books LIMIT 1")
                if (idType != "text") {
                    problems += "${book.id}: books.id type is '$idType', expected 'text'"
                }
                val booksRows = scalar(database, "SELECT count(*) FROM books")
                if (booksRows != 1) {
                    problems += "${book.id}: expected exactly one books row, found $booksRows"
                }

                val chapters = scalar(database, "SELECT count(*) FROM chapters")
                val verses = scalar(database, "SELECT count(*) FROM verses")
                if (chapters <= 0) problems += "${book.id}: no chapters"
                if (verses <= 0) problems += "${book.id}: no verses"

                val inverted = scalar(
                    database,
                    "SELECT count(*) FROM verses WHERE verse_end IS NOT NULL AND verse_end < verse_start"
                )
                if (inverted != 0) {
                    problems += "${book.id}: $inverted inverted verse ranges"
                }

                schemaFingerprints += schemaFingerprint(database)
            }
        }

        assertEquals(
            "all assets must share one read schema (found ${schemaFingerprints.size} variants)",
            1,
            schemaFingerprints.size
        )
        assertTrue(
            "corpus gate failures:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun representative_books_have_meaningful_chapter_counts() = runBlocking {
        val expected = mapOf(
            "jakob" to 5,
            "Utpoti" to 50,
            "Jobachem_Pustok" to 44,
            "Esther" to 10,
            "juanv" to 21,
            "Stotranchem_Pustok" to 150
        )
        for ((bookId, count) in expected) {
            assertEquals(
                "$bookId chapter count",
                count,
                provider.openBook(bookId).getChapters(bookId).size
            )
        }

        // Content probe: schema-valid but emptied text must not pass the gate.
        val james = provider.openBook("jakob")
        val chapterOne = james.getChapters("jakob").first { it.number == 1 }
        val verses = james.getVerses(chapterOne.id)
        assertTrue("James 1 must contain verses", verses.isNotEmpty())
        assertTrue(
            "James 1 verses must carry non-blank formatted and plain text",
            verses.all { it.html.isNotBlank() && it.plainText.isNotBlank() }
        )
    }

    @Test
    fun fts_row_count_matches_verse_count_when_fts5_is_available() = runBlocking {
        val database = provider.openBook("jakob")
        assumeTrue(
            "FTS5 module not available in this sqlite build",
            database.isFts5Available()
        )
        val verseCount = database.getChapters("jakob").sumOf { database.getVerses(it.id).size }
        assertEquals(verseCount.toLong(), database.ftsRowCount())
    }

    private fun tableNames(database: SQLiteDatabase): Set<String> =
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            null
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun scalar(database: SQLiteDatabase, sql: String): Int =
        database.rawQuery(sql, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun scalarString(database: SQLiteDatabase, sql: String): String? =
        database.rawQuery(sql, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Stable fingerprint of the read schema (table/column/type + user version). */
    private fun schemaFingerprint(database: SQLiteDatabase): String = buildString {
        append("user_version=").append(database.version).append(';')
        for (table in REQUIRED_TABLES) {
            append(table).append('(')
            database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    append(cursor.getString(1)).append(':').append(cursor.getString(2)).append(',')
                }
            }
            append(");")
        }
    }

    private companion object {
        val REQUIRED_TABLES = listOf(
            "books",
            "chapters",
            "verses",
            "headings",
            "introduction_blocks"
        )
        const val FTS_TABLE = "verses_fts"
    }
}
