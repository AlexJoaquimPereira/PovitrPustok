package com.biblia.povitrpustok.data.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TEST-04: the 1→2 migration must create the liturgical cache schema
 * (tables, unique date index, cascade FK), be idempotent, and leave the
 * v1 bookmark/reading-state tables untouched (no destructive fallback).
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory database
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    }
                )
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migration_creates_liturgical_cache_tables_indexes_and_fk() {
        val database = helper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(database)

        val tables = tableNames(database)
        assertTrue(tables.contains("liturgical_day_cache"))
        assertTrue(tables.contains("liturgical_reading"))

        val uniqueDateIndex = database.query(
            "PRAGMA index_list(`liturgical_day_cache`)"
        ).use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val unique = cursor.getInt(2) == 1
                if (name == "index_liturgical_day_cache_date" && unique) found = true
            }
            found
        }
        assertTrue("the date index must be unique", uniqueDateIndex)

        val cascades = database.query(
            "PRAGMA foreign_key_list(`liturgical_reading`)"
        ).use { cursor ->
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
        assertTrue("readings must cascade-delete with their day", cascades)
    }

    @Test
    fun migration_is_idempotent() {
        val database = helper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(database)
        AppDatabase.MIGRATION_1_2.migrate(database)
        assertEquals(2, tableNames(database).count { it.startsWith("liturgical_") })
    }

    @Test
    fun migration_preserves_v1_bookmark_and_reading_state_rows() {
        val database = helper.writableDatabase

        // Minimal v1 tables; the migration must not drop or rewrite them.
        database.execSQL(
            "CREATE TABLE `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`book_id` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE `reading_state` (`book_id` TEXT NOT NULL, " +
                "`chapter_position` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`book_id`))"
        )
        database.execSQL("INSERT INTO bookmarks (book_id) VALUES ('jakob')")
        database.execSQL(
            "INSERT INTO reading_state (book_id, chapter_position, updated_at) VALUES ('jakob', 3, 0)"
        )

        AppDatabase.MIGRATION_1_2.migrate(database)

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
    }

    @Test
    fun migrated_schema_matches_the_room_created_schema() {
        val migrated = helper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(migrated)

        val roomDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val expected = roomDatabase.openHelper.writableDatabase
            for (table in listOf("liturgical_day_cache", "liturgical_reading")) {
                assertEquals(
                    "$table column definition must match Room's schema",
                    columnSignature(expected, table),
                    columnSignature(migrated, table)
                )
            }
        } finally {
            roomDatabase.close()
        }
    }

    @Test
    fun room_opens_and_migrates_a_v1_database_without_data_loss() {
        val databaseName = "room-migration-v1-test.db"
        context.deleteDatabase(databaseName)

        // Room's own DDL for the v1 entities (unchanged in v2).
        val definition = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val ddl = definition.openHelper.writableDatabase.query(
            "SELECT sql FROM sqlite_master " +
                "WHERE tbl_name IN ('bookmarks','reading_state') " +
                "AND type IN ('table','index') AND sql IS NOT NULL " +
                "ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        definition.close()

        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { v1 ->
            ddl.forEach { v1.execSQL(it) }
            v1.execSQL(
                "INSERT INTO bookmarks " +
                    "(bookId,chapterId,chapterPosition,verseId,chapterNumber,verseStart,verseEnd,createdAt) " +
                    "VALUES ('jakob',1,1,4,1,5,5,1)"
            )
            v1.execSQL(
                "INSERT INTO reading_state (id,bookId,chapterPosition,updatedAt) " +
                    "VALUES (1,'jakob',3,1)"
            )
            v1.version = 1
        }

        // Production builder path: Room runs MIGRATION_1_2 and validates the schema.
        val roomDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            val support = roomDatabase.openHelper.writableDatabase
            assertEquals("bookmarks must survive the real upgrade path", 1, scalar(support, "SELECT count(*) FROM bookmarks"))
            assertEquals("reading state must survive the real upgrade path", 1, scalar(support, "SELECT count(*) FROM reading_state"))
            assertEquals("liturgical cache table must exist after upgrade", 0, scalar(support, "SELECT count(*) FROM liturgical_day_cache"))
        } finally {
            roomDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun columnSignature(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        "${cursor.getString(1)}:${cursor.getString(2)}:" +
                            "${cursor.getInt(3)}:${cursor.getString(4)}:${cursor.getInt(5)}"
                    )
                }
            }.sorted()
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
}
