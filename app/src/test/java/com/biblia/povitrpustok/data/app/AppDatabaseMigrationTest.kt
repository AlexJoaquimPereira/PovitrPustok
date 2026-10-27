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
 * The v1 schema JSON is generated from the two v1 entities and checked in
 * beside the current v2 schema, so MigrationTestHelper can recreate a real
 * v1 database, run [AppDatabase.MIGRATION_1_2], and validate the result
 * against the exported v2 schema.
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
    fun production_builder_applies_migration_1_to_2() {
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
            assertEquals("production builder must reach schema v2", 2, database.version)
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
