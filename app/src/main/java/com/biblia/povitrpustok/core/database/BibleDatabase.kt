package com.biblia.povitrpustok.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import com.biblia.povitrpustok.core.result.CorruptBibleDatabaseException
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse

/**
 * Read-only handle to one imported Bible book database (plan §11, §34).
 *
 * Owns the canonical SQL for the imported schema. It never writes to the
 * database and never mutates imported content; every query maps rows to
 * the domain models. Raw [SQLiteException]s are translated into
 * [BibleDatabaseAccessException] so they never reach UI code.
 *
 * Each book database contains exactly one `books` row; its id is the
 * `book_id` referenced by chapters/verses/headings/introduction_blocks.
 */
class BibleDatabase internal constructor(
    internal val sqliteDatabase: SQLiteDatabase
) : AutoCloseable {

    /**
     * The book's stable textual identity from its single `books` row
     * (`books.id` is TEXT, e.g. `jakob`). It is the value referenced by
     * `chapters.book_id` / `introduction_blocks.book_id`, so it must never
     * be coerced through a numeric accessor (DB-01: `getLong` produced `0`
     * and every book-scoped query returned no rows).
     */
    private val storedBookId: String by lazy {
        query(
            "SELECT id FROM books LIMIT 1",
            emptyArray()
        ) { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                throw CorruptBibleDatabaseException("unknown", null)
            }
        }
    }

    /** All chapters in source order (plan §34: WHERE book_id = ? ORDER BY position). */
    fun getChapters(bookId: String): List<Chapter> =
        query(
            "SELECT id, number, position FROM chapters WHERE book_id = ? ORDER BY position",
            arrayOf(storedBookId)
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Chapter(
                            id = cursor.getLong(0),
                            bookId = bookId,
                            number = cursor.getInt(1),
                            position = cursor.getInt(2)
                        )
                    )
                }
            }
        }

    /**
     * The chapter identified by source-order [position] (plan §8).
     * Never uses `chapter_number` for sequential lookup: source chapter
     * numbers can repeat (e.g. Job).
     */
    fun getChapter(bookId: String, position: Int): Chapter? =
        query(
            "SELECT id, number, position FROM chapters WHERE book_id = ? AND position = ?",
            arrayOf(storedBookId, position.toString())
        ) { cursor ->
            if (cursor.moveToFirst()) {
                Chapter(
                    id = cursor.getLong(0),
                    bookId = bookId,
                    number = cursor.getInt(1),
                    position = cursor.getInt(2)
                )
            } else {
                null
            }
        }

    /** Headings of one chapter in source order (plan §34). */
    fun getHeadings(chapterId: Long): List<Heading> =
        query(
            "SELECT id, chapter_id, position, before_verse, html FROM headings WHERE chapter_id = ? ORDER BY position",
            arrayOf(chapterId.toString())
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Heading(
                            id = cursor.getLong(0),
                            chapterId = cursor.getLong(1),
                            position = cursor.getInt(2),
                            beforeVerse = cursor.getInt(3),
                            html = cursor.getString(4)
                        )
                    )
                }
            }
        }

    /**
     * Verses of one chapter in source order (plan §34).
     *
     * DB-03: `verses.position` is the source/XML order. Ordering by
     * `verse_start` silently reordered 77 chapters whose source order is not
     * monotonic in the verse number (e.g. Job 33, Esther 3, Daniel 6's
     * preface). `id` is the stable tie-breaker if a position ever repeats.
     */
    fun getVerses(chapterId: Long): List<Verse> =
        query(
            "SELECT id, chapter_id, chapter_number, verse_start, verse_end, text, text_plain FROM verses WHERE chapter_id = ? ORDER BY position, id",
            arrayOf(chapterId.toString())
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toVerse())
                }
            }
        }

    /** One verse by internal chapter id and verse_start. */
    fun getVerse(chapterId: Long, verseStart: Int): Verse? =
        query(
            "SELECT id, chapter_id, chapter_number, verse_start, verse_end, text, text_plain FROM verses WHERE chapter_id = ? AND verse_start = ?",
            arrayOf(chapterId.toString(), verseStart.toString())
        ) { cursor ->
            if (cursor.moveToFirst()) cursor.toVerse() else null
        }

    /**
     * Source records whose effective range covers [verse] (REF-01).
     *
     * A plain verse is covered only by itself; a combined source verse
     * (e.g. 11-12, `verse_end` non-NULL) is covered by its single record.
     * Duplicate source verse numbers can yield more than one record — the
     * caller must surface that ambiguity rather than pick arbitrarily.
     * Records are returned in source position order.
     */
    fun getVersesCovering(chapterId: Long, verse: Int): List<Verse> =
        query(
            """
            SELECT id, chapter_id, chapter_number, verse_start, verse_end, text, text_plain
            FROM verses
            WHERE chapter_id = ?
              AND verse_start <= CAST(? AS INTEGER)
              AND (CASE WHEN verse_end IS NULL THEN verse_start ELSE verse_end END) >= CAST(? AS INTEGER)
            ORDER BY position, id
            """.trimIndent(),
            arrayOf(chapterId.toString(), verse.toString(), verse.toString())
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toVerse())
            }
        }

    /** Introduction blocks in source order; never flattened (plan §11, §34). */
    fun getIntroductionBlocks(bookId: String): List<IntroductionBlock> =
        query(
            "SELECT id, book_id, position, type, html FROM introduction_blocks WHERE book_id = ? ORDER BY position",
            arrayOf(storedBookId)
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        IntroductionBlock(
                            id = cursor.getLong(0),
                            bookId = bookId,
                            position = cursor.getInt(2),
                            type = cursor.getString(3),
                            html = cursor.getString(4)
                        )
                    )
                }
            }
        }

    /** Whether the FTS5 virtual table exists in this database. */
    fun hasFtsTable(): Boolean =
        query(
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'verses_fts'",
            emptyArray()
        ) { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) > 0 else false
        }

    /**
     * Whether the running SQLite build compiled the FTS5 module
     * (`ENABLE_FTS5` compile option). Desktop/Robolectric sqlite builds may
     * lack FTS5 even though the imported databases define verses_fts.
     */
    fun isFts5Available(): Boolean =
        query("PRAGMA compile_options", emptyArray()) { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0).contains("ENABLE_FTS5")) {
                    return@query true
                }
            }
            false
        }

    /** Number of rows currently indexed by the FTS5 table. */
    fun ftsRowCount(): Long =
        query(
            "SELECT count(*) FROM verses_fts",
            emptyArray()
        ) { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    /**
     * Minimal FTS5 access primitive: row ids of verses matching [query].
     *
     * This is the data-source capability the Phase 5 search feature builds
     * on; it performs no query parsing, normalization, or UI logic and is
     * not exposed as a search feature.
     */
    fun ftsMatchVerseIds(query: String, limit: Int = 50): List<Long> =
        query(
            "SELECT rowid FROM verses_fts WHERE verses_fts MATCH ? LIMIT ?",
            arrayOf(query, limit.toString())
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getLong(0))
                }
            }
        }

    /**
     * Full-text search within this book (Phase 5; corrective Issue 1; SEARCH-01):
     * a single FTS5 MATCH query joined to verses and chapters, ordered by
     * relevance (`rank`/BM25) then deterministically by chapter position,
     * verse start, verse id. The raw `verses.text_plain` is returned so the
     * repository can build a portable snippet; FTS5 `snippet()` is
     * intentionally not used because `verses_fts` (one column, external
     * content) does not match the `verses` content-table schema, which makes
     * `snippet()` behavior implementation-dependent across SQLite builds
     * (the device-side "SELECT query fails" source). No chapter is ever
     * loaded to build a result.
     */
    fun searchVerses(query: String, limit: Int = 50): List<BibleVerseHit> =
        query(
            """
            SELECT v.id, v.chapter_id, c.position, v.chapter_number,
                   v.verse_start, v.verse_end, v.text_plain
            FROM verses_fts f
            JOIN verses v ON v.id = f.rowid
            JOIN chapters c ON c.id = v.chapter_id
            WHERE verses_fts MATCH ?
            ORDER BY f.rank, c.position, v.verse_start, v.id
            LIMIT ?
            """.trimIndent(),
            arrayOf(query, limit.toString())
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    // DB-02: verse_end is NULL for ordinary single verses
                    // (getInt would return 0). A single verse has
                    // verseEnd == verseStart; real source ranges keep their end.
                    val verseStart = cursor.getInt(4)
                    add(
                        BibleVerseHit(
                            verseId = cursor.getLong(0),
                            chapterId = cursor.getLong(1),
                            chapterPosition = cursor.getInt(2),
                            chapterNumber = cursor.getInt(3),
                            verseStart = verseStart,
                            verseEnd = if (cursor.isNull(5)) verseStart else cursor.getInt(5),
                            plainText = cursor.getString(6)
                        )
                    )
                }
            }
        }

    private fun Cursor.toVerse(): Verse {
        // DB-02: coalesce a NULL verse_end (single verse) to its start so the
        // domain invariant verseEnd == verseStart holds; never expose 0.
        val verseStart = getInt(3)
        return Verse(
            id = getLong(0),
            chapterId = getLong(1),
            chapterNumber = getInt(2),
            verseStart = verseStart,
            verseEnd = if (isNull(4)) verseStart else getInt(4),
            html = getString(5),
            plainText = getString(6)
        )
    }

    private fun <T> query(sql: String, args: Array<String>, mapper: (Cursor) -> T): T =
        try {
            sqliteDatabase.rawQuery(sql, args).use { cursor -> mapper(cursor) }
        } catch (e: SQLiteException) {
            // Include the underlying SQLite error so the exact failing
            // operation is identifiable from the UI/log (Issue 1).
            throw BibleDatabaseAccessException("Query failed: $sql (${e.message})", e)
        }

    override fun close() {
        sqliteDatabase.close()
    }
}
