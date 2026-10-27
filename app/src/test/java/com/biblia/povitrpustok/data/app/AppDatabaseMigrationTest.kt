package com.biblia.povitrpustok.data.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F1-07: migrations are validated against the exported Room schemas rather
 * than a hand-written DDL comparison.
 *
 * The v1 and v2 schema JSONs are checked in beside the current v3 schema, so
 * MigrationTestHelper can recreate a real older database, run the production
 * migrations, and validate the result against the exported target schema.
 *
 * These tests run under `testDebugUnitTest`; the schema JSON is carried as a
 * debug-variant asset so Robolectric can read it. Release unit tests are not a
 * supported schema-test target (the release APK does not ship the schemas).
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun migration_1_to_2_creates_liturgical_cache_and_preserves_v1_rows() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO bookmarks " +
                    "(bookId,chapterId,chapterPosition,verseId,chapterNumber,verseStart,verseEnd,createdAt) " +
                    "VALUES ('jakob',1,1,4,1,5,5,1)"
            )
            execSQL(
                "INSERT INTO reading_state (id,bookId,chapterPosition,updatedAt) " +
                    "VALUES (1,'jakob',3,1)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )
        try {
            val tables = tableNames(database)
            assertTrue(tables.contains("liturgical_day_cache"))
            assertTrue(tables.contains("liturgical_reading"))

            assertEquals(
                "bookmarks must survive the migration",
                1,
                scalar(database, "SELECT count(*) FROM bookmarks")
            )
            assertEquals(
                "reading state must survive the migration",
                1,
                scalar(database, "SELECT count(*) FROM reading_state")
            )
            assertEquals(
                "the liturgical cache starts empty",
                0,
                scalar(database, "SELECT count(*) FROM liturgical_day_cache")
            )

            assertTrue(
                "the date index must be unique",
                database.query("PRAGMA index_list(`liturgical_day_cache`)").use { cursor ->
                    var found = false
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1)
                        val unique = cursor.getInt(2) == 1
                        if (name == "index_liturgical_day_cache_date" && unique) found = true
                    }
                    found
                }
            )

            assertTrue(
                "readings must cascade-delete with their day",
                database.query("PRAGMA foreign_key_list(`liturgical_reading`)").use { cursor ->
                    var found = false
                    while (cursor.moveToNext()) {
                        val table = cursor.getString(2)
                        val from = cursor.getString(3)
                        val onDelete = cursor.getString(6)
                        if (table == "liturgical_day_cache" && from == "day_id" && onDelete == "CASCADE") {
                            found = true
                        }
                    }
                    found
                }
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun migration_is_idempotent_under_reapplication() {
        helper.createDatabase(TEST_DB, 1).apply {
            // Re-applying the migration before validation proves the DDL is
            // safe for an interrupted/retried upgrade.
            AppDatabase.MIGRATION_1_2.migrate(this)
            AppDatabase.MIGRATION_1_2.migrate(this)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )
        try {
            assertEquals(
                2,
                tableNames(database).count { it.startsWith("liturgical_") }
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun migration_2_to_3_rebuilds_bookmarks_with_uuid_and_anchor() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO bookmarks " +
                    "(bookId,chapterId,chapterPosition,verseId,chapterNumber,verseStart,verseEnd,createdAt) " +
                    "VALUES ('jakob',1,3,42,1,5,6,1000)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )
        try {
            database.query(
                "SELECT id,bookId,chapterPosition,verseStart,verseEnd,startOffset,endOffset," +
                    "chapterNumber,createdAt,updatedAt,deletedAt FROM bookmarks"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.count)
                assertTrue(
                    "a canonical UUID must be generated",
                    cursor.getString(0).matches(
                        Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                    )
                )
                assertEquals("jakob", cursor.getString(1))
                assertEquals(3, cursor.getInt(2))
                assertEquals(5, cursor.getInt(3))
                assertEquals(6, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
                assertEquals(0, cursor.getInt(6))
                assertEquals(1, cursor.getInt(7))
                assertEquals(1000L, cursor.getLong(8))
                assertEquals(1000L, cursor.getLong(9))
                assertTrue("deletedAt starts null", cursor.isNull(10))
            }

            val columns = database.query("PRAGMA table_info(`bookmarks`)").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            assertTrue("corpus row ids are no longer stored", "chapterId" !in columns && "verseId" !in columns)
            assertTrue("anchor columns are present", "startOffset" in columns && "endOffset" in columns)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration_2_to_3_deduplicates_rows_that_share_an_anchor() {
        helper.createDatabase(TEST_DB, 2).apply {
            // v2 uniqueness was (bookId, chapterId, verseId); two corpus rows
            // can therefore describe the same stable anchor.
            execSQL(
                "INSERT INTO bookmarks " +
                    "(bookId,chapterId,chapterPosition,verseId,chapterNumber,verseStart,verseEnd,createdAt) " +
                    "VALUES ('jakob',1,3,42,1,5,6,1000)"
            )
            execSQL(
                "INSERT INTO bookmarks " +
                    "(bookId,chapterId,chapterPosition,verseId,chapterNumber,verseStart,verseEnd,createdAt) " +
                    "VALUES ('jakob',2,3,43,1,5,6,2000)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )
        try {
            assertEquals(
                "duplicate anchors collapse to one bookmark instead of crashing the upgrade",
                1,
                scalar(database, "SELECT count(*) FROM bookmarks")
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun production_builder_applies_all_migrations_to_v3() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(AppDatabase.DATABASE_NAME)

        helper.createDatabase(AppDatabase.DATABASE_NAME, 1).apply {
            execSQL(
                "INSERT INTO bookmarks " +
                    "(bookId,chapterId,chapterPosition,verseId,chapterNumber,verseStart,verseEnd,createdAt) " +
                    "VALUES ('jakob',1,1,4,1,5,5,1)"
            )
            execSQL(
                "INSERT INTO reading_state (id,bookId,chapterPosition,updatedAt) " +
                    "VALUES (1,'jakob',3,1)"
            )
            close()
        }

        val roomDatabase = AppDatabase.create(context)
        try {
            val database = roomDatabase.openHelper.writableDatabase
            assertEquals("production builder must reach schema v3", 3, database.version)
            assertEquals(1, scalar(database, "SELECT count(*) FROM bookmarks"))
            assertEquals(1, scalar(database, "SELECT count(*) FROM reading_state"))
            assertEquals(0, scalar(database, "SELECT count(*) FROM liturgical_day_cache"))
        } finally {
            roomDatabase.close()
            context.deleteDatabase(AppDatabase.DATABASE_NAME)
        }
    }

    private fun tableNames(database: SupportSQLiteDatabase): Set<String> =
        database.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun scalar(database: SupportSQLiteDatabase, sql: String): Int =
        database.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
