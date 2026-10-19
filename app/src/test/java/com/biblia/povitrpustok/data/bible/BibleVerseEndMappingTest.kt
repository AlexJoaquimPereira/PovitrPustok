package com.biblia.povitrpustok.data.bible

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DB-02 regression: the corpus stores `verse_end` as SQL NULL for ordinary
 * single verses (34,251 of 34,272 rows). `Cursor.getInt` on NULL returns 0,
 * which made `Verse.verseEnd == 0` and rendered every plain verse as a
 * broken `n–0` range. The domain contract is that a single verse has
 * `verseEnd == verseStart`, while genuine source ranges keep their end.
 */
@RunWith(RobolectricTestRunner::class)
class BibleVerseEndMappingTest {

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
    fun null_verse_end_maps_to_verse_start_for_plain_verses() = runBlocking {
        val database = provider.openBook("jakob")
        val chapter = database.getChapters("jakob").first { it.number == 1 }
        val verses = database.getVerses(chapter.id)
        assertTrue("James 1 must contain verses", verses.isNotEmpty())

        // Prove the branch under test is actually exercised: the corpus stores NULL.
        val nullRows = countNullVerseEnd("jakob", verses.map { it.id })
        assertEquals(
            "every James 1 row must have a NULL stored verse_end",
            verses.size,
            nullRows
        )

        assertTrue(
            "a plain verse must report verseEnd == verseStart, not 0",
            verses.all { it.verseEnd == it.verseStart }
        )
        assertTrue("verse numbers must remain positive", verses.all { it.verseStart >= 1 && it.verseEnd >= 1 })

        // getVerse() shares toVerse(): the NULL branch must coalesce there too.
        val first = verses.first()
        val single = database.getVerse(chapter.id, first.verseStart)
        assertTrue("James 1:${first.verseStart} must be readable", single != null)
        assertEquals(
            "getVerse must coalesce a NULL verse_end to verse_start",
            first.verseStart,
            single!!.verseEnd
        )
    }

    @Test
    fun source_ranges_preserve_their_end() = runBlocking {
        val database = provider.openBook("Utpoti")

        // Genesis 16 stores a real combined verse 11-12 (verse_end non-NULL).
        val chapter16 = database.getChapters("Utpoti").first { it.position == 16 }
        val ranged = database.getVerses(chapter16.id).first { it.verseStart == 11 }
        assertEquals(12, ranged.verseEnd)

        // The single-verse read must return the same source range.
        val single = database.getVerse(chapter16.id, 11)
        assertTrue(single != null)
        assertEquals(12, single!!.verseEnd)

        // A second source range elsewhere in the book (Genesis 48:15-16).
        val chapter48 = database.getChapters("Utpoti").first { it.position == 48 }
        val ranged48 = database.getVerses(chapter48.id).first { it.verseStart == 15 }
        assertEquals(16, ranged48.verseEnd)
    }

    @Test
    fun verse_end_is_never_below_verse_start_across_sampled_books() = runBlocking {
        val samples = listOf("jakob", "Utpoti", "Jobachem_Pustok", "Stotranchem_Pustok", "juanv")
        val problems = mutableListOf<String>()
        for (bookId in samples) {
            val database = provider.openBook(bookId)
            for (chapter in database.getChapters(bookId)) {
                database.getVerses(chapter.id).forEach { verse ->
                    if (verse.verseEnd < verse.verseStart || verse.verseEnd < 1) {
                        problems += "$bookId ${chapter.number}:${verse.verseStart} -> end=${verse.verseEnd}"
                    }
                }
            }
        }
        assertTrue(
            "verse_end must never be below verse_start:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    /** Reads the prepared copy independently to inspect raw stored NULLs. */
    private fun countNullVerseEnd(bookId: String, verseIds: List<Long>): Int {
        val file = File(context.filesDir, "bible/$bookId.sqlite")
        val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return raw.use { database ->
            val placeholders = verseIds.joinToString(",")
            database.rawQuery(
                "SELECT count(*) FROM verses WHERE id IN ($placeholders) AND verse_end IS NULL",
                null
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
    }
}
